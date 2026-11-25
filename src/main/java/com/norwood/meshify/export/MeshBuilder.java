package com.norwood.meshify.export;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public final class MeshBuilder {
   // Welding snaps every vertex to a grid of 1/1024 block.
   private static final int WELD_GRID = 1024;
   private static final float WELD_STEP = 1.0F / WELD_GRID;
   private static final double DEGENERATE_AREA = 1.0E-8;
   private static final float DEGENERATE_NORMAL_LENGTH = 1.0E-8F;
   private final Map<String, TextureKey> textures = new LinkedHashMap<>();
   private final Map<String, List<MeshBuilder.Quad>> quads = new LinkedHashMap<>();
   private final Set<String> emissiveTextureIds = new LinkedHashSet<>();

   public void addQuad(TextureKey texture, float[][] positions, float[][] uv, float[] normal) {
      this.textures.putIfAbsent(texture.id(), texture);
      this.quads.computeIfAbsent(texture.id(), k -> new ArrayList<>()).add(new MeshBuilder.Quad(positions, uv, normal));
   }

   // Flags a texture as belonging to a light-emitting block, so it is not darkened by face shading.
   public void markEmissive(String textureId) {
      this.emissiveTextureIds.add(textureId);
   }

   public Set<String> emissiveTextureIds() {
      return this.emissiveTextureIds;
   }

   public Map<String, TextureKey> textures() {
      return this.textures;
   }

   public Map<String, List<MeshBuilder.Quad>> quads() {
      return this.quads;
   }

   public int faceCount() {
      int count = 0;

      for (List<MeshBuilder.Quad> group : this.quads.values()) {
         count += group.size();
      }

      return count;
   }

   public MeshBuilder.OptimizationStats optimize() {
      int degenerate = 0;
      int duplicate = 0;
      Iterator<Entry<String, List<MeshBuilder.Quad>>> iterator = this.quads.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<String, List<MeshBuilder.Quad>> entry = iterator.next();
         List<MeshBuilder.Quad> group = entry.getValue();
         List<MeshBuilder.Quad> kept = new ArrayList<>(group.size());
         Set<String> seen = new HashSet<>(group.size() * 2);

         for (MeshBuilder.Quad quad : group) {
            snap(quad.positions());
            if (quadArea(quad.positions()) < DEGENERATE_AREA) {
               degenerate++;
            } else if (!seen.add(signature(quad))) {
               duplicate++;
            } else {
               kept.add(quad);
            }
         }

         if (kept.isEmpty()) {
            iterator.remove();
            this.textures.remove(entry.getKey());
         } else {
            entry.setValue(kept);
         }
      }

      return new MeshBuilder.OptimizationStats(WELD_GRID, degenerate, duplicate);
   }

   // Unit normal of the plane through a face's first three corners; straight up when they are collinear.
   public static float[] faceNormal(float[][] corners) {
      float abX = corners[1][0] - corners[0][0];
      float abY = corners[1][1] - corners[0][1];
      float abZ = corners[1][2] - corners[0][2];
      float acX = corners[2][0] - corners[0][0];
      float acY = corners[2][1] - corners[0][1];
      float acZ = corners[2][2] - corners[0][2];
      float normalX = abY * acZ - abZ * acY;
      float normalY = abZ * acX - abX * acZ;
      float normalZ = abX * acY - abY * acX;
      float length = (float)Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
      return length < DEGENERATE_NORMAL_LENGTH ? new float[]{0.0F, 1.0F, 0.0F} : new float[]{normalX / length, normalY / length, normalZ / length};
   }

   private static void snap(float[][] positions) {
      for (float[] corner : positions) {
         for (int axis = 0; axis < 3; axis++) {
            corner[axis] = Math.round(corner[axis] / WELD_STEP) * WELD_STEP;
         }
      }
   }

   private static double quadArea(float[][] corners) {
      return triangleArea(corners[0], corners[1], corners[2]) + triangleArea(corners[0], corners[2], corners[3]);
   }

   private static double triangleArea(float[] a, float[] b, float[] c) {
      double abX = b[0] - a[0];
      double abY = b[1] - a[1];
      double abZ = b[2] - a[2];
      double acX = c[0] - a[0];
      double acY = c[1] - a[1];
      double acZ = c[2] - a[2];
      double crossX = abY * acZ - abZ * acY;
      double crossY = abZ * acX - abX * acZ;
      double crossZ = abX * acY - abY * acX;
      return 0.5 * Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ);
   }

   private static String signature(MeshBuilder.Quad quad) {
      StringBuilder signature = new StringBuilder(96);

      for (float[] corner : quad.positions()) {
         signature.append(Math.round(corner[0] / WELD_STEP))
            .append(',')
            .append(Math.round(corner[1] / WELD_STEP))
            .append(',')
            .append(Math.round(corner[2] / WELD_STEP))
            .append(';');
      }

      for (float[] uv : quad.uv()) {
         signature.append(String.format(Locale.ROOT, "%.4f/%.4f;", uv[0], uv[1]));
      }

      float[] normal = quad.normal();
      signature.append(Math.round(normal[0])).append(',').append(Math.round(normal[1])).append(',').append(Math.round(normal[2]));
      return signature.toString();
   }

   public record OptimizationStats(int weldGrid, int degenerateRemoved, int duplicateRemoved) {
   }

   public record Quad(float[][] positions, float[][] uv, float[] normal) {
   }
}
