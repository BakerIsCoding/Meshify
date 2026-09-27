package com.norwood.meshify;

import com.norwood.meshify.Selection.Corner;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

/** The corner keys and the in-world outline of the selection. */
public final class SelectionRenderer {
   private static final int START_COLOR = 0xFF55FF55;
   private static final int END_COLOR = 0xFFFF5555;
   private static final int REGION_COLOR = 0xFF55FFFF;

   private static KeyBinding setStart;
   private static KeyBinding setEnd;

   private SelectionRenderer() {
   }

   public static void register() {
      KeyBinding.Category keybinding$category = KeyBinding.Category.MISC;
      setStart = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.meshify.set_start", InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_V, keybinding$category)
      );
      setEnd = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.meshify.set_end", InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_B, keybinding$category)
      );

      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         while (setStart.wasPressed()) {
            setCorner(client, Corner.START);
         }

         while (setEnd.wasPressed()) {
            setCorner(client, Corner.END);
         }
      });

      // Not BEFORE_DEBUG_RENDER: Fabric never prepares that hook's context, so its matrices are null.
      WorldRenderEvents.AFTER_ENTITIES.register(context -> {
         MinecraftClient client = MinecraftClient.getInstance();
         if (context.matrices() != null
            && context.consumers() != null
            && client.world != null
            && Selection.matches(client.world.getRegistryKey())) {
            Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
            VertexConsumer lines = context.consumers().getBuffer(RenderLayers.lines());
            for (Outline outline : outlines()) {
               VertexRendering.drawOutline(
                  context.matrices(), lines, VoxelShapes.cuboid(outline.box()), -camera.x, -camera.y, -camera.z, outline.color(), outline.width()
               );
            }
         }
      });
   }

   private static List<Outline> outlines() {
      List<Outline> outlines = new ArrayList<>();
      if (Selection.isComplete()) {
         BlockPos min = Selection.min();
         BlockPos max = Selection.max();
         outlines.add(new Outline(new Box(min.getX(), min.getY(), min.getZ(), max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0), REGION_COLOR, 3.0F));
      }

      if (Selection.start != null) {
         outlines.add(new Outline(blockBox(Selection.start), START_COLOR, 5.0F));
      }

      if (Selection.end != null) {
         outlines.add(new Outline(blockBox(Selection.end), END_COLOR, 5.0F));
      }

      return outlines;
   }

   private static Box blockBox(BlockPos pos) {
      // Nudged outward so the corner outline does not z-fight with the region outline.
      return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0).expand(0.002);
   }

   private static void setCorner(MinecraftClient client, Corner corner) {
      if (client.player != null && client.world != null) {
         BlockPos pos = MeshifyActions.targetedBlock(client);
         Selection.set(corner, pos, client.world.getRegistryKey());
         client.player.sendMessage(MeshifyActions.cornerSetMessage(corner, pos), true);
      }
   }

   private record Outline(Box box, int color, float width) {
   }
}
