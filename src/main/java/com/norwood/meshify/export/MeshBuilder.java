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
   private static final float WELD = 9.765625E-4F;
   private static final double DEGENERATE_AREA = 1.0E-8;
   private final Map<String, TexKey> keys = new LinkedHashMap<>();
   private final Map<String, List<MeshBuilder.Quad>> quads = new LinkedHashMap<>();
   private final Set<String> emissive = new LinkedHashSet<>();

   public void addQuad(TexKey key, float[][] pos, float[][] uv, float[] normal) {
      this.keys.putIfAbsent(key.id(), key);
      this.quads.computeIfAbsent(key.id(), k -> new ArrayList<>()).add(new MeshBuilder.Quad(pos, uv, normal));
   }

   /** Flags a texture as belonging to a light-emitting block, so it is not darkened by face shading. */
   public void markEmissive(String keyId) {
      this.emissive.add(keyId);
   }

   public Set<String> emissiveKeys() {
      return this.emissive;
   }

   public Map<String, TexKey> keys() {
      return this.keys;
   }

   public Map<String, List<MeshBuilder.Quad>> quads() {
      return this.quads;
   }

   public int faceCount() {
      int i = 0;

      for (List<MeshBuilder.Quad> list : this.quads.values()) {
         i += list.size();
      }

      return i;
   }

   public MeshBuilder.OptStats optimize() {
      int i = 0;
      int j = 0;
      Iterator<Entry<String, List<MeshBuilder.Quad>>> iterator = this.quads.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<String, List<MeshBuilder.Quad>> entry = iterator.next();
         List<MeshBuilder.Quad> list = entry.getValue();
         List<MeshBuilder.Quad> list1 = new ArrayList<>(list.size());
         Set<String> set = new HashSet<>(list.size() * 2);

         for (MeshBuilder.Quad meshbuilder$quad : list) {
            snap(meshbuilder$quad.pos());
            if (area(meshbuilder$quad.pos()) < 1.0E-8) {
               i++;
            } else if (!set.add(signature(meshbuilder$quad))) {
               j++;
            } else {
               list1.add(meshbuilder$quad);
            }
         }

         if (list1.isEmpty()) {
            iterator.remove();
            this.keys.remove(entry.getKey());
         } else {
            entry.setValue(list1);
         }
      }

      return new MeshBuilder.OptStats(1024, i, j);
   }

   private static void snap(float[][] pos) {
      for (float[] afloat : pos) {
         afloat[0] = Math.round(afloat[0] / 9.765625E-4F) * 9.765625E-4F;
         afloat[1] = Math.round(afloat[1] / 9.765625E-4F) * 9.765625E-4F;
         afloat[2] = Math.round(afloat[2] / 9.765625E-4F) * 9.765625E-4F;
      }
   }

   private static double area(float[][] p) {
      return triArea(p[0], p[1], p[2]) + triArea(p[0], p[2], p[3]);
   }

   private static double triArea(float[] a, float[] b, float[] c) {
      double d0 = b[0] - a[0];
      double d1 = b[1] - a[1];
      double d2 = b[2] - a[2];
      double d3 = c[0] - a[0];
      double d4 = c[1] - a[1];
      double d5 = c[2] - a[2];
      double d6 = d1 * d5 - d2 * d4;
      double d7 = d2 * d3 - d0 * d5;
      double d8 = d0 * d4 - d1 * d3;
      return 0.5 * Math.sqrt(d6 * d6 + d7 * d7 + d8 * d8);
   }

   private static String signature(MeshBuilder.Quad q) {
      StringBuilder stringbuilder = new StringBuilder(96);

      for (float[] afloat : q.pos()) {
         stringbuilder.append(Math.round(afloat[0] / 9.765625E-4F))
            .append(',')
            .append(Math.round(afloat[1] / 9.765625E-4F))
            .append(',')
            .append(Math.round(afloat[2] / 9.765625E-4F))
            .append(';');
      }

      for (float[] afloat2 : q.uv()) {
         stringbuilder.append(String.format(Locale.ROOT, "%.4f/%.4f;", afloat2[0], afloat2[1]));
      }

      float[] afloat1 = q.normal();
      stringbuilder.append(Math.round(afloat1[0])).append(',').append(Math.round(afloat1[1])).append(',').append(Math.round(afloat1[2]));
      return stringbuilder.toString();
   }

   public record OptStats(int weldGrid, int degenerateRemoved, int duplicateRemoved) {
   }

   public record Quad(float[][] pos, float[][] uv, float[] normal) {
   }
}
