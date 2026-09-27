package com.norwood.meshify;

import com.norwood.meshify.MeshifyActions.Feedback;
import com.norwood.meshify.Selection.Corner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

/** The corner keys and the in-world outline of the selection. */
public final class SelectionRenderer {
   private static final int START_COLOR = 0xFF55FF55;
   private static final int END_COLOR = 0xFFFF5555;
   private static final int REGION_COLOR = 0xFF55FFFF;
   private static final float REGION_LINE_WIDTH = 3.0F;
   private static final float CORNER_LINE_WIDTH = 5.0F;
   /** Grows the corner outlines just past the region's, so the two never z-fight. */
   private static final double CORNER_NUDGE = 0.002;

   private static KeyBinding setStartKey;
   private static KeyBinding setEndKey;

   private SelectionRenderer() {
   }

   public static void register() {
      setStartKey = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.meshify.set_start", InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_V, KeyBinding.Category.MISC)
      );
      setEndKey = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.meshify.set_end", InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_B, KeyBinding.Category.MISC)
      );

      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         while (setStartKey.wasPressed()) {
            setCorner(client, Corner.START);
         }

         while (setEndKey.wasPressed()) {
            setCorner(client, Corner.END);
         }
      });

      // Not BEFORE_DEBUG_RENDER: Fabric never prepares that hook's context, so its matrices are null.
      WorldRenderEvents.AFTER_ENTITIES.register(context -> {
         MinecraftClient client = MinecraftClient.getInstance();
         if (context.matrices() != null
            && context.consumers() != null
            && client.world != null
            && Selection.matchesDimension(client.world.getRegistryKey())) {
            Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
            VertexConsumer lineBuffer = context.consumers().getBuffer(RenderLayers.lines());
            for (Outline outline : outlines()) {
               VertexRendering.drawOutline(
                  context.matrices(), lineBuffer, VoxelShapes.cuboid(outline.box()), -camera.x, -camera.y, -camera.z, outline.color(), outline.width()
               );
            }
         }
      });
   }

   private static List<Outline> outlines() {
      List<Outline> outlines = new ArrayList<>();
      if (Selection.isComplete()) {
         outlines.add(new Outline(Box.enclosing(Selection.min(), Selection.max()), REGION_COLOR, REGION_LINE_WIDTH));
      }

      if (Selection.start != null) {
         outlines.add(new Outline(new Box(Selection.start).expand(CORNER_NUDGE), START_COLOR, CORNER_LINE_WIDTH));
      }

      if (Selection.end != null) {
         outlines.add(new Outline(new Box(Selection.end).expand(CORNER_NUDGE), END_COLOR, CORNER_LINE_WIDTH));
      }

      return outlines;
   }

   /** The keys report on the action bar, which stays out of the chat while the player sets corners by eye. */
   private static void setCorner(MinecraftClient client, Corner corner) {
      if (client.player != null && client.world != null) {
         Consumer<Text> actionBar = text -> client.player.sendMessage(text, true);
         MeshifyActions.setCorner(new Feedback(actionBar, actionBar), corner, MeshifyActions.targetedBlock(client));
      }
   }

   private record Outline(Box box, int color, float width) {
   }
}
