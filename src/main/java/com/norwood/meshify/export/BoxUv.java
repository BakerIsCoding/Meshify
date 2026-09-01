package com.norwood.meshify.export;

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
      emit(mesh, tex, bx, by, bz, x0, y0, z0, x1, y1, z1, u0, v0, texW, texH, yawDeg, pivotX, pivotZ, inflate, false);
   }

   /** @see #emit(MeshBuilder, TexKey, double, double, double, double, double, double, double, double, double, int, int, int, int, int, int, int, double, double, double, double, Direction, boolean) */
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
      double inflate,
      boolean upright
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
         inflate,
         Direction.UP,
         upright
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
      emit(mesh, tex, bx, by, bz, x0, y0, z0, x1, y1, z1, u0, v0, sx, sy, sz, texW, texH, yawDeg, pivotX, pivotZ, inflate, up, false);
   }

   /**
    * Minecraft unwraps every cuboid the same way, but most block-entity models are drawn through a
    * renderer that rotates them 180° about Z (skulls, shulker boxes), which puts the sheet's
    * top/bottom and left/right halves the other way round from block space. Models the game draws
    * upright — the chest — need {@code upright} set: without it the lid shows its dark underside
    * ("a shadow on top") and a double chest picks the wrong end cap, which is transparent.
    */
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
      Direction up,
      boolean upright
   ) {
      double d0 = x0 - inflate;
      double d1 = y0 - inflate;
      double d2 = z0 - inflate;
      double d3 = x1 + inflate;
      double d4 = y1 + inflate;
      double d5 = z1 + inflate;
      float[][] afloat = uv(texW, texH, u0 + sz, v0 + sz + sy, u0 + sz + sx, v0 + sz + sy, u0 + sz + sx, v0 + sz, u0 + sz, v0 + sz);
      float[][] afloat1 = uv(
         texW, texH, u0 + 2 * sz + sx, v0 + sz + sy, u0 + 2 * sz + 2 * sx, v0 + sz + sy, u0 + 2 * sz + 2 * sx, v0 + sz, u0 + 2 * sz + sx, v0 + sz
      );
      float[][] afloat2 = uv(texW, texH, u0, v0 + sz + sy, u0 + sz, v0 + sz + sy, u0 + sz, v0 + sz, u0, v0 + sz);
      float[][] afloat3 = uv(texW, texH, u0 + sz + sx, v0 + sz + sy, u0 + 2 * sz + sx, v0 + sz + sy, u0 + 2 * sz + sx, v0 + sz, u0 + sz + sx, v0 + sz);
      float[][] afloat4 = uv(texW, texH, u0 + sz + sx, v0, u0 + sz, v0, u0 + sz, v0 + sz, u0 + sz + sx, v0 + sz);
      float[][] afloat5 = uv(texW, texH, u0 + sz + sx, v0, u0 + sz + 2 * sx, v0, u0 + sz + 2 * sx, v0 + sz, u0 + sz + sx, v0 + sz);
      if (upright) {
         // The 180° turn keeps every face on its own side of the box, so it only re-reads the
         // sheet: the two side halves trade places (and roll over with the flip), the depth faces
         // roll over in place, and the top and bottom halves trade places.
         afloat = rotate180(afloat);
         afloat1 = rotate180(afloat1);
         float[][] afloat6 = afloat2;
         afloat2 = rotate180(afloat3);
         afloat3 = rotate180(afloat6);
         afloat6 = afloat4;
         afloat4 = afloat5;
         afloat5 = afloat6;
      }

      face(
         mesh, tex, bx, by, bz, yawDeg, pivotX, pivotZ, up,
         new double[][]{{d3, d1, d2}, {d0, d1, d2}, {d0, d4, d2}, {d3, d4, d2}}, afloat, new float[]{0.0F, 0.0F, -1.0F}
      );
      face(
         mesh, tex, bx, by, bz, yawDeg, pivotX, pivotZ, up,
         new double[][]{{d0, d1, d5}, {d3, d1, d5}, {d3, d4, d5}, {d0, d4, d5}}, afloat1, new float[]{0.0F, 0.0F, 1.0F}
      );
      face(
         mesh, tex, bx, by, bz, yawDeg, pivotX, pivotZ, up,
         new double[][]{{d3, d1, d5}, {d3, d1, d2}, {d3, d4, d2}, {d3, d4, d5}}, afloat2, new float[]{1.0F, 0.0F, 0.0F}
      );
      face(
         mesh, tex, bx, by, bz, yawDeg, pivotX, pivotZ, up,
         new double[][]{{d0, d1, d2}, {d0, d1, d5}, {d0, d4, d5}, {d0, d4, d2}}, afloat3, new float[]{-1.0F, 0.0F, 0.0F}
      );
      face(
         mesh, tex, bx, by, bz, yawDeg, pivotX, pivotZ, up,
         new double[][]{{d0, d4, d5}, {d3, d4, d5}, {d3, d4, d2}, {d0, d4, d2}}, afloat4, new float[]{0.0F, 1.0F, 0.0F}
      );
      face(
         mesh, tex, bx, by, bz, yawDeg, pivotX, pivotZ, up,
         new double[][]{{d3, d1, d5}, {d0, d1, d5}, {d0, d1, d2}, {d3, d1, d2}}, afloat5, new float[]{0.0F, -1.0F, 0.0F}
      );
   }

   /** Half a turn of a face's texture, so corner 0 reads what corner 2 used to. */
   private static float[][] rotate180(float[][] uv) {
      return new float[][]{uv[2], uv[3], uv[0], uv[1]};
   }

   // ---------------------------------------------------------------- self-check

   /** Run with {@code gradlew boxUvCheck}. */
   public static void main(String[] args) {
      // The double-chest base: every column of the 64x64 unwrap is a different width, so a face
      // reading the wrong one cannot pass by coincidence. Columns are 0-14, 14-29, 29-43, 43-58,
      // and the top row splits 14-29 / 29-44.
      check(uSpan(false, 0.0F, 1.0F, 0.0F), new float[]{14.0F, 29.0F}, "default top face");
      check(uSpan(false, 1.0F, 0.0F, 0.0F), new float[]{0.0F, 14.0F}, "default east face");
      check(uSpan(true, 0.0F, 1.0F, 0.0F), new float[]{29.0F, 44.0F}, "upright top face");
      check(uSpan(true, 0.0F, -1.0F, 0.0F), new float[]{14.0F, 29.0F}, "upright bottom face");
      check(uSpan(true, 1.0F, 0.0F, 0.0F), new float[]{29.0F, 43.0F}, "upright east face");
      check(uSpan(true, -1.0F, 0.0F, 0.0F), new float[]{0.0F, 14.0F}, "upright west face");
      // The depth faces keep their column either way; only the corner order turns over.
      check(uSpan(true, 0.0F, 0.0F, -1.0F), new float[]{14.0F, 29.0F}, "upright north face");
      float[] afloat = firstCorner(box(true));
      float[] afloat1 = firstCorner(box(false));
      check(afloat[0] != afloat1[0] || afloat[1] != afloat1[1], "upright turns the north face over");
      System.out.println("BoxUv self-check passed");
   }

   private static MeshBuilder box(boolean upright) {
      MeshBuilder meshbuilder = new MeshBuilder();
      emit(meshbuilder, TexKey.generated("t", mc -> null), 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 16.0, 10.0, 15.0, 0, 19, 64, 64, 0.0, 8.0, 8.0, 0.0, upright);
      return meshbuilder;
   }

   /** The horizontal texture span, in sheet pixels, of the face pointing along {@code normal}. */
   private static float[] uSpan(boolean upright, float nx, float ny, float nz) {
      for (MeshBuilder.Quad meshbuilder$quad : box(upright).quads().values().iterator().next()) {
         float[] afloat = meshbuilder$quad.normal();
         if (afloat[0] == nx && afloat[1] == ny && afloat[2] == nz) {
            float f = Float.MAX_VALUE;
            float f1 = -Float.MAX_VALUE;

            for (float[] afloat1 : meshbuilder$quad.uv()) {
               f = Math.min(f, afloat1[0] * 64.0F);
               f1 = Math.max(f1, afloat1[0] * 64.0F);
            }

            return new float[]{f, f1};
         }
      }

      throw new AssertionError("BoxUv self-check: no face with normal " + nx + "," + ny + "," + nz);
   }

   private static float[] firstCorner(MeshBuilder mesh) {
      return mesh.quads().values().iterator().next().get(0).uv()[0];
   }

   private static void check(float[] got, float[] want, String what) {
      if (!java.util.Arrays.equals(got, want)) {
         throw new AssertionError(
            "BoxUv self-check failed: " + what + " (got " + java.util.Arrays.toString(got) + ", want " + java.util.Arrays.toString(want) + ")"
         );
      }
   }

   private static void check(boolean ok, String what) {
      if (!ok) {
         throw new AssertionError("BoxUv self-check failed: " + what);
      }
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
