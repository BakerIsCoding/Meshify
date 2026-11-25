package com.norwood.meshify.export;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// An entity model's texture sheet placed on a block; draws the model's cuboids and quads, given in model pixels (16 to a block).
public record BoxUv(Placement placement, TextureKey texture, int textureWidth, int textureHeight, Direction up, boolean drawnUpright) {
   private static final double PIXELS_PER_BLOCK = 16.0;
   private static final double PIVOT = PIXELS_PER_BLOCK / 2;
   // The bottom of a double chest: every column of its 64x64 unwrap has a different width, so a wrong face can't pass the check by chance.
   private static final AABB CHECK_BOX = new AABB(1.0, 0.0, 1.0, 16.0, 10.0, 15.0);

   public BoxUv(Placement placement, TextureKey texture, int textureWidth, int textureHeight) {
      this(placement, texture, textureWidth, textureHeight, Direction.UP, false);
   }

   // Most block-entity renderers turn the model 180° about Z; this is for the ones that don't, like the chest's.
   public BoxUv upright() {
      return new BoxUv(this.placement, this.texture, this.textureWidth, this.textureHeight, this.up, true);
   }

   // Tips the model over so its top faces up, as the shulker box renderer does with its facing.
   public BoxUv withUp(Direction up) {
      return new BoxUv(this.placement, this.texture, this.textureWidth, this.textureHeight, up, this.drawnUpright);
   }

   // A cuboid unwrapped from (u0, v0) at its own size.
   public void box(AABB box, int u0, int v0) {
      this.box(box, u0, v0, unwrapSize(box));
   }

   // The cuboid grown by inflate on every side but unwrapped at its own size, like a head's hat layer.
   public void inflatedBox(AABB box, int u0, int v0, double inflate) {
      this.box(box.inflate(inflate), u0, v0, unwrapSize(box));
   }

   // A cuboid with an unwrap of another size, for models the renderer draws scaled, smaller than their unwrap.
   public void box(AABB box, int u0, int v0, Vec3i unwrapSize) {
      int width = unwrapSize.getX();
      int height = unwrapSize.getY();
      int depth = unwrapSize.getZ();
      // The standard cuboid unwrap: top and bottom in the first row, the four sides below them.
      float[][] north = this.normalizedUv(
         u0 + depth, v0 + depth + height, u0 + depth + width, v0 + depth + height, u0 + depth + width, v0 + depth, u0 + depth, v0 + depth
      );
      float[][] south = this.normalizedUv(
         u0 + 2 * depth + width, v0 + depth + height, u0 + 2 * depth + 2 * width, v0 + depth + height,
         u0 + 2 * depth + 2 * width, v0 + depth, u0 + 2 * depth + width, v0 + depth
      );
      float[][] east = this.normalizedUv(u0, v0 + depth + height, u0 + depth, v0 + depth + height, u0 + depth, v0 + depth, u0, v0 + depth);
      float[][] west = this.normalizedUv(
         u0 + depth + width, v0 + depth + height, u0 + 2 * depth + width, v0 + depth + height, u0 + 2 * depth + width, v0 + depth, u0 + depth + width, v0 + depth
      );
      float[][] top = this.normalizedUv(u0 + depth + width, v0, u0 + depth, v0, u0 + depth, v0 + depth, u0 + depth + width, v0 + depth);
      float[][] bottom = this.normalizedUv(u0 + depth + width, v0, u0 + depth + 2 * width, v0, u0 + depth + 2 * width, v0 + depth, u0 + depth + width, v0 + depth);
      if (this.drawnUpright) {
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

      double minX = box.minX;
      double minY = box.minY;
      double minZ = box.minZ;
      double maxX = box.maxX;
      double maxY = box.maxY;
      double maxZ = box.maxZ;
      this.face(new double[][]{{maxX, minY, minZ}, {minX, minY, minZ}, {minX, maxY, minZ}, {maxX, maxY, minZ}}, north, new float[]{0.0F, 0.0F, -1.0F});
      this.face(new double[][]{{minX, minY, maxZ}, {maxX, minY, maxZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}}, south, new float[]{0.0F, 0.0F, 1.0F});
      this.face(new double[][]{{maxX, minY, maxZ}, {maxX, minY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}}, east, new float[]{1.0F, 0.0F, 0.0F});
      this.face(new double[][]{{minX, minY, minZ}, {minX, minY, maxZ}, {minX, maxY, maxZ}, {minX, maxY, minZ}}, west, new float[]{-1.0F, 0.0F, 0.0F});
      this.face(new double[][]{{minX, maxY, maxZ}, {maxX, maxY, maxZ}, {maxX, maxY, minZ}, {minX, maxY, minZ}}, top, new float[]{0.0F, 1.0F, 0.0F});
      this.face(new double[][]{{maxX, minY, maxZ}, {minX, minY, maxZ}, {minX, minY, minZ}, {maxX, minY, minZ}}, bottom, new float[]{0.0F, -1.0F, 0.0F});
   }

   // One quad, its corners in model pixels and its UVs in texture pixels, both in the same corner order.
   public void quad(double[][] cornersPx, double[][] uvPx, float[] normal) {
      float[][] uv = new float[4][2];

      for (int k = 0; k < 4; k++) {
         uv[k][0] = (float)(uvPx[k][0] / this.textureWidth);
         uv[k][1] = (float)(uvPx[k][1] / this.textureHeight);
      }

      this.face(cornersPx, uv, normal);
   }

   private static Vec3i unwrapSize(AABB box) {
      return new Vec3i((int)Math.round(box.maxX - box.minX), (int)Math.round(box.maxY - box.minY), (int)Math.round(box.maxZ - box.minZ));
   }

   // Half a turn of a face's texture, so corner 0 reads what corner 2 used to.
   private static float[][] rotate180(float[][] uv) {
      return new float[][]{uv[2], uv[3], uv[0], uv[1]};
   }

   private float[][] normalizedUv(double... uvPx) {
      float[][] uv = new float[4][2];

      for (int k = 0; k < 4; k++) {
         uv[k][0] = (float)(uvPx[k * 2] / this.textureWidth);
         uv[k][1] = (float)(uvPx[k * 2 + 1] / this.textureHeight);
      }

      return uv;
   }

   private void face(double[][] cornersPx, float[][] uv, float[] normal) {
      double yaw = Math.toRadians(this.placement.yawDegrees());
      double cos = Math.cos(yaw);
      double sin = Math.sin(yaw);
      Vec3 origin = this.placement.origin();
      float[][] positions = new float[4][3];

      for (int k = 0; k < 4; k++) {
         double dx = cornersPx[k][0] - PIVOT;
         double dz = cornersPx[k][2] - PIVOT;
         double[] rotated = reorient(this.up, dx * cos - dz * sin + PIVOT, cornersPx[k][1], dx * sin + dz * cos + PIVOT, v -> PIXELS_PER_BLOCK - v);
         positions[k][0] = (float)(origin.x + rotated[0] / PIXELS_PER_BLOCK);
         positions[k][1] = (float)(origin.y + rotated[1] / PIXELS_PER_BLOCK);
         positions[k][2] = (float)(origin.z + rotated[2] / PIXELS_PER_BLOCK);
      }

      double[] rotatedNormal = reorient(this.up, normal[0] * cos - normal[2] * sin, normal[1], normal[0] * sin + normal[2] * cos, v -> -v);
      this.placement.mesh().addQuad(this.texture, positions, uv, new float[]{(float)rotatedNormal[0], (float)rotatedNormal[1], (float)rotatedNormal[2]});
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

   // Run with gradlew boxUvCheck.
   public static void main(String[] args) {
      MeshBuilder defaultBox = new MeshBuilder();
      checkSheet(defaultBox).box(CHECK_BOX, 0, 19);
      MeshBuilder uprightBox = new MeshBuilder();
      checkSheet(uprightBox).upright().box(CHECK_BOX, 0, 19);
      check(uSpan(defaultBox, Direction.UP), new float[]{14.0F, 29.0F}, "default top face");
      check(uSpan(defaultBox, Direction.EAST), new float[]{0.0F, 14.0F}, "default east face");
      check(uSpan(uprightBox, Direction.UP), new float[]{29.0F, 44.0F}, "upright top face");
      check(uSpan(uprightBox, Direction.DOWN), new float[]{14.0F, 29.0F}, "upright bottom face");
      check(uSpan(uprightBox, Direction.EAST), new float[]{29.0F, 43.0F}, "upright east face");
      check(uSpan(uprightBox, Direction.WEST), new float[]{0.0F, 14.0F}, "upright west face");
      // The depth faces keep their column either way; only the corner order turns over.
      check(uSpan(uprightBox, Direction.NORTH), new float[]{14.0F, 29.0F}, "upright north face");
      float[] uprightCorner = firstCorner(uprightBox);
      float[] defaultCorner = firstCorner(defaultBox);
      check(uprightCorner[0] != defaultCorner[0] || uprightCorner[1] != defaultCorner[1], "upright turns the north face over");
      System.out.println("BoxUv self-check passed");
   }

   private static BoxUv checkSheet(MeshBuilder mesh) {
      return new BoxUv(new Placement(mesh, Vec3.ZERO, 0.0), TextureKey.generated("t", client -> null), 64, 64);
   }

   // The U range, in texture pixels, of the face pointing toward side.
   private static float[] uSpan(MeshBuilder box, Direction side) {
      float[] expected = {side.getStepX(), side.getStepY(), side.getStepZ()};

      for (MeshBuilder.Quad quad : box.quads().values().iterator().next()) {
         float[] normal = quad.normal();
         if (normal[0] == expected[0] && normal[1] == expected[1] && normal[2] == expected[2]) {
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;

            for (float[] uv : quad.uv()) {
               min = Math.min(min, uv[0] * 64.0F);
               max = Math.max(max, uv[0] * 64.0F);
            }

            return new float[]{min, max};
         }
      }

      throw new AssertionError("BoxUv self-check: no face toward " + side);
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
}
