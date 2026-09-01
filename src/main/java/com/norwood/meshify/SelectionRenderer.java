package com.norwood.meshify;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

/**
 * The point-and-click half of the mod: two keys set the corners from whatever block the crosshair
 * is on, and the pending region is drawn in the world so you can see what will be exported before
 * running the command.
 */
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
            setCorner(client, true);
         }

         while (setEnd.wasPressed()) {
            setCorner(client, false);
         }
      });

      // AFTER_ENTITIES, not BEFORE_DEBUG_RENDER: the debug hook sits in the outer render method,
      // where Fabric never prepares the context, so its matrices and consumers are always null.
      WorldRenderEvents.AFTER_ENTITIES.register(context -> {
         MinecraftClient minecraftclient = MinecraftClient.getInstance();
         if (context.matrices() != null
            && context.consumers() != null
            && minecraftclient.world != null
            && Selection.matches(minecraftclient.world.getRegistryKey())) {
            Vec3d vec3d = minecraftclient.gameRenderer.getCamera().getCameraPos();
            var vertexconsumer = context.consumers().getBuffer(RenderLayers.lines());
            if (Selection.isComplete()) {
               BlockPos blockpos = Selection.min();
               BlockPos blockpos1 = Selection.max();
               Box box = new Box(
                  blockpos.getX(), blockpos.getY(), blockpos.getZ(), blockpos1.getX() + 1.0, blockpos1.getY() + 1.0, blockpos1.getZ() + 1.0
               );
               draw(context, vertexconsumer, box, vec3d, REGION_COLOR, 3.0F);
            }

            // The individual corners stay visible on top of the region so it is obvious which one
            // is which when you are about to move one of them.
            if (Selection.start != null) {
               draw(context, vertexconsumer, blockBox(Selection.start), vec3d, START_COLOR, 5.0F);
            }

            if (Selection.end != null) {
               draw(context, vertexconsumer, blockBox(Selection.end), vec3d, END_COLOR, 5.0F);
            }
         }
      });
   }

   private static Box blockBox(BlockPos pos) {
      // Nudged outward so the corner outline does not z-fight with the region outline.
      return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0).expand(0.002);
   }

   private static void draw(
      net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context,
      net.minecraft.client.render.VertexConsumer consumer,
      Box box,
      Vec3d camera,
      int color,
      float width
   ) {
      VertexRendering.drawOutline(
         context.matrices(), consumer, VoxelShapes.cuboid(box), -camera.x, -camera.y, -camera.z, color, width
      );
   }

   private static void setCorner(MinecraftClient client, boolean isStart) {
      if (client.player != null && client.world != null) {
         // Whatever the crosshair is on, falling back to the block underfoot when aiming at nothing.
         BlockPos blockpos = client.crosshairTarget instanceof BlockHitResult blockhitresult
               && client.crosshairTarget.getType() == HitResult.Type.BLOCK
            ? blockhitresult.getBlockPos()
            : client.player.getBlockPos();
         Selection.set(isStart, blockpos, client.world.getRegistryKey());
         Text text = Text.translatable(isStart ? "meshify.label.start" : "meshify.label.end");
         client.player
            .sendMessage(
               Selection.isComplete()
                  ? Text.translatable("meshify.msg.pos_set_volume", text, blockpos.toShortString(), Selection.volume())
                  : Text.translatable("meshify.msg.pos_set", text, blockpos.toShortString()),
               true
            );
      }
   }
}
