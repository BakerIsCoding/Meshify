package com.norwood.meshify.export;

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

/** Merges coplanar full-block faces into large quads, each textured from a sheet the faces are painted onto. */
public final class BakeCollector {
   public static final int LAYER_SOLID = 0;
   public static final int LAYER_ALPHA = 1;
   /** Or'ed onto a layer for light-emitting blocks; faces only merge within the same layer. */
   public static final int LAYER_EMISSIVE = 2;
   private static final float EPSILON = 1.0E-4F;
   /** Edge pixels repeated around every baked rectangle, so sampling never bleeds in a neighbour. */
   private static final int BORDER = 2;
   private static final int MIN_SHEET_WIDTH = 64;
   private final int uvSize;
   private final int maxSheetSize;
   private final Map<BakeCollector.PlaneKey, Map<Long, BakeCollector.Cell>> planes = new HashMap<>();
   private final Map<String, NativeImage> sources = new HashMap<>();
   private int facesConsumed;

   public BakeCollector(int uvSize, int maxSheetSize) {
      this.uvSize = uvSize;
      // Sheets are powers of two, so the cap rounds down to one.
      this.maxSheetSize = Integer.highestOneBit(maxSheetSize);
   }

   /** Takes a face lying on its block's {@code face} side and covering it whole; false leaves it to the plain mesh. */
   public boolean tryAdd(double blockX, double blockY, double blockZ, Direction face, TextureKey texture, float[][] positions, float[][] uv, int layer) {
      int axis = face.getAxis().ordinal();
      boolean positive = face.getDirection() == AxisDirection.POSITIVE;
      double[] origin = new double[]{blockX, blockY, blockZ};
      float plane = (float)(origin[axis] + (positive ? 1 : 0));

      for (float[] corner : positions) {
         if (Math.abs(corner[axis] - plane) > EPSILON) {
            return false;
         }
      }

      int uAxis = axis == 0 ? 1 : 0;
      int vAxis = axis == 2 ? 1 : 2;
      // The UVs re-ordered by corner position, slot v * 2 + u; four corners filling four distinct slots cover the square.
      float[][] cornerUv = new float[4][];

      for (int k = 0; k < 4; k++) {
         double du = positions[k][uAxis] - origin[uAxis];
         double dv = positions[k][vAxis] - origin[vAxis];
         int cu = (int)Math.round(du);
         int cv = (int)Math.round(dv);
         if (cu < 0 || cu > 1 || cv < 0 || cv > 1 || Math.abs(du - cu) > EPSILON || Math.abs(dv - cv) > EPSILON) {
            return false;
         }

         int slot = cv * 2 + cu;
         if (cornerUv[slot] != null) {
            return false;
         }

         cornerUv[slot] = new float[]{uv[k][0], uv[k][1]};
      }

      BakeCollector.PlaneKey key = new BakeCollector.PlaneKey(axis, Math.round(plane), positive, layer);
      long cell = cellKey((int)Math.round(origin[uAxis]), (int)Math.round(origin[vAxis]));
      this.planes.computeIfAbsent(key, k -> new HashMap<>()).put(cell, new BakeCollector.Cell(texture, cornerUv));
      this.facesConsumed++;
      return true;
   }

   public boolean isEmpty() {
      return this.planes.isEmpty();
   }

   private static long cellKey(int u, int v) {
      return (long)u << 32 | v & 0xFFFFFFFFL;
   }

   public BakeCollector.Stats flush(MeshBuilder mesh, List<String> warnings) {
      int quadsEmitted = 0;
      int sheets = 0;
      long bakedPixels = 0L;

      // One set of sheets per layer: solid or alpha, each with or without emission.
      for (int layer = 0; layer <= (LAYER_ALPHA | LAYER_EMISSIVE); layer++) {
         List<BakeCollector.Rect> rects = new ArrayList<>();

         for (Entry<BakeCollector.PlaneKey, Map<Long, BakeCollector.Cell>> plane : this.planes.entrySet()) {
            if (plane.getKey().layer() == layer) {
               this.mergeCells(plane.getKey(), plane.getValue(), rects);
            }
         }

         if (rects.isEmpty()) {
            continue;
         }

         List<BakeCollector.Placement> placements = new ArrayList<>();
         List<int[]> sheetSizes = new ArrayList<>();
         this.packOntoSheets(rects, placements, sheetSizes);
         boolean emissive = (layer & LAYER_EMISSIVE) != 0;
         String prefix = "bake_" + ((layer & LAYER_ALPHA) == 0 ? "solid" : "alpha") + (emissive ? "_emissive" : "");

         for (int sheet = 0; sheet < sheetSizes.size(); sheet++) {
            int sheetIndex = sheet;
            int width = sheetSizes.get(sheet)[0];
            int height = sheetSizes.get(sheet)[1];
            List<BakeCollector.Placement> onSheet = placements.stream().filter(placement -> placement.sheet() == sheetIndex).toList();
            TextureKey texture = TextureKey.generated(prefix + "_" + sheet, client -> this.renderSheet(onSheet, width, height, client, warnings), true);
            if (emissive) {
               mesh.markEmissive(texture.id());
            }

            for (BakeCollector.Placement placement : onSheet) {
               this.emitQuad(mesh, texture, placement, width, height);
               quadsEmitted++;
            }

            bakedPixels += (long)width * height;
            sheets++;
         }
      }

      return new BakeCollector.Stats(this.facesConsumed, quadsEmitted, sheets, bakedPixels);
   }

   public void close() {
      this.planes.clear();

      for (NativeImage image : this.sources.values()) {
         if (image != null) {
            image.close();
         }
      }

      this.sources.clear();
   }

   /** Covers one plane's cells with as few rectangles as it can, none wider or taller than a sheet holds. */
   private void mergeCells(BakeCollector.PlaneKey plane, Map<Long, BakeCollector.Cell> cells, List<BakeCollector.Rect> out) {
      int maxCells = Math.max(1, (this.maxSheetSize - 2 * BORDER) / this.uvSize);
      int minU = Integer.MAX_VALUE;
      int minV = Integer.MAX_VALUE;
      int maxU = Integer.MIN_VALUE;
      int maxV = Integer.MIN_VALUE;

      for (long key : cells.keySet()) {
         int u = (int)(key >> 32);
         int v = (int)key;
         minU = Math.min(minU, u);
         maxU = Math.max(maxU, u);
         minV = Math.min(minV, v);
         maxV = Math.max(maxV, v);
      }

      int spanU = maxU - minU + 1;
      int spanV = maxV - minV + 1;
      boolean[] covered = new boolean[spanU * spanV];

      for (int v = 0; v < spanV; v++) {
         for (int u = 0; u < spanU; u++) {
            if (covered[v * spanU + u] || !cells.containsKey(cellKey(minU + u, minV + v))) {
               continue;
            }

            int width = 1;
            while (width < maxCells && u + width < spanU && !covered[v * spanU + u + width] && cells.containsKey(cellKey(minU + u + width, minV + v))) {
               width++;
            }

            int height;
            grow:
            for (height = 1; height < maxCells && v + height < spanV; height++) {
               for (int du = 0; du < width; du++) {
                  if (covered[(v + height) * spanU + u + du] || !cells.containsKey(cellKey(minU + u + du, minV + v + height))) {
                     break grow;
                  }
               }
            }

            for (int dv = 0; dv < height; dv++) {
               for (int du = 0; du < width; du++) {
                  covered[(v + dv) * spanU + u + du] = true;
               }
            }

            out.add(new BakeCollector.Rect(plane, minU + u, minV + v, width, height));
         }
      }
   }

   /** Shelf-packs the rectangles, tallest first, onto as many sheets as they need. */
   private void packOntoSheets(List<BakeCollector.Rect> rects, List<BakeCollector.Placement> out, List<int[]> sheetSizes) {
      List<BakeCollector.Rect> sorted = new ArrayList<>(rects);
      sorted.sort(Comparator.comparingInt(BakeCollector.Rect::height).reversed());
      long area = 0L;
      int widest = MIN_SHEET_WIDTH;

      for (BakeCollector.Rect rect : sorted) {
         area += (long)this.padded(rect.width()) * this.padded(rect.height());
         widest = Math.max(widest, this.padded(rect.width()));
      }

      int sheetWidth = Math.min(this.maxSheetSize, Math.max(TextureStore.ceilPowerOfTwo(widest), TextureStore.ceilPowerOfTwo((int)Math.ceil(Math.sqrt(area)))));
      int sheet = 0;
      int x = 0;
      int y = 0;
      int shelfHeight = 0;
      int usedHeight = 0;

      for (BakeCollector.Rect rect : sorted) {
         int paddedWidth = this.padded(rect.width());
         int paddedHeight = this.padded(rect.height());
         if (x + paddedWidth > sheetWidth) {
            x = 0;
            y += shelfHeight;
            shelfHeight = 0;
         }

         if (y + paddedHeight > this.maxSheetSize) {
            sheetSizes.add(new int[]{sheetWidth, TextureStore.ceilPowerOfTwo(usedHeight)});
            sheet++;
            x = 0;
            y = 0;
            shelfHeight = 0;
            usedHeight = 0;
         }

         out.add(new BakeCollector.Placement(rect, sheet, x + BORDER, y + BORDER));
         x += paddedWidth;
         shelfHeight = Math.max(shelfHeight, paddedHeight);
         usedHeight = Math.max(usedHeight, y + paddedHeight);
      }

      sheetSizes.add(new int[]{sheetWidth, TextureStore.ceilPowerOfTwo(usedHeight)});
   }

   /** Pixels a run of {@code cells} takes on a sheet, border included. */
   private int padded(int cells) {
      return cells * this.uvSize + 2 * BORDER;
   }

   private NativeImage renderSheet(List<BakeCollector.Placement> placements, int width, int height, MinecraftClient client, List<String> warnings) {
      NativeImage sheet = new NativeImage(width, height, false);
      sheet.fillRect(0, 0, width, height, 0);

      for (BakeCollector.Placement placement : placements) {
         BakeCollector.Rect rect = placement.rect();
         Map<Long, BakeCollector.Cell> cells = this.planes.get(rect.plane());
         if (cells == null) {
            continue;
         }

         for (int dv = 0; dv < rect.height(); dv++) {
            for (int du = 0; du < rect.width(); du++) {
               BakeCollector.Cell cell = cells.get(cellKey(rect.u0() + du, rect.v0() + dv));
               NativeImage source = cell == null ? null : this.loadSource(cell.texture(), client, warnings);
               if (source != null) {
                  this.blit(sheet, source, cell, placement.x() + du * this.uvSize, placement.y() + dv * this.uvSize);
               }
            }
         }

         padBorder(sheet, placement.x(), placement.y(), rect.width() * this.uvSize, rect.height() * this.uvSize);
      }

      return sheet;
   }

   /** Paints one cell's texture into a {@code uvSize} square, sampled through the face's corner UVs. */
   private void blit(NativeImage sheet, NativeImage source, BakeCollector.Cell cell, int sheetX, int sheetY) {
      int sourceWidth = source.getWidth();
      int sourceHeight = source.getHeight();
      float[][] cornerUv = cell.cornerUv();

      for (int py = 0; py < this.uvSize; py++) {
         float fy = (py + 0.5F) / this.uvSize;

         for (int px = 0; px < this.uvSize; px++) {
            float fx = (px + 0.5F) / this.uvSize;
            int sx = Math.clamp((int)(bilerp(cornerUv, 0, fx, fy) * sourceWidth), 0, sourceWidth - 1);
            int sy = Math.clamp((int)(bilerp(cornerUv, 1, fx, fy) * sourceHeight), 0, sourceHeight - 1);
            sheet.setColorArgb(sheetX + px, sheetY + py, source.getColorArgb(sx, sy));
         }
      }
   }

   private static float bilerp(float[][] corners, int component, float fx, float fy) {
      float c00 = corners[0][component];
      float c10 = corners[1][component];
      float c01 = corners[2][component];
      float c11 = corners[3][component];
      return c00 * (1.0F - fx) * (1.0F - fy) + c10 * fx * (1.0F - fy) + c01 * (1.0F - fx) * fy + c11 * fx * fy;
   }

   /** Repeats a rectangle's edge pixels outward across its {@link #BORDER}. */
   private static void padBorder(NativeImage image, int x, int y, int w, int h) {
      for (int dy = -BORDER; dy < h + BORDER; dy++) {
         int srcY = Math.clamp(dy, 0, h - 1);

         for (int dx = -BORDER; dx < w + BORDER; dx++) {
            if (dx >= 0 && dx < w && dy >= 0 && dy < h) {
               continue;
            }

            int outX = x + dx;
            int outY = y + dy;
            if (outX >= 0 && outY >= 0 && outX < image.getWidth() && outY < image.getHeight()) {
               image.setColorArgb(outX, outY, image.getColorArgb(x + Math.clamp(dx, 0, w - 1), y + srcY));
            }
         }
      }
   }

   /** Loads each source texture once; a failed one is remembered as null, so it only warns once. */
   private NativeImage loadSource(TextureKey texture, MinecraftClient client, List<String> warnings) {
      if (this.sources.containsKey(texture.id())) {
         return this.sources.get(texture.id());
      }

      NativeImage image = null;
      try {
         image = texture.load(client);
      } catch (Exception exception) {
         warnings.add("bake source failed: " + texture.id() + " (" + exception.getMessage() + ")");
      }

      this.sources.put(texture.id(), image);
      return image;
   }

   private void emitQuad(MeshBuilder mesh, TextureKey texture, BakeCollector.Placement placement, int sheetWidth, int sheetHeight) {
      BakeCollector.Rect rect = placement.rect();
      BakeCollector.PlaneKey plane = rect.plane();
      int axis = plane.axis();
      int uAxis = axis == 0 ? 1 : 0;
      int vAxis = axis == 2 ? 1 : 2;
      float u0 = rect.u0();
      float u1 = rect.u0() + rect.width();
      float v0 = rect.v0();
      float v1 = rect.v0() + rect.height();
      float[][] planeCorners = new float[][]{{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
      float[][] positions = new float[4][3];

      for (int k = 0; k < 4; k++) {
         positions[k][axis] = plane.slice();
         positions[k][uAxis] = planeCorners[k][0];
         positions[k][vAxis] = planeCorners[k][1];
      }

      float s0 = (float)placement.x() / sheetWidth;
      float t0 = (float)placement.y() / sheetHeight;
      float s1 = (float)(placement.x() + rect.width() * this.uvSize) / sheetWidth;
      float t1 = (float)(placement.y() + rect.height() * this.uvSize) / sheetHeight;
      float[][] uv = new float[][]{{s0, t0}, {s1, t0}, {s1, t1}, {s0, t1}};
      float[] normal = new float[3];
      normal[axis] = plane.positive() ? 1.0F : -1.0F;
      // Those corners wind toward +X and +Z but toward -Y (u x v), so the order flips where that disagrees with the face.
      boolean windsPositive = axis != 1;
      if (windsPositive != plane.positive()) {
         swap(positions, 1, 3);
         swap(uv, 1, 3);
      }

      mesh.addQuad(texture, positions, uv, normal);
   }

   private static void swap(float[][] array, int i, int j) {
      float[] first = array[i];
      array[i] = array[j];
      array[j] = first;
   }

   private record Cell(TextureKey texture, float[][] cornerUv) {
   }

   private record Placement(BakeCollector.Rect rect, int sheet, int x, int y) {
   }

   private record PlaneKey(int axis, int slice, boolean positive, int layer) {
   }

   private record Rect(BakeCollector.PlaneKey plane, int u0, int v0, int width, int height) {
   }

   public record Stats(int facesConsumed, int quadsEmitted, int sheets, long bakedPixels) {
      /** How many fewer quads the merge left, as a percentage of the faces it took. */
      public double reductionPercent() {
         return this.facesConsumed > 0 ? 100.0 * (1.0 - (double)this.quadsEmitted / this.facesConsumed) : 0.0;
      }

      public double megapixels() {
         return this.bakedPixels / 1000000.0;
      }
   }
}
