package com.norwood.pos2obj.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.AxisDirection;

public final class BakeCollector {
   public static final int LAYER_SOLID = 0;
   public static final int LAYER_ALPHA = 1;
   private static final float EPS = 1.0E-4F;
   private static final int BORDER = 2;
   private final int uvSize;
   private final int maxAtlas;
   private final Map<BakeCollector.PlaneKey, Map<Long, BakeCollector.Cell>> planes = new HashMap<>();
   private final Map<String, NativeImage> sources = new HashMap<>();
   private int facesConsumed;

   public BakeCollector(int uvSize, int maxAtlas) {
      this.uvSize = Math.max(1, uvSize);
      int i = Math.max(256, Math.min(16384, maxAtlas));
      int j = 256;

      while (j * 2 <= i) {
         j *= 2;
      }

      this.maxAtlas = j;
   }

   public boolean tryAdd(double ox, double oy, double oz, Direction face, TexKey tex, float[][] pos, float[][] uv, int layer) {
      int i = face.getAxis().ordinal();
      boolean flag = face.getDirection() == AxisDirection.POSITIVE;
      double[] adouble = new double[]{ox, oy, oz};
      float f = (float)(adouble[i] + (flag ? 1 : 0));

      for (float[] afloat : pos) {
         if (Math.abs(afloat[i] - f) > 1.0E-4F) {
            return false;
         }
      }

      int i1 = i == 0 ? 1 : 0;
      int j1 = i == 2 ? 1 : 2;
      float[][] afloat1 = new float[4][];
      int k1 = 0;

      while (k1 < 4) {
         double d0 = pos[k1][i1] - adouble[i1];
         double d1 = pos[k1][j1] - adouble[j1];
         int j = (int)Math.round(d0);
         int k = (int)Math.round(d1);
         if (j >= 0 && j <= 1 && k >= 0 && k <= 1) {
            if (!(Math.abs(d0 - j) > 1.0E-4F) && !(Math.abs(d1 - k) > 1.0E-4F)) {
               int l = k * 2 + j;
               if (afloat1[l] != null) {
                  return false;
               }

               afloat1[l] = new float[]{uv[k1][0], uv[k1][1]};
               k1++;
               continue;
            }

            return false;
         }

         return false;
      }

      for (float[] afloat2 : afloat1) {
         if (afloat2 == null) {
            return false;
         }
      }

      BakeCollector.PlaneKey bakecollector$planekey = new BakeCollector.PlaneKey(i, Math.round(f), flag, layer);
      long l1 = pack((int)Math.round(adouble[i1]), (int)Math.round(adouble[j1]));
      this.planes.computeIfAbsent(bakecollector$planekey, k -> new HashMap<>()).put(l1, new BakeCollector.Cell(tex, afloat1));
      this.facesConsumed++;
      return true;
   }

   public boolean isEmpty() {
      return this.planes.isEmpty();
   }

   private static long pack(int u, int v) {
      return (long)u << 32 ^ v & 4294967295L;
   }

   public BakeCollector.Stats flush(MeshBuilder mesh, List<String> warnings) {
      int i = 0;
      int j = 0;
      long k = 0L;

      for (int l = 0; l <= 1; l++) {
         List<BakeCollector.Rect> list = new ArrayList<>();

         for (Entry<BakeCollector.PlaneKey, Map<Long, BakeCollector.Cell>> entry : this.planes.entrySet()) {
            if (entry.getKey().layer() == l) {
               this.greedy(entry.getKey(), entry.getValue(), list);
            }
         }

         if (!list.isEmpty()) {
            List<BakeCollector.Placement> list2 = new ArrayList<>();
            List<int[]> list3 = new ArrayList<>();
            this.pack(list, list2, list3);
            String s = "bake_" + (l == 0 ? "solid" : "alpha");

            for (int i1 = 0; i1 < list3.size(); i1++) {
               int[] aint = list3.get(i1);
               int j1 = i1;
               int k1 = aint[0];
               int l1 = aint[1];
               List<BakeCollector.Placement> list1 = list2.stream().filter(px -> px.sheet() == j1).toList();
               TexKey texkey = TexKey.generated(s + "_" + i1, client -> this.render(list1, k1, l1, client, warnings), true);

               for (BakeCollector.Placement bakecollector$placement : list1) {
                  this.emitQuad(mesh, texkey, bakecollector$placement, k1, l1);
                  i++;
               }

               k += (long)k1 * l1;
               j++;
            }
         }
      }

      return new BakeCollector.Stats(this.facesConsumed, i, j, k);
   }

   public void close() {
      this.planes.clear();

      for (NativeImage nativeimage : this.sources.values()) {
         if (nativeimage != null) {
            try {
               nativeimage.close();
            } catch (Exception exception) {
            }
         }
      }

      this.sources.clear();
   }

   private void greedy(BakeCollector.PlaneKey plane, Map<Long, BakeCollector.Cell> cells, List<BakeCollector.Rect> out) {
      int i = Math.max(1, (this.maxAtlas - 4) / this.uvSize);
      int j = Integer.MAX_VALUE;
      int k = Integer.MAX_VALUE;
      int l = Integer.MIN_VALUE;
      int i1 = Integer.MIN_VALUE;

      for (long j1 : cells.keySet()) {
         int k1 = (int)(j1 >> 32);
         int l1 = (int)j1;
         j = Math.min(j, k1);
         l = Math.max(l, k1);
         k = Math.min(k, l1);
         i1 = Math.max(i1, l1);
      }

      int i3 = l - j + 1;
      int j3 = i1 - k + 1;
      boolean[] aboolean = new boolean[i3 * j3];

      for (int k3 = 0; k3 < j3; k3++) {
         for (int l3 = 0; l3 < i3; l3++) {
            if (!aboolean[k3 * i3 + l3] && cells.containsKey(pack(j + l3, k + k3))) {
               int i2 = 1;

               while (i2 < i && l3 + i2 < i3 && !aboolean[k3 * i3 + l3 + i2] && cells.containsKey(pack(j + l3 + i2, k + k3))) {
                  i2++;
               }

               int j2;
               label73:
               for (j2 = 1; j2 < i && k3 + j2 < j3; j2++) {
                  for (int k2 = 0; k2 < i2; k2++) {
                     if (aboolean[(k3 + j2) * i3 + l3 + k2] || !cells.containsKey(pack(j + l3 + k2, k + k3 + j2))) {
                        break label73;
                     }
                  }
               }

               for (int i4 = 0; i4 < j2; i4++) {
                  for (int l2 = 0; l2 < i2; l2++) {
                     aboolean[(k3 + i4) * i3 + l3 + l2] = true;
                  }
               }

               out.add(new BakeCollector.Rect(plane, j + l3, k + k3, i2, j2));
            }
         }
      }
   }

   private void pack(List<BakeCollector.Rect> rects, List<BakeCollector.Placement> out, List<int[]> sheetSizes) {
      List<BakeCollector.Rect> list = new ArrayList<>(rects);
      list.sort(Comparator.<BakeCollector.Rect>comparingInt(rx -> rx.h()).reversed());
      long i = 0L;
      int j = 64;

      for (BakeCollector.Rect bakecollector$rect : list) {
         i += (long)(bakecollector$rect.w() * this.uvSize + 4) * (bakecollector$rect.h() * this.uvSize + 4);
         j = Math.max(j, bakecollector$rect.w() * this.uvSize + 4);
      }

      int i2 = Math.min(this.maxAtlas, Math.max(ceilPow2(j), ceilPow2((int)Math.ceil(Math.sqrt(i)))));
      int j2 = 0;
      int k = 0;
      int l = 0;
      int i1 = 0;
      int j1 = 0;

      for (BakeCollector.Rect bakecollector$rect1 : list) {
         int k1 = bakecollector$rect1.w() * this.uvSize + 4;
         int l1 = bakecollector$rect1.h() * this.uvSize + 4;
         if (k + k1 > i2) {
            k = 0;
            l += i1;
            i1 = 0;
         }

         if (l + l1 > this.maxAtlas) {
            sheetSizes.add(new int[]{i2, ceilPow2(j1)});
            j2++;
            k = 0;
            l = 0;
            i1 = 0;
            j1 = 0;
         }

         out.add(new BakeCollector.Placement(bakecollector$rect1, j2, k + 2, l + 2));
         k += k1;
         i1 = Math.max(i1, l1);
         j1 = Math.max(j1, l + l1);
      }

      sheetSizes.add(new int[]{i2, ceilPow2(j1)});
   }

   private static int ceilPow2(int v) {
      int i = 1;

      while (i < v) {
         i <<= 1;
      }

      return i;
   }

   private NativeImage render(List<BakeCollector.Placement> placements, int width, int height, MinecraftClient mc, List<String> warnings) {
      NativeImage nativeimage = new NativeImage(width, height, false);

      for (int i = 0; i < height; i++) {
         for (int j = 0; j < width; j++) {
            nativeimage.setColorArgb(j, i, 0);
         }
      }

      for (BakeCollector.Placement bakecollector$placement : placements) {
         Map<Long, BakeCollector.Cell> map = this.planes.get(bakecollector$placement.rect().plane());
         if (map != null) {
            BakeCollector.Rect bakecollector$rect = bakecollector$placement.rect();

            for (int k = 0; k < bakecollector$rect.h(); k++) {
               for (int l = 0; l < bakecollector$rect.w(); l++) {
                  BakeCollector.Cell bakecollector$cell = map.get(pack(bakecollector$rect.u0() + l, bakecollector$rect.v0() + k));
                  if (bakecollector$cell != null) {
                     NativeImage nativeimage1 = this.source(bakecollector$cell.tex(), mc, warnings);
                     if (nativeimage1 != null) {
                        this.blit(
                           nativeimage,
                           nativeimage1,
                           bakecollector$cell,
                           bakecollector$placement.x() + l * this.uvSize,
                           bakecollector$placement.y() + k * this.uvSize
                        );
                     }
                  }
               }
            }

            padBorder(
               nativeimage,
               bakecollector$placement.x(),
               bakecollector$placement.y(),
               bakecollector$rect.w() * this.uvSize,
               bakecollector$rect.h() * this.uvSize
            );
         }
      }

      return nativeimage;
   }

   private void blit(NativeImage out, NativeImage src, BakeCollector.Cell cell, int dx, int dy) {
      int i = src.getWidth();
      int j = src.getHeight();
      float[][] afloat = cell.cornerUv();

      for (int k = 0; k < this.uvSize; k++) {
         float f = (k + 0.5F) / this.uvSize;

         for (int l = 0; l < this.uvSize; l++) {
            float f1 = (l + 0.5F) / this.uvSize;
            float f2 = bilerp(afloat, 0, f1, f);
            float f3 = bilerp(afloat, 1, f1, f);
            int i1 = clamp((int)(f2 * i), 0, i - 1);
            int j1 = clamp((int)(f3 * j), 0, j - 1);
            out.setColorArgb(dx + l, dy + k, src.getColorArgb(i1, j1));
         }
      }
   }

   private static float bilerp(float[][] corners, int comp, float fx, float fy) {
      float f = corners[0][comp];
      float f1 = corners[1][comp];
      float f2 = corners[2][comp];
      float f3 = corners[3][comp];
      return f * (1.0F - fx) * (1.0F - fy) + f1 * fx * (1.0F - fy) + f2 * (1.0F - fx) * fy + f3 * fx * fy;
   }

   private static void padBorder(NativeImage img, int x, int y, int w, int h) {
      for (int i = -2; i < h + 2; i++) {
         int j = clamp(i, 0, h - 1);

         for (int k = -2; k < w + 2; k++) {
            if (k < 0 || k >= w || i < 0 || i >= h) {
               int l = clamp(k, 0, w - 1);
               int i1 = x + k;
               int j1 = y + i;
               if (i1 >= 0 && j1 >= 0 && i1 < img.getWidth() && j1 < img.getHeight()) {
                  img.setColorArgb(i1, j1, img.getColorArgb(x + l, y + j));
               }
            }
         }
      }
   }

   private static int clamp(int v, int lo, int hi) {
      return v < lo ? lo : Math.min(v, hi);
   }

   private NativeImage source(TexKey tex, MinecraftClient mc, List<String> warnings) {
      if (this.sources.containsKey(tex.id())) {
         return this.sources.get(tex.id());
      } else {
         NativeImage nativeimage = null;

         try {
            nativeimage = tex.load(mc);
         } catch (Exception exception) {
            warnings.add("bake source failed: " + tex.id() + " (" + exception.getMessage() + ")");
         }

         this.sources.put(tex.id(), nativeimage);
         return nativeimage;
      }
   }

   private void emitQuad(MeshBuilder mesh, TexKey tex, BakeCollector.Placement p, int sheetW, int sheetH) {
      BakeCollector.Rect bakecollector$rect = p.rect();
      BakeCollector.PlaneKey bakecollector$planekey = bakecollector$rect.plane();
      int i = bakecollector$planekey.axis();
      int j = i == 0 ? 1 : 0;
      int k = i == 2 ? 1 : 2;
      float f = bakecollector$rect.u0();
      float f1 = bakecollector$rect.u0() + bakecollector$rect.w();
      float f2 = bakecollector$rect.v0();
      float f3 = bakecollector$rect.v0() + bakecollector$rect.h();
      float[][] afloat = new float[4][3];
      float[][] afloat1 = new float[][]{{f, f2}, {f1, f2}, {f1, f3}, {f, f3}};

      for (int l = 0; l < 4; l++) {
         afloat[l][i] = bakecollector$planekey.slice();
         afloat[l][j] = afloat1[l][0];
         afloat[l][k] = afloat1[l][1];
      }

      float f7 = (float)p.x() / sheetW;
      float f4 = (float)p.y() / sheetH;
      float f5 = (float)(p.x() + bakecollector$rect.w() * this.uvSize) / sheetW;
      float f6 = (float)(p.y() + bakecollector$rect.h() * this.uvSize) / sheetH;
      float[][] afloat2 = new float[][]{{f7, f4}, {f5, f4}, {f5, f6}, {f7, f6}};
      float[] afloat3 = new float[3];
      afloat3[i] = bakecollector$planekey.positive() ? 1.0F : -1.0F;
      boolean flag = i != 1;
      if (flag != bakecollector$planekey.positive()) {
         swap(afloat, 1, 3);
         swap(afloat2, 1, 3);
      }

      mesh.addQuad(tex, afloat, afloat2, afloat3);
   }

   private static void swap(float[][] a, int i, int j) {
      float[] afloat = a[i];
      a[i] = a[j];
      a[j] = afloat;
   }

   private record Cell(TexKey tex, float[][] cornerUv) {
   }

   private record Placement(BakeCollector.Rect rect, int sheet, int x, int y) {
   }

   private record PlaneKey(int axis, int slice, boolean positive, int layer) {
   }

   private record Rect(BakeCollector.PlaneKey plane, int u0, int v0, int w, int h) {
   }

   public record Stats(int facesConsumed, int quadsEmitted, int sheets, long bakedPixels) {
   }
}
