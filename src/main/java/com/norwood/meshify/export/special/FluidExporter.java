package com.norwood.meshify.export.special;

import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TextureKey;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class FluidExporter {
   private FluidExporter() {
   }

   /** A box up to the fluid's surface, without the faces the same fluid or a full block hides, except on the region's edge. */
   public static void export(MeshBuilder mesh, ClientWorld world, BlockPos pos, FluidState fluid, BlockBox bounds, double blockX, double blockY, double blockZ) {
      FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid.getFluid());
      Sprite[] sprites = handler == null ? null : handler.getFluidSprites(world, pos, fluid);
      if (sprites == null || sprites.length == 0 || sprites[0] == null) {
         return;
      }

      TextureKey texture = TextureKey.sprite(sprites[0], handler.getFluidColor(world, pos, fluid));
      // Under more of the same fluid the column is full height, with no surface of its own.
      boolean submerged = sameFluid(world, pos.up(), fluid);
      float height = submerged ? 1.0F : fluid.getHeight(world, pos);
      float sideTop = 1.0F - height;
      float[][] sideUv = new float[][]{{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, sideTop}, {0.0F, sideTop}};
      if (!submerged) {
         addQuad(
            mesh,
            texture,
            blockX,
            blockY,
            blockZ,
            new float[][]{{0.0F, height, 1.0F}, {1.0F, height, 1.0F}, {1.0F, height, 0.0F}, {0.0F, height, 0.0F}},
            new float[][]{{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}, {0.0F, 0.0F}},
            new float[]{0.0F, 1.0F, 0.0F}
         );
      }

      if (shouldDraw(world, pos, Direction.DOWN, fluid, bounds)) {
         addQuad(
            mesh,
            texture,
            blockX,
            blockY,
            blockZ,
            new float[][]{{1.0F, 0.0F, 1.0F}, {0.0F, 0.0F, 1.0F}, {0.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F}},
            new float[][]{{1.0F, 1.0F}, {0.0F, 1.0F}, {0.0F, 0.0F}, {1.0F, 0.0F}},
            new float[]{0.0F, -1.0F, 0.0F}
         );
      }

      if (shouldDraw(world, pos, Direction.NORTH, fluid, bounds)) {
         addQuad(
            mesh,
            texture,
            blockX,
            blockY,
            blockZ,
            new float[][]{{1.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, height, 0.0F}, {1.0F, height, 0.0F}},
            sideUv,
            new float[]{0.0F, 0.0F, -1.0F}
         );
      }

      if (shouldDraw(world, pos, Direction.SOUTH, fluid, bounds)) {
         addQuad(
            mesh,
            texture,
            blockX,
            blockY,
            blockZ,
            new float[][]{{0.0F, 0.0F, 1.0F}, {1.0F, 0.0F, 1.0F}, {1.0F, height, 1.0F}, {0.0F, height, 1.0F}},
            sideUv,
            new float[]{0.0F, 0.0F, 1.0F}
         );
      }

      if (shouldDraw(world, pos, Direction.WEST, fluid, bounds)) {
         addQuad(
            mesh,
            texture,
            blockX,
            blockY,
            blockZ,
            new float[][]{{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 1.0F}, {0.0F, height, 1.0F}, {0.0F, height, 0.0F}},
            sideUv,
            new float[]{-1.0F, 0.0F, 0.0F}
         );
      }

      if (shouldDraw(world, pos, Direction.EAST, fluid, bounds)) {
         addQuad(
            mesh,
            texture,
            blockX,
            blockY,
            blockZ,
            new float[][]{{1.0F, 0.0F, 1.0F}, {1.0F, 0.0F, 0.0F}, {1.0F, height, 0.0F}, {1.0F, height, 1.0F}},
            sideUv,
            new float[]{1.0F, 0.0F, 0.0F}
         );
      }
   }

   private static boolean sameFluid(ClientWorld world, BlockPos pos, FluidState fluid) {
      return world.getFluidState(pos).getFluid().matchesType(fluid.getFluid());
   }

   private static boolean shouldDraw(ClientWorld world, BlockPos pos, Direction side, FluidState fluid, BlockBox bounds) {
      BlockPos neighbor = pos.offset(side);
      if (!bounds.contains(neighbor)) {
         return true;
      }

      return !sameFluid(world, neighbor, fluid) && !world.getBlockState(neighbor).isOpaqueFullCube();
   }

   private static void addQuad(MeshBuilder mesh, TextureKey texture, double blockX, double blockY, double blockZ, float[][] corners, float[][] uv, float[] normal) {
      float[][] positions = new float[4][3];

      for (int k = 0; k < 4; k++) {
         positions[k][0] = (float)(blockX + corners[k][0]);
         positions[k][1] = (float)(blockY + corners[k][1]);
         positions[k][2] = (float)(blockZ + corners[k][2]);
      }

      mesh.addQuad(texture, positions, uv, normal);
   }
}
