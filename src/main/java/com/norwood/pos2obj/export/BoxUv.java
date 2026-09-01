package com.norwood.pos2obj.export;

import net.minecraft.util.math.Direction;

public final class BoxUv {
   private BoxUv() {
   }

   public static void emit(
      MeshBuilder mesh,
      TexKey tex,
      double bx,
      double by,
      double bz,
      double x0,
      double y0,
      double z0,
      double x1,
      double y1,
      double z1,
      int u0,
      int v0,
      int texW,
      int texH,
      double yawDeg,
      double pivotX,
      double pivotZ,
      double inflate
   ) {
      emit(
         mesh,
         tex,
         bx,
         by,
         bz,
         x0,
         y0,
         z0,
         x1,
         y1,
         z1,
         u0,
         v0,
         (int)Math.round(x1 - x0),
         (int)Math.round(y1 - y0),
         (int)Math.round(z1 - z0),
         texW,
         texH,
         yawDeg,
         pivotX,
         pivotZ,
         inflate
      );
   }

   public static void emit(
      MeshBuilder mesh,
      TexKey tex,
      double bx,
      double by,
      double bz,
      double x0,
      double y0,
      double z0,
      double x1,
      double y1,
      double z1,
      int u0,
      int v0,
      int sx,
      int sy,
      int sz,
      int texW,
      int texH,
      double yawDeg,
      double pivotX,
      double pivotZ,
      double inflate
   ) {
      emit(mesh, tex, bx, by, bz, x0, y0, z0, x1, y1, z1, u0, v0, sx, sy, sz, texW, texH, yawDeg, pivotX, pivotZ, inflate, Direction.UP);
   }

   public static void emit(
      MeshBuilder mesh,
      TexKey tex,
      double bx,
      double by,
      double bz,
      double x0,
      double y0,
      double z0,
      double x1,
      double y1,
      double z1,
      int u0,
      int v0,
      int sx,
      int sy,
      int sz,
      int texW,
      int texH,
      double yawDeg,
      double pivotX,
      double pivotZ,
      double inflate,
      Direction up
   ) {
      double d0 = x0 - inflate;
      double d1 = y0 - inflate;
      double d2 = z0 - inflate;
      double d3 = x1 + inflate;
      double d4 = y1 + inflate;
      double d5 = z1 + inflate;
      face(
         mesh,
         tex,
         bx,
         by,
         bz,
         yawDeg,
         pivotX,
         pivotZ,
         up,
         new double[][]{{d3, d1, d2}, {d0, d1, d2}, {d0, d4, d2}, {d3, d4, d2}},
         uv(texW, texH, u0 + sz, v0 + sz + sy, u0 + sz + sx, v0 + sz + sy, u0 + sz + sx, v0 + sz, u0 + sz, v0 + sz),
         new float[]{0.0F, 0.0F, -1.0F}
      );
      face(
         mesh,
         tex,
         bx,
         by,
         bz,
         yawDeg,
         pivotX,
         pivotZ,
         up,
         new double[][]{{d0, d1, d5}, {d3, d1, d5}, {d3, d4, d5}, {d0, d4, d5}},
         uv(texW, texH, u0 + 2 * sz + sx, v0 + sz + sy, u0 + 2 * sz + 2 * sx, v0 + sz + sy, u0 + 2 * sz + 2 * sx, v0 + sz, u0 + 2 * sz + sx, v0 + sz),
         new float[]{0.0F, 0.0F, 1.0F}
      );
      face(
         mesh,
         tex,
         bx,
         by,
         bz,
         yawDeg,
         pivotX,
         pivotZ,
         up,
         new double[][]{{d3, d1, d5}, {d3, d1, d2}, {d3, d4, d2}, {d3, d4, d5}},
         uv(texW, texH, u0, v0 + sz + sy, u0 + sz, v0 + sz + sy, u0 + sz, v0 + sz, u0, v0 + sz),
         new float[]{1.0F, 0.0F, 0.0F}
      );
      face(
         mesh,
         tex,
         bx,
         by,
         bz,
         yawDeg,
         pivotX,
         pivotZ,
         up,
         new double[][]{{d0, d1, d2}, {d0, d1, d5}, {d0, d4, d5}, {d0, d4, d2}},
         uv(texW, texH, u0 + sz + sx, v0 + sz + sy, u0 + 2 * sz + sx, v0 + sz + sy, u0 + 2 * sz + sx, v0 + sz, u0 + sz + sx, v0 + sz),
         new float[]{-1.0F, 0.0F, 0.0F}
      );
      face(
         mesh,
         tex,
         bx,
         by,
         bz,
         yawDeg,
         pivotX,
         pivotZ,
         up,
         new double[][]{{d0, d4, d5}, {d3, d4, d5}, {d3, d4, d2}, {d0, d4, d2}},
         uv(texW, texH, u0 + sz + sx, v0, u0 + sz, v0, u0 + sz, v0 + sz, u0 + sz + sx, v0 + sz),
         new float[]{0.0F, 1.0F, 0.0F}
      );
      face(
         mesh,
         tex,
         bx,
         by,
         bz,
         yawDeg,
         pivotX,
         pivotZ,
         up,
         new double[][]{{d3, d1, d5}, {d0, d1, d5}, {d0, d1, d2}, {d3, d1, d2}},
         uv(texW, texH, u0 + sz + sx, v0, u0 + sz + 2 * sx, v0, u0 + sz + 2 * sx, v0 + sz, u0 + sz + sx, v0 + sz),
         new float[]{0.0F, -1.0F, 0.0F}
      );
   }

   private static float[][] uv(int texW, int texH, double... uvPx) {
      float[][] afloat = new float[4][2];

      for (int i = 0; i < 4; i++) {
         afloat[i][0] = (float)(uvPx[i * 2] / texW);
         afloat[i][1] = (float)(uvPx[i * 2 + 1] / texH);
      }

      return afloat;
   }

   public static void rotatedQuad(
      MeshBuilder mesh,
      TexKey tex,
      double bx,
      double by,
      double bz,
      double[][] cornersPx,
      double[][] uvPx,
      int texW,
      int texH,
      double yawDeg,
      double pivotX,
      double pivotZ,
      float[] normal
   ) {
      float[][] afloat = new float[4][2];

      for (int i = 0; i < 4; i++) {
         afloat[i][0] = (float)(uvPx[i][0] / texW);
         afloat[i][1] = (float)(uvPx[i][1] / texH);
      }

      face(mesh, tex, bx, by, bz, yawDeg, pivotX, pivotZ, Direction.UP, cornersPx, afloat, normal);
   }

   private static void face(
      MeshBuilder mesh,
      TexKey tex,
      double bx,
      double by,
      double bz,
      double yawDeg,
      double pivotX,
      double pivotZ,
      Direction up,
      double[][] cornersPx,
      float[][] uv,
      float[] normal
   ) {
      double d0 = Math.toRadians(yawDeg);
      double d1 = Math.cos(d0);
      double d2 = Math.sin(d0);
      float[][] afloat = new float[4][3];

      for (int i = 0; i < 4; i++) {
         double d3 = cornersPx[i][0] - pivotX;
         double d5 = cornersPx[i][2] - pivotZ;
         double d7 = d3 * d1 - d5 * d2 + pivotX;
         double d9 = cornersPx[i][1];
         double d11 = d3 * d2 + d5 * d1 + pivotZ;
         double d13 = d7;
         double d14 = d9;
         double d15 = d11;
         switch (up) {
            case DOWN:
               d14 = 16.0 - d9;
               d15 = 16.0 - d11;
               break;
            case NORTH:
               d14 = d11;
               d15 = 16.0 - d9;
               break;
            case SOUTH:
               d14 = 16.0 - d11;
               d15 = d9;
               break;
            case EAST:
               d13 = d9;
               d14 = 16.0 - d7;
               break;
            case WEST:
               d13 = 16.0 - d9;
               d14 = d7;
         }

         afloat[i][0] = (float)(bx + d13 / 16.0);
         afloat[i][1] = (float)(by + d14 / 16.0);
         afloat[i][2] = (float)(bz + d15 / 16.0);
      }

      double d16 = normal[0] * d1 - normal[2] * d2;
      double d4 = normal[1];
      double d6 = normal[0] * d2 + normal[2] * d1;
      double d8 = d16;
      double d10 = d4;
      double d12 = d6;
      switch (up) {
         case DOWN:
            d10 = -d4;
            d12 = -d6;
            break;
         case NORTH:
            d10 = d6;
            d12 = -d4;
            break;
         case SOUTH:
            d10 = -d6;
            d12 = d4;
            break;
         case EAST:
            d8 = d4;
            d10 = -d16;
            break;
         case WEST:
            d8 = -d4;
            d10 = d16;
      }

      mesh.addQuad(tex, afloat, uv, new float[]{(float)d8, (float)d10, (float)d12});
   }
}
