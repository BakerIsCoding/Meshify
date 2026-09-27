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

/** Rasterises sign text with Minecraft's own bitmap font, which covers Latin, Greek and Cyrillic. */
public final class FontRaster {
   /** Texels per font unit; 1 already matches the sign's text grid, higher only repeats texels. */
   public static final int SCALE = 1;
   public static final int LINE_HEIGHT = 10;
   /** Where the default font's baseline sits inside its line; each provider hangs off it. */
   private static final int BASELINE = 7;
   /** From {@code font/include/space.json}: the space character is not in the bitmap sheets. */
   private static final int SPACE_ADVANCE = 4;
   private static Map<Integer, FontRaster.Glyph> glyphs;
   private static boolean loadFailed;

   private FontRaster() {
   }

   public static synchronized boolean ensureLoaded(MinecraftClient mc, List<String> warnings) {
      if (glyphs != null) {
         return true;
      } else if (loadFailed) {
         return false;
      } else {
         try {
            glyphs = loadGlyphs(mc);
            return true;
         } catch (Exception exception) {
            loadFailed = true;
            warnings.add("default font load failed, sign text skipped (" + exception.getMessage() + ")");
            return false;
         }
      }
   }

   /** Only valid once {@link #ensureLoaded} ran. */
   public static boolean covers(int codePoint) {
      return codePoint == 32 || glyphs != null && glyphs.containsKey(codePoint);
   }

   private static Map<Integer, FontRaster.Glyph> loadGlyphs(MinecraftClient mc) throws IOException {
      Map<Integer, FontRaster.Glyph> map = new HashMap<>(1024);

      for (JsonObject jsonobject : bitmapProviders(mc)) {
         readSheet(mc, jsonobject, map);
      }

      // No 'A' means the sheets were not read, and every sign would export blank without a warning.
      if (!map.containsKey(65)) {
         throw new IOException("no Latin glyphs in the default font");
      } else {
         return map;
      }
   }

   /** The bitmap providers of the default font stack, highest priority first. */
   private static List<JsonObject> bitmapProviders(MinecraftClient mc) throws IOException {
      Identifier identifier = Identifier.of("minecraft", "font/include/default.json");
      Resource resource = mc.getResourceManager().getResource(identifier).orElseThrow(() -> new IOException("not found: " + identifier));
      List<JsonObject> list = new ArrayList<>();

      try (InputStream inputstream = resource.getInputStream()) {
         JsonObject jsonobject = JsonParser.parseReader(new InputStreamReader(inputstream, StandardCharsets.UTF_8)).getAsJsonObject();
         JsonArray jsonarray = jsonobject.getAsJsonArray("providers");
         if (jsonarray != null) {
            for (JsonElement jsonelement : jsonarray) {
               JsonObject jsonobject1 = jsonelement.getAsJsonObject();
               if (jsonobject1.has("type") && jsonobject1.get("type").getAsString().equals("bitmap") && jsonobject1.has("file")) {
                  list.add(jsonobject1);
               }
            }
         }
      }

      if (list.isEmpty()) {
         throw new IOException("no bitmap providers in " + identifier);
      } else {
         return list;
      }
   }

   /** Cuts one sheet into glyphs, keeping only a one-bit-per-pixel mask. */
   private static void readSheet(MinecraftClient mc, JsonObject provider, Map<Integer, FontRaster.Glyph> out) throws IOException {
      Identifier identifier = Identifier.of(provider.get("file").getAsString());
      Identifier identifier1 = Identifier.of(identifier.getNamespace(), "textures/" + identifier.getPath());
      Optional<Resource> optional = mc.getResourceManager().getResource(identifier1);
      if (optional.isPresent()) {
         JsonArray jsonarray = provider.getAsJsonArray("chars");
         int i = jsonarray.size();
         int[][] aint = new int[i][];

         for (int j = 0; j < i; j++) {
            aint[j] = jsonarray.get(j).getAsString().codePoints().toArray();
         }

         int k = aint[0].length;
         if (k != 0) {
            int l = provider.has("height") ? provider.get("height").getAsInt() : 8;
            int i1 = provider.get("ascent").getAsInt();

            try (InputStream inputstream = optional.get().getInputStream()) {
               NativeImage nativeimage = NativeImage.read(inputstream);

               try {
                  int j1 = nativeimage.getWidth() / k;
                  int k1 = nativeimage.getHeight() / i;
                  // One int of mask per row; vanilla cells are 8 or 9 wide, so wider ones are skipped.
                  if (j1 >= 1 && j1 <= 32 && k1 >= 1) {
                     for (int l1 = 0; l1 < i; l1++) {
                        for (int i2 = 0; i2 < aint[l1].length && i2 < k; i2++) {
                           int j2 = aint[l1][i2];
                           if (j2 != 0) {
                              out.putIfAbsent(j2, cut(nativeimage, i2 * j1, l1 * k1, j1, k1, l, i1));
                           }
                        }
                     }
                  }
               } finally {
                  nativeimage.close();
               }
            }
         }
      }
   }

   private static FontRaster.Glyph cut(NativeImage sheet, int x, int y, int cellW, int cellH, int height, int ascent) {
      int[] aint = new int[cellH];
      int i = 0;

      for (int j = 0; j < cellH; j++) {
         int k = 0;

         for (int l = 0; l < cellW; l++) {
            if ((sheet.getColorArgb(x + l, y + j) >>> 24) != 0) {
               k |= 1 << cellW - 1 - l;
               i = Math.max(i, l + 1);
            }
         }

         aint[j] = k;
      }

      return new FontRaster.Glyph(aint, cellW, cellH, i, height, ascent);
   }

   public static NativeImage render(String[] lines, int rgb, int canvasW, int canvasH) {
      NativeImage nativeimage = new NativeImage(canvasW, canvasH, false);

      for (int i = 0; i < canvasH; i++) {
         for (int j = 0; j < canvasW; j++) {
            nativeimage.setColorArgb(j, i, 0);
         }
      }

      int k = 0xFF000000 | rgb & 16777215;
      int l = LINE_HEIGHT * SCALE;
      int i1 = Math.max(0, (canvasH - lines.length * l) / 2);

      for (int j1 = 0; j1 < lines.length; j1++) {
         String s = lines[j1];
         if (s != null && !s.isBlank()) {
            int[] aint = s.codePoints().toArray();
            int k1 = 0;

            for (int l1 : aint) {
               k1 += advance(l1);
            }

            int i2 = Math.max(0, (canvasW - k1) / 2);
            int j2 = i1 + j1 * l + BASELINE * SCALE;

            for (int k2 : aint) {
               FontRaster.Glyph fontraster$glyph = glyphs.get(k2);
               if (fontraster$glyph != null) {
                  draw(nativeimage, fontraster$glyph, i2, j2, k);
               }

               i2 += advance(k2);
            }
         }
      }

      return nativeimage;
   }

   private static int advance(int codePoint) {
      FontRaster.Glyph fontraster$glyph = glyphs.get(codePoint);
      return fontraster$glyph == null || codePoint == 32
         ? SPACE_ADVANCE * SCALE
         : (Math.round(fontraster$glyph.width() * (float)fontraster$glyph.height() / fontraster$glyph.cellH()) + 1) * SCALE;
   }

   private static void draw(NativeImage canvas, FontRaster.Glyph glyph, int penX, int baseline, int argb) {
      int i = glyph.height() * SCALE;
      int j = baseline - glyph.ascent() * SCALE;
      int k = Math.round(glyph.width() * (float)i / glyph.cellH());

      for (int l = 0; l < i; l++) {
         int i1 = glyph.rows()[l * glyph.cellH() / i];
         if (i1 != 0) {
            int j1 = j + l;
            if (j1 >= 0 && j1 < canvas.getHeight()) {
               for (int k1 = 0; k1 < k; k1++) {
                  if ((i1 & 1 << glyph.cellW() - 1 - k1 * glyph.cellH() / i) != 0) {
                     int l1 = penX + k1;
                     if (l1 >= 0 && l1 < canvas.getWidth()) {
                        canvas.setColorArgb(l1, j1, argb);
                     }
                  }
               }
            }
         }
      }
   }

   /** One cell of a bitmap sheet: {@code rows} holds one bit per pixel, high bit leftmost. */
   private record Glyph(int[] rows, int cellW, int cellH, int width, int height, int ascent) {
   }
}
