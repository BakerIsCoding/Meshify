package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BlockContext;
import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.TextureKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

public final class BedExporter {
   // Per face, four corners of {x, y, z, u, v} in model and texture pixels.
   private static final double[][][] HEAD_QUADS = new double[][][]{
      {{0.0, 9.0, 16.0, 22.0, 22.0}, {16.0, 9.0, 16.0, 6.0, 22.0}, {16.0, 9.0, 0.0, 6.0, 6.0}, {0.0, 9.0, 0.0, 22.0, 6.0}},
      {{0.0, 3.0, 0.0, 44.0, 6.0}, {16.0, 3.0, 0.0, 28.0, 6.0}, {16.0, 3.0, 16.0, 28.0, 22.0}, {0.0, 3.0, 16.0, 44.0, 22.0}},
      {{16.0, 3.0, 0.0, 6.0, 0.0}, {0.0, 3.0, 0.0, 22.0, 0.0}, {0.0, 9.0, 0.0, 22.0, 6.0}, {16.0, 9.0, 0.0, 6.0, 6.0}},
      {{0.0, 3.0, 0.0, 0.0, 6.0}, {0.0, 3.0, 16.0, 0.0, 22.0}, {0.0, 9.0, 16.0, 6.0, 22.0}, {0.0, 9.0, 0.0, 6.0, 6.0}},
      {{16.0, 3.0, 16.0, 28.0, 22.0}, {16.0, 3.0, 0.0, 28.0, 6.0}, {16.0, 9.0, 0.0, 22.0, 6.0}, {16.0, 9.0, 16.0, 22.0, 22.0}}
   };
   private static final float[][] HEAD_NORMALS = new float[][]{
      {0.0F, 1.0F, 0.0F}, {0.0F, -1.0F, 0.0F}, {0.0F, 0.0F, -1.0F}, {-1.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F}
   };
   private static final double[][][] FOOT_QUADS = new double[][][]{
      {{0.0, 9.0, 16.0, 22.0, 44.0}, {16.0, 9.0, 16.0, 6.0, 44.0}, {16.0, 9.0, 0.0, 6.0, 28.0}, {0.0, 9.0, 0.0, 22.0, 28.0}},
      {{0.0, 3.0, 0.0, 44.0, 28.0}, {16.0, 3.0, 0.0, 28.0, 28.0}, {16.0, 3.0, 16.0, 28.0, 44.0}, {0.0, 3.0, 16.0, 44.0, 44.0}},
      {{16.0, 9.0, 16.0, 38.0, 28.0}, {0.0, 9.0, 16.0, 22.0, 28.0}, {0.0, 3.0, 16.0, 22.0, 22.0}, {16.0, 3.0, 16.0, 38.0, 22.0}},
      {{0.0, 3.0, 0.0, 0.0, 28.0}, {0.0, 3.0, 16.0, 0.0, 44.0}, {0.0, 9.0, 16.0, 6.0, 44.0}, {0.0, 9.0, 0.0, 6.0, 28.0}},
      {{16.0, 3.0, 16.0, 28.0, 44.0}, {16.0, 3.0, 0.0, 28.0, 28.0}, {16.0, 9.0, 0.0, 22.0, 28.0}, {16.0, 9.0, 16.0, 22.0, 44.0}}
   };
   private static final float[][] FOOT_NORMALS = new float[][]{
      {0.0F, 1.0F, 0.0F}, {0.0F, -1.0F, 0.0F}, {0.0F, 0.0F, 1.0F}, {-1.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F}
   };

   private BedExporter() {
   }

   public static void export(BlockContext context, BedBlock bed) {
      BlockState state = context.state();
      double yaw = Yaw.of(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
      boolean isHead = state.getValue(BedBlock.PART) == BedPart.HEAD;
      TextureKey texture = TextureKey.resource(Identifier.withDefaultNamespace("textures/entity/bed/" + bed.getColor().getName() + ".png"));
      BoxUv bedHalf = new BoxUv(context.placement(yaw), texture, 64, 64);
      double[][][] quads = isHead ? HEAD_QUADS : FOOT_QUADS;
      float[][] normals = isHead ? HEAD_NORMALS : FOOT_NORMALS;

      for (int face = 0; face < quads.length; face++) {
         double[][] corners = new double[4][];
         double[][] uvs = new double[4][];

         for (int k = 0; k < 4; k++) {
            double[] corner = quads[face][k];
            corners[k] = new double[]{corner[0], corner[1], corner[2]};
            uvs[k] = new double[]{corner[3], corner[4]};
         }

         bedHalf.quad(corners, uvs, normals[face]);
      }

      // The legs.
      if (isHead) {
         bedHalf.box(new AABB(0.0, 0.0, 0.0, 3.0, 3.0, 3.0), 50, 0);
         bedHalf.box(new AABB(13.0, 0.0, 0.0, 16.0, 3.0, 3.0), 50, 6);
      } else {
         bedHalf.box(new AABB(0.0, 0.0, 13.0, 3.0, 3.0, 16.0), 50, 12);
         bedHalf.box(new AABB(13.0, 0.0, 13.0, 16.0, 3.0, 16.0), 50, 18);
      }
   }
}
