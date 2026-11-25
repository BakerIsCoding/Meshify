package com.norwood.meshify.export.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.norwood.meshify.export.BlockContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

// Runs a block entity's own renderer into a CaptureQueue, for block entities no special exporter handles.
public final class BlockEntityCaptureExporter {
   private BlockEntityCaptureExporter() {
   }

   public static boolean export(BlockContext context, BlockEntity blockEntity) {
      BlockEntityRenderDispatcher dispatcher = context.client().getBlockEntityRenderDispatcher();

      try {
         var renderer = dispatcher.getRenderer(blockEntity);
         if (renderer == null) {
            return false;
         }

         // From 26.2 extraction only succeeds when the flag matches how the renderer wants to be drawn.
         //? if >=26.2 {
         /*BlockEntityRenderState state = dispatcher.tryExtractRenderState(blockEntity, 0.0F, null, renderer.shouldRenderOffScreen());
         *///?} else
         BlockEntityRenderState state = dispatcher.tryExtractRenderState(blockEntity, 0.0F, null);
         if (state == null) {
            return false;
         }

         PoseStack matrices = new PoseStack();
         Vec3 origin = context.origin();
         matrices.translate(origin.x, origin.y, origin.z);
         CaptureQueue queue = new CaptureQueue(context.mesh(), context.client(), context.warnings());
         dispatcher.submit(state, matrices, queue, dummyCamera(context.pos()));
         return queue.capturedQuads > 0;
      } catch (Exception exception) {
         context.warnings().add("BER capture failed @ " + context.pos().toShortString() + " (" + blockEntity.getType() + "): " + exception);
         return false;
      }
   }

   // A camera a little in front of and above pos, for renderers that want one.
   static CameraRenderState dummyCamera(BlockPos pos) {
      CameraRenderState camera = new CameraRenderState();
      camera.initialized = true;
      camera.pos = new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 4.0);
      camera.blockPos = BlockPos.containing(camera.pos);
      //? if <26.1
      camera.entityPos = camera.pos;
      camera.orientation = new Quaternionf();
      return camera;
   }
}
