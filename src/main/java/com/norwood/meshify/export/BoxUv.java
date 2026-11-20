package com.norwood.meshify.export;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.core.Direction;

// Entity-model cuboids and quads, in model pixels (16 to a block), turned about the block's vertical centre line.
public final class BoxUv {
   private static final double PIXELS_PER_BLOCK = 16.0;
   private static final double PIVOT = PIXELS_PER_BLOCK / 2;

   private BoxUv() {
   }

   public static void emit(
      MeshBuilder mesh,
      TextureKey texture,
      double blockX,
      double blockY,
      double blockZ,
      double x0,
      double y0,
      double z0,
      double x1,
      double y1,
      double z1,
      int u0,
      int v0,
      int textureWidth,
      int textureHeight,
      double yawDegrees,
      double inflate
   ) {
      emit(mesh, texture, blockX, blockY, blockZ, x0, y0, z0, x1, y1, z1, u0, v0, textureWidth, textureHeight, yawDegrees, inflate, false);
   }

   // Takes the unwrap size from the cuboid itself.
   public static void emit(
      MeshBuilder mesh,
      TextureKey texture,
      double blockX,
      double blockY,
      double blockZ,
      double x0,
      double y0,
      double z0,
      double x1,
      double y1,
      double z1,
      int u0,
      int v0,
      int textureWidth,
      int textureHeight,
      double yawDegrees,
      double inflate,
      boolean upright
   ) {
      emit(
         mesh,
         texture,
         blockX,
         blockY,
         blockZ,
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
         textureWidth,
         textureHeight,
         yawDegrees,
         inflate,
         Direction.UP,
         upright
      );
   }

   /**
    * Most block-entity renderers turn the model 180° about Z; pass {@code upright} for ones that don't, like the chest.
    * {@code sx}, {@code sy} and {@code sz} size the texture unwrap, which scaled models draw smaller than the cuboid.
    */
   public static void emit(
      MeshBuilder mesh,
      TextureKey texture,
      double blockX,
      double blockY,
      double blockZ,
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
      int textureWidth,
      int textureHeight,
      double yawDegrees,
      double inflate,
      Direction up,
      boolean upright
   ) {
      double minX = x0 - inflate;
      double minY = y0 - inflate;
      double minZ = z0 - inflate;
      double maxX = x1 + inflate;
      double maxY = y1 + inflate;
      double maxZ = z1 + inflate;
      // The standard cuboid unwrap: top and bottom in the first row, the four sides below them.
      float[][] north = normalizedUv(textureWidth, textureHeight, u0 + sz, v0 + sz + sy, u0 + sz + sx, v0 + sz + sy, u0 + sz + sx, v0 + sz, u0 + sz, v0 + sz);
      float[][] south = normalizedUv(
         textureWidth, textureHeight, u0 + 2 * sz + sx, v0 + sz + sy, u0 + 2 * sz + 2 * sx, v0 + sz + sy, u0 + 2 * sz + 2 * sx, v0 + sz, u0 + 2 * sz + sx, v0 + sz
      );
      float[][] east = normalizedUv(textureWidth, textureHeight, u0, v0 + sz + sy, u0 + sz, v0 + sz + sy, u0 + sz, v0 + sz, u0, v0 + sz);
      float[][] west = normalizedUv(textureWidth, textureHeight, u0 + sz + sx, v0 + sz + sy, u0 + 2 * sz + sx, v0 + sz + sy, u0 + 2 * sz + sx, v0 + sz, u0 + sz + sx, v0 + sz);
      float[][] top = normalizedUv(textureWidth, textureHeight, u0 + sz + sx, v0, u0 + sz, v0, u0 + sz, v0 + sz, u0 + sz + sx, v0 + sz);
      float[][] bottom = normalizedUv(textureWidth, textureHeight, u0 + sz + sx, v0, u0 + sz + 2 * sx, v0, u0 + sz + 2 * sx, v0 + sz, u0 + sz + sx, v0 + sz);
      if (upright) {
         // The 180° turn keeps every face on its side of the box, so only the sheet lookup changes.
         north = rotate180(north);
         south = rotate180(south);
         float[][] oldEast = east;
         east = rotate180(west);
         west = rotate180(oldEast);
         float[][] oldTop = top;
         top = bottom;
         bottom = oldTop;
      }

      emitFace(
         mesh, texture, blockX, blockY, blockZ, yawDegrees, up,
         new double[][]{{maxX, minY, minZ}, {minX, minY, minZ}, {minX, maxY, minZ}, {maxX, maxY, minZ}}, north, new float[]{0.0F, 0.0F, -1.0F}
      );
      emitFace(
         mesh, texture, blockX, blockY, blockZ, yawDegrees, up,
         new double[][]{{minX, minY, maxZ}, {maxX, minY, maxZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}}, south, new float[]{0.0F, 0.0F, 1.0F}
      );
      emitFace(
         mesh, texture, blockX, blockY, blockZ, yawDegrees, up,
         new double[][]{{maxX, minY, maxZ}, {maxX, minY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}}, east, new float[]{1.0F, 0.0F, 0.0F}
      );
      emitFace(
         mesh, texture, blockX, blockY, blockZ, yawDegrees, up,
         new double[][]{{minX, minY, minZ}, {minX, minY, maxZ}, {minX, maxY, maxZ}, {minX, maxY, minZ}}, west, new float[]{-1.0F, 0.0F, 0.0F}
      );
      emitFace(
         mesh, texture, blockX, blockY, blockZ, yawDegrees, up,
         new double[][]{{minX, maxY, maxZ}, {maxX, maxY, maxZ}, {maxX, maxY, minZ}, {minX, maxY, minZ}}, top, new float[]{0.0F, 1.0F, 0.0F}
      );
      emitFace(
         mesh, texture, blockX, blockY, blockZ, yawDegrees, up,
         new double[][]{{maxX, minY, maxZ}, {minX, minY, maxZ}, {minX, minY, minZ}, {maxX, minY, minZ}}, bottom, new float[]{0.0F, -1.0F, 0.0F}
      );
   }

   // Half a turn of a face's texture, so corner 0 reads what corner 2 used to.
   private static float[][] rotate180(float[][] uv) {
      return new float[][]{uv[2], uv[3], uv[0], uv[1]};
   }

   // Run with gradlew boxUvCheck.
   public static void main(String[] args) {
      // Every column of the 64x64 double-chest unwrap has a different width, so a wrong face can't pass by chance.
      check(uSpan(false, 0.0F, 1.0F, 0.0F), new float[]{14.0F, 29.0F}, "default top face");
      check(uSpan(false, 1.0F, 0.0F, 0.0F), new float[]{0.0F, 14.0F}, "default east face");
      check(uSpan(true, 0.0F, 1.0F, 0.0F), new float[]{29.0F, 44.0F}, "upright top face");
      check(uSpan(true, 0.0F, -1.0F, 0.0F), new float[]{14.0F, 29.0F}, "upright bottom face");
      check(uSpan(true, 1.0F, 0.0F, 0.0F), new float[]{29.0F, 43.0F}, "upright east face");
      check(uSpan(true, -1.0F, 0.0F, 0.0F), new float[]{0.0F, 14.0F}, "upright west face");
      // The depth faces keep their column either way; only the corner order turns over.
      check(uSpan(true, 0.0F, 0.0F, -1.0F), new float[]{14.0F, 29.0F}, "upright north face");
      float[] uprightCorner = firstCorner(box(true));
      float[] defaultCorner = firstCorner(box(false));
      check(uprightCorner[0] != defaultCorner[0] || uprightCorner[1] != defaultCorner[1], "upright turns the north face over");
      System.out.println("BoxUv self-check passed");
   }

   private static MeshBuilder box(boolean upright) {
      MeshBuilder mesh = new MeshBuilder();
      emit(mesh, TextureKey.generated("t", client -> null), 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 16.0, 10.0, 15.0, 0, 19, 64, 64, 0.0, 0.0, upright);
      return mesh;
   }

   private static float[] uSpan(boolean upright, float nx, float ny, float nz) {
      for (MeshBuilder.Quad quad : box(upright).quads().values().iterator().next()) {
         float[] normal = quad.normal();
         if (normal[0] == nx && normal[1] == ny && normal[2] == nz) {
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;

            for (float[] uv : quad.uv()) {
               min = Math.min(min, uv[0] * 64.0F);
               max = Math.max(max, uv[0] * 64.0F);
            }

            return new float[]{min, max};
         }
      }

      throw new AssertionError("BoxUv self-check: no face with normal " + nx + "," + ny + "," + nz);
   }

   private static float[] firstCorner(MeshBuilder mesh) {
      return mesh.quads().values().iterator().next().get(0).uv()[0];
   }

   private static void check(float[] actual, float[] expected, String description) {
      if (!Arrays.equals(actual, expected)) {
         throw new AssertionError("BoxUv self-check failed: " + description + " (got " + Arrays.toString(actual) + ", want " + Arrays.toString(expected) + ")");
      }
   }

   private static void check(boolean passed, String description) {
      if (!passed) {
         throw new AssertionError("BoxUv self-check failed: " + description);
      }
   }

   private static float[][] normalizedUv(int textureWidth, int textureHeight, double... uvPx) {
      float[][] uv = new float[4][2];

      for (int k = 0; k < 4; k++) {
         uv[k][0] = (float)(uvPx[k * 2] / textureWidth);
         uv[k][1] = (float)(uvPx[k * 2 + 1] / textureHeight);
      }

      return uv;
   }

   public static void rotatedQuad(
      MeshBuilder mesh, TextureKey texture, double blockX, double blockY, double blockZ, double[][] cornersPx, double[][] uvPx, int textureWidth, int textureHeight, double yawDegrees, float[] normal
   ) {
      float[][] uv = new float[4][2];

      for (int k = 0; k < 4; k++) {
         uv[k][0] = (float)(uvPx[k][0] / textureWidth);
         uv[k][1] = (float)(uvPx[k][1] / textureHeight);
      }

      emitFace(mesh, texture, blockX, blockY, blockZ, yawDegrees, Direction.UP, cornersPx, uv, normal);
   }

   private static void emitFace(
      MeshBuilder mesh, TextureKey texture, double blockX, double blockY, double blockZ, double yawDegrees, Direction up, double[][] cornersPx, float[][] uv, float[] normal
   ) {
      double yaw = Math.toRadians(yawDegrees);
      double cos = Math.cos(yaw);
      double sin = Math.sin(yaw);
      float[][] positions = new float[4][3];

      for (int k = 0; k < 4; k++) {
         double dx = cornersPx[k][0] - PIVOT;
         double dz = cornersPx[k][2] - PIVOT;
         double[] rotated = reorient(up, dx * cos - dz * sin + PIVOT, cornersPx[k][1], dx * sin + dz * cos + PIVOT, v -> PIXELS_PER_BLOCK - v);
         positions[k][0] = (float)(blockX + rotated[0] / PIXELS_PER_BLOCK);
         positions[k][1] = (float)(blockY + rotated[1] / PIXELS_PER_BLOCK);
         positions[k][2] = (float)(blockZ + rotated[2] / PIXELS_PER_BLOCK);
      }

      double[] rotatedNormal = reorient(up, normal[0] * cos - normal[2] * sin, normal[1], normal[0] * sin + normal[2] * cos, v -> -v);
      mesh.addQuad(texture, positions, uv, new float[]{(float)rotatedNormal[0], (float)rotatedNormal[1], (float)rotatedNormal[2]});
   }

   // Tips a point or direction over so the model's top faces the up direction; flip mirrors one coordinate across the block.
   private static double[] reorient(Direction up, double x, double y, double z, DoubleUnaryOperator flip) {
      return switch (up) {
         case UP -> new double[]{x, y, z};
         case DOWN -> new double[]{x, flip.applyAsDouble(y), flip.applyAsDouble(z)};
         case NORTH -> new double[]{x, z, flip.applyAsDouble(y)};
         case SOUTH -> new double[]{x, flip.applyAsDouble(z), y};
         case EAST -> new double[]{y, flip.applyAsDouble(x), z};
         case WEST -> new double[]{flip.applyAsDouble(y), x, z};
      };
   }
}
