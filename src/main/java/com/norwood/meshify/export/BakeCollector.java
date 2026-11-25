package com.norwood.meshify.export;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.phys.Vec3;

// Merges coplanar full-block faces into large quads, each textured from a sheet the faces are painted onto.
public final class BakeCollector {
   public static final int LAYER_SOLID = 0;
   public static final int LAYER_ALPHA = 1;
   // Or'ed onto a layer for light-emitting blocks; faces only merge within the same layer.
   public static final int LAYER_EMISSIVE = 2;
   private static final float EPSILON = 1.0E-4F;
   // Edge pixels repeated around every baked rectangle, so sampling never bleeds in a neighbour.
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

   // Takes a face that covers one whole side of its block; false leaves it to the plain mesh.
   public boolean tryAdd(Vec3 blockOrigin, Direction face, TextureKey texture, float[][] positions, float[][] uv, int layer) {
      int axis = face.getAxis().ordinal();
      boolean positive = face.getAxisDirection() == AxisDirection.POSITIVE;
      double[] origin = new double[]{blockOrigin.x, blockOrigin.y, blockOrigin.z};
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
         double offsetU = positions[k][uAxis] - origin[uAxis];
         double offsetV = positions[k][vAxis] - origin[vAxis];
         int cornerU = (int)Math.round(offsetU);
         int cornerV = (int)Math.round(offsetV);
         if (cornerU < 0 || cornerU > 1 || cornerV < 0 || cornerV > 1 || Math.abs(offsetU - cornerU) > EPSILON || Math.abs(offsetV - cornerV) > EPSILON) {
            return false;
         }

         int slot = cornerV * 2 + cornerU;
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
      int sheetCount = 0;
      long bakedPixels = 0L;

      // One set of sheets per layer: solid or alpha, each with or without emission.
      for (int layer = 0; layer <= (LAYER_ALPHA | LAYER_EMISSIVE); layer++) {
         List<BakeCollector.Rect> rects = new ArrayList<>();

         for (Entry<BakeCollector.PlaneKey, Map<Long, BakeCollector.Cell>> plane : this.planes.entrySet()) {
            if (plane.getKey().layer() == layer) {
               rects.addAll(this.mergeCells(plane.getKey(), plane.getValue()));
            }
         }

         if (rects.isEmpty()) {
            continue;
         }

         List<BakeCollector.Sheet> sheets = this.packOntoSheets(rects);
         boolean emissive = (layer & LAYER_EMISSIVE) != 0;
         String prefix = "bake_" + ((layer & LAYER_ALPHA) == 0 ? "solid" : "alpha") + (emissive ? "_emissive" : "");

         for (int index = 0; index < sheets.size(); index++) {
            BakeCollector.Sheet sheet = sheets.get(index);
            TextureKey texture = TextureKey.generatedPreScaled(prefix + "_" + index, client -> this.renderSheet(sheet, client, warnings));
            if (emissive) {
               mesh.markEmissive(texture.id());
            }

            this.emitQuads(mesh, texture, sheet);
            quadsEmitted += sheet.placements().size();
            bakedPixels += (long)sheet.width() * sheet.height();
         }

         sheetCount += sheets.size();
      }

      return new BakeCollector.Stats(this.facesConsumed, quadsEmitted, sheetCount, bakedPixels);
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

   // Covers one plane's cells with as few rectangles as it can, none wider or taller than a sheet holds.
   private List<BakeCollector.Rect> mergeCells(BakeCollector.PlaneKey plane, Map<Long, BakeCollector.Cell> cells) {
      List<BakeCollector.Rect> rects = new ArrayList<>();
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

            rects.add(new BakeCollector.Rect(plane, minU + u, minV + v, width, height));
         }
      }

      return rects;
   }

   // Shelf-packs the rectangles, tallest first, onto as many sheets as they need.
   private List<BakeCollector.Sheet> packOntoSheets(List<BakeCollector.Rect> rects) {
      List<BakeCollector.Rect> sorted = new ArrayList<>(rects);
      sorted.sort(Comparator.comparingInt(BakeCollector.Rect::height).reversed());
      long area = 0L;
      int widest = MIN_SHEET_WIDTH;

      for (BakeCollector.Rect rect : sorted) {
         area += (long)this.padded(rect.width()) * this.padded(rect.height());
         widest = Math.max(widest, this.padded(rect.width()));
      }

      int sheetWidth = Math.min(this.maxSheetSize, Math.max(TextureStore.ceilPowerOfTwo(widest), TextureStore.ceilPowerOfTwo((int)Math.ceil(Math.sqrt(area)))));
      List<BakeCollector.Sheet> sheets = new ArrayList<>();
      List<BakeCollector.Placement> onSheet = new ArrayList<>();
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
            sheets.add(new BakeCollector.Sheet(sheetWidth, TextureStore.ceilPowerOfTwo(usedHeight), onSheet));
            onSheet = new ArrayList<>();
            x = 0;
            y = 0;
            shelfHeight = 0;
            usedHeight = 0;
         }

         onSheet.add(new BakeCollector.Placement(rect, x + BORDER, y + BORDER));
         x += paddedWidth;
         shelfHeight = Math.max(shelfHeight, paddedHeight);
         usedHeight = Math.max(usedHeight, y + paddedHeight);
      }

      sheets.add(new BakeCollector.Sheet(sheetWidth, TextureStore.ceilPowerOfTwo(usedHeight), onSheet));
      return sheets;
   }

   // Pixels a run of cells takes on a sheet, border included.
   private int padded(int cells) {
      return cells * this.uvSize + 2 * BORDER;
   }

   private NativeImage renderSheet(BakeCollector.Sheet sheet, Minecraft client, List<String> warnings) {
      NativeImage image = new NativeImage(sheet.width(), sheet.height(), false);
      image.fillRect(0, 0, sheet.width(), sheet.height(), 0);

      for (BakeCollector.Placement placement : sheet.placements()) {
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
                  this.blit(image, source, cell, placement.x() + du * this.uvSize, placement.y() + dv * this.uvSize);
               }
            }
         }

         padBorder(image, placement.x(), placement.y(), rect.width() * this.uvSize, rect.height() * this.uvSize);
      }

      return image;
   }

   // Paints one cell's texture into a uvSize square of image, sampled through the face's corner UVs.
   private void blit(NativeImage image, NativeImage source, BakeCollector.Cell cell, int sheetX, int sheetY) {
      int sourceWidth = source.getWidth();
      int sourceHeight = source.getHeight();
      float[][] cornerUv = cell.cornerUv();

      for (int pixelY = 0; pixelY < this.uvSize; pixelY++) {
         float fractionY = (pixelY + 0.5F) / this.uvSize;

         for (int pixelX = 0; pixelX < this.uvSize; pixelX++) {
            float fractionX = (pixelX + 0.5F) / this.uvSize;
            int sourceX = Math.clamp((int)(bilerp(cornerUv, 0, fractionX, fractionY) * sourceWidth), 0, sourceWidth - 1);
            int sourceY = Math.clamp((int)(bilerp(cornerUv, 1, fractionX, fractionY) * sourceHeight), 0, sourceHeight - 1);
            image.setPixel(sheetX + pixelX, sheetY + pixelY, source.getPixel(sourceX, sourceY));
         }
      }
   }

   private static float bilerp(float[][] corners, int component, float fractionX, float fractionY) {
      float c00 = corners[0][component];
      float c10 = corners[1][component];
      float c01 = corners[2][component];
      float c11 = corners[3][component];
      return c00 * (1.0F - fractionX) * (1.0F - fractionY) + c10 * fractionX * (1.0F - fractionY) + c01 * (1.0F - fractionX) * fractionY + c11 * fractionX * fractionY;
   }

   // Repeats a rectangle's edge pixels outward across its BORDER.
   private static void padBorder(NativeImage image, int x, int y, int width, int height) {
      for (int dy = -BORDER; dy < height + BORDER; dy++) {
         int srcY = Math.clamp(dy, 0, height - 1);

         for (int dx = -BORDER; dx < width + BORDER; dx++) {
            if (dx >= 0 && dx < width && dy >= 0 && dy < height) {
               continue;
            }

            int outX = x + dx;
            int outY = y + dy;
            if (outX >= 0 && outY >= 0 && outX < image.getWidth() && outY < image.getHeight()) {
               image.setPixel(outX, outY, image.getPixel(x + Math.clamp(dx, 0, width - 1), y + srcY));
            }
         }
      }
   }

   // Loads each source texture once; a failed one is remembered as null, so it only warns once.
   private NativeImage loadSource(TextureKey texture, Minecraft client, List<String> warnings) {
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

   // One quad per rectangle on the sheet, textured from where the rectangle was packed.
   private void emitQuads(MeshBuilder mesh, TextureKey texture, BakeCollector.Sheet sheet) {
      for (BakeCollector.Placement placement : sheet.placements()) {
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

         float s0 = (float)placement.x() / sheet.width();
         float t0 = (float)placement.y() / sheet.height();
         float s1 = (float)(placement.x() + rect.width() * this.uvSize) / sheet.width();
         float t1 = (float)(placement.y() + rect.height() * this.uvSize) / sheet.height();
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
   }

   private static void swap(float[][] array, int i, int j) {
      float[] first = array[i];
      array[i] = array[j];
      array[j] = first;
   }

   private record Cell(TextureKey texture, float[][] cornerUv) {
   }

   private record Placement(BakeCollector.Rect rect, int x, int y) {
   }

   // One baked texture sheet and the rectangles packed onto it.
   private record Sheet(int width, int height, List<BakeCollector.Placement> placements) {
   }

   private record PlaneKey(int axis, int slice, boolean positive, int layer) {
   }

   private record Rect(BakeCollector.PlaneKey plane, int u0, int v0, int width, int height) {
   }

   public record Stats(int facesConsumed, int quadsEmitted, int sheets, long bakedPixels) {
      // How many fewer quads the merge left, as a percentage of the faces it took.
      public double reductionPercent() {
         return this.facesConsumed > 0 ? 100.0 * (1.0 - (double)this.quadsEmitted / this.facesConsumed) : 0.0;
      }

      public double megapixels() {
         return this.bakedPixels / 1_000_000.0;
      }
   }
}
