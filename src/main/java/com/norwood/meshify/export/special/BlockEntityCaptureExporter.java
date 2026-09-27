package com.norwood.meshify.export.special;

import com.norwood.meshify.export.MeshBuilder;
import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/** Runs a block entity's own renderer into a {@link CaptureQueue}, for block entities no special exporter handles. */
public final class BlockEntityCaptureExporter {
   private BlockEntityCaptureExporter() {
   }

   public static boolean export(MeshBuilder mesh, MinecraftClient client, BlockPos pos, BlockEntity blockEntity, double blockX, double blockY, double blockZ, List<String> warnings) {
      BlockEntityRenderManager renderManager = client.getBlockEntityRenderDispatcher();

      try {
         if (renderManager.get(blockEntity) == null) {
            return false;
         }

         BlockEntityRenderState state = renderManager.getRenderState(blockEntity, 0.0F, null);
         if (state == null) {
            return false;
         }

         MatrixStack matrices = new MatrixStack();
         matrices.translate(blockX, blockY, blockZ);
         CaptureQueue queue = new CaptureQueue(mesh, client, warnings);
         renderManager.render(state, matrices, queue, dummyCamera(pos));
         return queue.capturedQuads > 0;
      } catch (Exception exception) {
         warnings.add("BER capture failed @ " + pos.toShortString() + " (" + blockEntity.getType() + "): " + exception);
         return false;
      }
   }

   /** A camera a little in front of and above {@code pos}, for renderers that want one. */
   static CameraRenderState dummyCamera(BlockPos pos) {
      CameraRenderState camera = new CameraRenderState();
      camera.initialized = true;
      camera.pos = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 4.0);
      camera.blockPos = BlockPos.ofFloored(camera.pos);
      camera.entityPos = camera.pos;
      camera.orientation = new Quaternionf();
      return camera;
   }
}
