package com.norwood.meshify.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

/** Rasterises sign text with Minecraft's own bitmap font, which covers Latin, Greek and Cyrillic, one texel per font unit. */
public final class FontRaster {
   public static final int LINE_HEIGHT = 10;
   /** Where the default font's baseline sits inside its line; each provider hangs off it. */
   private static final int BASELINE = 7;
   /** From {@code font/include/space.json}: the space character is not in the bitmap sheets. */
   private static final int SPACE_ADVANCE = 4;
   private static final int DEFAULT_GLYPH_HEIGHT = 8;
   /** A glyph row is one int of mask bits; vanilla cells are 8 or 9 wide. */
   private static final int MAX_CELL_WIDTH = 32;
   private static Map<Integer, FontRaster.Glyph> glyphs;
   private static boolean loadFailed;

   private FontRaster() {
   }

   public static synchronized boolean ensureLoaded(MinecraftClient client, List<String> warnings) {
      if (glyphs != null) {
         return true;
      } else if (loadFailed) {
         return false;
      }

      try {
         glyphs = loadGlyphs(client);
         return true;
      } catch (Exception exception) {
         loadFailed = true;
         warnings.add("default font load failed, sign text skipped (" + exception.getMessage() + ")");
         return false;
      }
   }

   /** Only valid once {@link #ensureLoaded} ran. */
   public static boolean covers(int codePoint) {
      return codePoint == ' ' || glyphs != null && glyphs.containsKey(codePoint);
   }

   private static Map<Integer, FontRaster.Glyph> loadGlyphs(MinecraftClient client) throws IOException {
      Map<Integer, FontRaster.Glyph> loaded = new HashMap<>(1024);

      for (JsonObject provider : bitmapProviders(client)) {
         readSheet(client, provider, loaded);
      }

      // No 'A' means the sheets were not read, and every sign would export blank without a warning.
      if (!loaded.containsKey((int)'A')) {
         throw new IOException("no Latin glyphs in the default font");
      }

      return loaded;
   }

   /** The bitmap providers of the default font stack, highest priority first. */
   private static List<JsonObject> bitmapProviders(MinecraftClient client) throws IOException {
      Identifier fontId = Identifier.ofVanilla("font/include/default.json");
      Resource resource = client.getResourceManager().getResource(fontId).orElseThrow(() -> new IOException("not found: " + fontId));
      List<JsonObject> providers = new ArrayList<>();

      try (InputStream in = resource.getInputStream()) {
         JsonArray all = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("providers");
         if (all != null) {
            for (JsonElement element : all) {
               JsonObject provider = element.getAsJsonObject();
               if (provider.has("type") && provider.get("type").getAsString().equals("bitmap") && provider.has("file")) {
                  providers.add(provider);
               }
            }
         }
      }

      if (providers.isEmpty()) {
         throw new IOException("no bitmap providers in " + fontId);
      }

      return providers;
   }

   /** Cuts one sheet into glyphs, keeping only a one-bit-per-pixel mask. */
   private static void readSheet(MinecraftClient client, JsonObject provider, Map<Integer, FontRaster.Glyph> loaded) throws IOException {
      Identifier file = Identifier.of(provider.get("file").getAsString());
      Optional<Resource> sheetResource = client.getResourceManager().getResource(Identifier.of(file.getNamespace(), "textures/" + file.getPath()));
      if (sheetResource.isEmpty()) {
         return;
      }

      JsonArray chars = provider.getAsJsonArray("chars");
      int rows = chars.size();
      int[][] codePoints = new int[rows][];

      for (int row = 0; row < rows; row++) {
         codePoints[row] = chars.get(row).getAsString().codePoints().toArray();
      }

      int columns = codePoints[0].length;
      if (columns == 0) {
         return;
      }

      int height = provider.has("height") ? provider.get("height").getAsInt() : DEFAULT_GLYPH_HEIGHT;
      int ascent = provider.get("ascent").getAsInt();

      try (InputStream in = sheetResource.get().getInputStream(); NativeImage sheet = NativeImage.read(in)) {
         int cellWidth = sheet.getWidth() / columns;
         int cellHeight = sheet.getHeight() / rows;
         if (cellWidth < 1 || cellWidth > MAX_CELL_WIDTH || cellHeight < 1) {
            return;
         }

         for (int row = 0; row < rows; row++) {
            for (int column = 0; column < codePoints[row].length && column < columns; column++) {
               int codePoint = codePoints[row][column];
               if (codePoint != 0) {
                  loaded.putIfAbsent(codePoint, cutGlyph(sheet, column * cellWidth, row * cellHeight, cellWidth, cellHeight, height, ascent));
               }
            }
         }
      }
   }

   private static FontRaster.Glyph cutGlyph(NativeImage sheet, int x, int y, int cellWidth, int cellHeight, int height, int ascent) {
      int[] rows = new int[cellHeight];
      int width = 0;

      for (int row = 0; row < cellHeight; row++) {
         int mask = 0;

         for (int column = 0; column < cellWidth; column++) {
            if ((sheet.getColorArgb(x + column, y + row) >>> 24) != 0) {
               mask |= 1 << cellWidth - 1 - column;
               width = Math.max(width, column + 1);
            }
         }

         rows[row] = mask;
      }

      return new FontRaster.Glyph(rows, cellWidth, cellHeight, width, height, ascent);
   }

   /** Draws each line centred on a transparent canvas, the block of lines centred vertically. */
   public static NativeImage render(String[] lines, int rgb, int canvasWidth, int canvasHeight) {
      NativeImage canvas = new NativeImage(canvasWidth, canvasHeight, false);
      canvas.fillRect(0, 0, canvasWidth, canvasHeight, 0);
      int argb = 0xFF000000 | rgb & 0xFFFFFF;
      int top = Math.max(0, (canvasHeight - lines.length * LINE_HEIGHT) / 2);

      for (int line = 0; line < lines.length; line++) {
         String text = lines[line];
         if (text == null || text.isBlank()) {
            continue;
         }

         int[] codePoints = text.codePoints().toArray();
         int width = 0;

         for (int codePoint : codePoints) {
            width += advance(codePoint);
         }

         int penX = Math.max(0, (canvasWidth - width) / 2);
         int baseline = top + line * LINE_HEIGHT + BASELINE;

         for (int codePoint : codePoints) {
            FontRaster.Glyph glyph = glyphs.get(codePoint);
            if (glyph != null) {
               drawGlyph(canvas, glyph, penX, baseline, argb);
            }

            penX += advance(codePoint);
         }
      }

      return canvas;
   }

   private static int advance(int codePoint) {
      FontRaster.Glyph glyph = glyphs.get(codePoint);
      return glyph == null || codePoint == ' ' ? SPACE_ADVANCE : Math.round(glyph.width() * (float)glyph.height() / glyph.cellHeight()) + 1;
   }

   /** Draws a glyph scaled from its cell height to its font height, nearest-neighbour. */
   private static void drawGlyph(NativeImage canvas, FontRaster.Glyph glyph, int penX, int baseline, int argb) {
      int height = glyph.height();
      int top = baseline - glyph.ascent();
      int width = Math.round(glyph.width() * (float)height / glyph.cellHeight());

      for (int dy = 0; dy < height; dy++) {
         int mask = glyph.rows()[dy * glyph.cellHeight() / height];
         int y = top + dy;
         if (mask == 0 || y < 0 || y >= canvas.getHeight()) {
            continue;
         }

         for (int dx = 0; dx < width; dx++) {
            int x = penX + dx;
            if ((mask & 1 << glyph.cellWidth() - 1 - dx * glyph.cellHeight() / height) != 0 && x >= 0 && x < canvas.getWidth()) {
               canvas.setColorArgb(x, y, argb);
            }
         }
      }
   }

   /** One cell of a bitmap sheet: {@code rows} holds one bit per pixel, high bit leftmost. */
   private record Glyph(int[] rows, int cellWidth, int cellHeight, int width, int height, int ascent) {
   }
}
