package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TexKey;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

public final class BedExporter {
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

   public static void export(MeshBuilder mesh, BlockState state, BedBlock bed, double bx, double by, double bz) {
      Direction direction = state.get(Properties.HORIZONTAL_FACING);

      double d0 = switch (direction) {
         case EAST -> 90.0;
         case SOUTH -> 180.0;
         case WEST -> 270.0;
         default -> 0.0;
      };
      boolean flag = state.get(BedBlock.PART) == BedPart.HEAD;
      TexKey texkey = TexKey.resource(Identifier.of("minecraft", "textures/entity/bed/" + bed.getColor().getId() + ".png"));
      double[][][] adouble = flag ? HEAD_QUADS : FOOT_QUADS;
      float[][] afloat = flag ? HEAD_NORMALS : FOOT_NORMALS;

      for (int i = 0; i < adouble.length; i++) {
         double[][] adouble1 = adouble[i];
         double[][] adouble2 = new double[4][3];
         double[][] adouble3 = new double[4][2];

         for (int j = 0; j < 4; j++) {
            adouble2[j][0] = adouble1[j][0];
            adouble2[j][1] = adouble1[j][1];
            adouble2[j][2] = adouble1[j][2];
            adouble3[j][0] = adouble1[j][3];
            adouble3[j][1] = adouble1[j][4];
         }

         BoxUv.rotatedQuad(mesh, texkey, bx, by, bz, adouble2, adouble3, 64, 64, d0, 8.0, 8.0, afloat[i]);
      }

      if (flag) {
         BoxUv.emit(mesh, texkey, bx, by, bz, 0.0, 0.0, 0.0, 3.0, 3.0, 3.0, 50, 0, 64, 64, d0, 8.0, 8.0, 0.0);
         BoxUv.emit(mesh, texkey, bx, by, bz, 13.0, 0.0, 0.0, 16.0, 3.0, 3.0, 50, 6, 64, 64, d0, 8.0, 8.0, 0.0);
      } else {
         BoxUv.emit(mesh, texkey, bx, by, bz, 0.0, 0.0, 13.0, 3.0, 3.0, 16.0, 50, 12, 64, 64, d0, 8.0, 8.0, 0.0);
         BoxUv.emit(mesh, texkey, bx, by, bz, 13.0, 0.0, 13.0, 16.0, 3.0, 16.0, 50, 18, 64, 64, d0, 8.0, 8.0, 0.0);
      }
   }
}
