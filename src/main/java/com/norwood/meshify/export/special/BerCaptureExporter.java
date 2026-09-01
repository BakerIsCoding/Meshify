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

public final class BerCaptureExporter {
   private BerCaptureExporter() {
   }

   public static boolean export(MeshBuilder mesh, MinecraftClient mc, BlockPos pos, BlockEntity be, double bx, double by, double bz, List<String> warnings) {
      BlockEntityRenderManager blockentityrendermanager = mc.getBlockEntityRenderDispatcher();

      try {
         if (blockentityrendermanager.get(be) == null) {
            return false;
         } else {
            BlockEntityRenderState blockentityrenderstate = blockentityrendermanager.getRenderState(be, 0.0F, null);
            if (blockentityrenderstate == null) {
               return false;
            } else {
               MatrixStack matrixstack = new MatrixStack();
               matrixstack.translate(bx, by, bz);
               CaptureQueue capturequeue = new CaptureQueue(mesh, mc, warnings);
               blockentityrendermanager.render(blockentityrenderstate, matrixstack, capturequeue, dummyCamera(pos));
               return capturequeue.captured > 0;
            }
         }
      } catch (Exception exception) {
         warnings.add("BER capture failed @ " + pos.toShortString() + " (" + be.getType() + "): " + exception);
         return false;
      }
   }

   static CameraRenderState dummyCamera(BlockPos pos) {
      CameraRenderState camerarenderstate = new CameraRenderState();
      camerarenderstate.initialized = true;
      camerarenderstate.pos = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 4.0);
      camerarenderstate.blockPos = BlockPos.ofFloored(camerarenderstate.pos);
      camerarenderstate.entityPos = camerarenderstate.pos;
      camerarenderstate.orientation = new Quaternionf();
      return camerarenderstate;
   }
}
