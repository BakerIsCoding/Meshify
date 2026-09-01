package com.norwood.meshify.export.special;

import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TexKey;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class FluidExporter {
   private FluidExporter() {
   }

   public static void export(MeshBuilder mesh, ClientWorld world, BlockPos pos, FluidState fluid, BlockPos min, BlockPos max, double bx, double by, double bz) {
      FluidRenderHandler fluidrenderhandler = FluidRenderHandlerRegistry.INSTANCE.get(fluid.getFluid());
      if (fluidrenderhandler != null) {
         Sprite[] asprite = fluidrenderhandler.getFluidSprites(world, pos, fluid);
         if (asprite != null && asprite.length != 0 && asprite[0] != null) {
            int i = fluidrenderhandler.getFluidColor(world, pos, fluid) & 16777215;
            int j = i == 16777215 ? -1 : i;
            TexKey texkey = TexKey.sprite(asprite[0], j);
            boolean flag = sameFluid(world, pos.up(), fluid);
            float f = flag ? 1.0F : fluid.getHeight(world, pos);
            if (!flag) {
               addQuad(
                  mesh,
                  texkey,
                  bx,
                  by,
                  bz,
                  new float[][]{{0.0F, f, 1.0F}, {1.0F, f, 1.0F}, {1.0F, f, 0.0F}, {0.0F, f, 0.0F}},
                  new float[][]{{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}, {0.0F, 0.0F}},
                  new float[]{0.0F, 1.0F, 0.0F}
               );
            }

            if (shouldDraw(world, pos, Direction.DOWN, fluid, min, max)) {
               addQuad(
                  mesh,
                  texkey,
                  bx,
                  by,
                  bz,
                  new float[][]{{1.0F, 0.0F, 1.0F}, {0.0F, 0.0F, 1.0F}, {0.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F}},
                  new float[][]{{1.0F, 1.0F}, {0.0F, 1.0F}, {0.0F, 0.0F}, {1.0F, 0.0F}},
                  new float[]{0.0F, -1.0F, 0.0F}
               );
            }

            float f1 = 1.0F - f;
            if (shouldDraw(world, pos, Direction.NORTH, fluid, min, max)) {
               addQuad(
                  mesh,
                  texkey,
                  bx,
                  by,
                  bz,
                  new float[][]{{1.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, f, 0.0F}, {1.0F, f, 0.0F}},
                  new float[][]{{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, f1}, {0.0F, f1}},
                  new float[]{0.0F, 0.0F, -1.0F}
               );
            }

            if (shouldDraw(world, pos, Direction.SOUTH, fluid, min, max)) {
               addQuad(
                  mesh,
                  texkey,
                  bx,
                  by,
                  bz,
                  new float[][]{{0.0F, 0.0F, 1.0F}, {1.0F, 0.0F, 1.0F}, {1.0F, f, 1.0F}, {0.0F, f, 1.0F}},
                  new float[][]{{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, f1}, {0.0F, f1}},
                  new float[]{0.0F, 0.0F, 1.0F}
               );
            }

            if (shouldDraw(world, pos, Direction.WEST, fluid, min, max)) {
               addQuad(
                  mesh,
                  texkey,
                  bx,
                  by,
                  bz,
                  new float[][]{{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 1.0F}, {0.0F, f, 1.0F}, {0.0F, f, 0.0F}},
                  new float[][]{{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, f1}, {0.0F, f1}},
                  new float[]{-1.0F, 0.0F, 0.0F}
               );
            }

            if (shouldDraw(world, pos, Direction.EAST, fluid, min, max)) {
               addQuad(
                  mesh,
                  texkey,
                  bx,
                  by,
                  bz,
                  new float[][]{{1.0F, 0.0F, 1.0F}, {1.0F, 0.0F, 0.0F}, {1.0F, f, 0.0F}, {1.0F, f, 1.0F}},
                  new float[][]{{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, f1}, {0.0F, f1}},
                  new float[]{1.0F, 0.0F, 0.0F}
               );
            }
         }
      }
   }

   private static boolean sameFluid(ClientWorld world, BlockPos pos, FluidState self) {
      return world.getFluidState(pos).getFluid().matchesType(self.getFluid());
   }

   private static boolean shouldDraw(ClientWorld world, BlockPos pos, Direction dir, FluidState self, BlockPos min, BlockPos max) {
      BlockPos blockpos = pos.offset(dir);
      if (blockpos.getX() < min.getX()
         || blockpos.getX() > max.getX()
         || blockpos.getY() < min.getY()
         || blockpos.getY() > max.getY()
         || blockpos.getZ() < min.getZ()
         || blockpos.getZ() > max.getZ()) {
         return true;
      } else if (sameFluid(world, blockpos, self)) {
         return false;
      } else {
         BlockState blockstate = world.getBlockState(blockpos);
         return !blockstate.isOpaqueFullCube();
      }
   }

   private static void addQuad(MeshBuilder mesh, TexKey tex, double bx, double by, double bz, float[][] local, float[][] uv, float[] normal) {
      float[][] afloat = new float[4][3];

      for (int i = 0; i < 4; i++) {
         afloat[i][0] = (float)(bx + local[i][0]);
         afloat[i][1] = (float)(by + local[i][1]);
         afloat[i][2] = (float)(bz + local[i][2]);
      }

      mesh.addQuad(tex, afloat, uv, normal);
   }
}
