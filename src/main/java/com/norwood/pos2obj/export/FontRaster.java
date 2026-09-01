package com.norwood.pos2obj.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

public final class FontRaster {
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
            warnings.add("unifont load failed, sign text skipped (" + exception.getMessage() + ")");
            return false;
         }
      }
   }

   private static Map<Integer, FontRaster.Glyph> loadGlyphs(MinecraftClient mc) throws IOException {
      Identifier identifier = Identifier.of("minecraft", "font/unifont.zip");

      try {
         Optional<Resource> optional = mc.getResourceManager().getResource(Identifier.of("minecraft", "font/include/unifont.json"));
         if (optional.isPresent()) {
            try (InputStream inputstream = optional.get().getInputStream()) {
               JsonObject jsonobject = JsonParser.parseReader(new InputStreamReader(inputstream, StandardCharsets.UTF_8)).getAsJsonObject();
               JsonArray jsonarray = jsonobject.getAsJsonArray("providers");
               if (jsonarray != null) {
                  for (JsonElement jsonelement : jsonarray) {
                     JsonObject jsonobject1 = jsonelement.getAsJsonObject();
                     if (jsonobject1.has("type") && jsonobject1.get("type").getAsString().equals("unihex") && jsonobject1.has("hex_file")) {
                        Identifier identifier1 = Identifier.of(jsonobject1.get("hex_file").getAsString());
                        identifier = identifier1;
                        break;
                     }
                  }
               }
            }
         }
      } catch (Exception exception) {
      }

      Resource resource = mc.getResourceManager().getResource(identifier).orElseThrow(() -> new IOException("unifont hex archive not found: "));
      Map<Integer, FontRaster.Glyph> map = new HashMap<>(70000);
      ZipInputStream zipinputstream = new ZipInputStream(resource.getInputStream());

      ZipEntry zipentry;
      try {
         label101:
         while ((zipentry = zipinputstream.getNextEntry()) != null) {
            if (zipentry.getName().endsWith(".hex")) {
               BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(zipinputstream, StandardCharsets.US_ASCII));

               while (true) {
                  String s1;
                  int l;
                  int i1;
                  while (true) {
                     if ((s1 = bufferedreader.readLine()) == null) {
                        break label101;
                     }

                     l = s1.indexOf(58);
                     if (l > 0) {
                        try {
                           i1 = Integer.parseInt(s1.substring(0, l), 16);
                           break;
                        } catch (NumberFormatException numberformatexception) {
                        }
                     }
                  }

                  String s = s1.substring(l + 1).trim();
                  int i = s.length() / 4;
                  if (i == 8 || i == 16 || i == 24 || i == 32) {
                     int j = i / 4;
                     int[] aint = new int[16];

                     for (int k = 0; k < 16; k++) {
                        aint[k] = (int)Long.parseLong(s.substring(k * j, (k + 1) * j), 16);
                     }

                     map.put(i1, new FontRaster.Glyph(i, aint));
                  }
               }
            }
         }
      } catch (Throwable throwable1) {
         try {
            zipinputstream.close();
         } catch (Throwable throwable) {
            throwable1.addSuppressed(throwable);
         }

         throw throwable1;
      }

      zipinputstream.close();
      if (map.isEmpty()) {
         throw new IOException("no glyphs parsed from " + identifier);
      } else {
         return map;
      }
   }

   public static NativeImage render(String[] lines, int rgb, int canvasW, int canvasH) {
      NativeImage nativeimage = new NativeImage(canvasW, canvasH, false);

      for (int i = 0; i < canvasH; i++) {
         for (int j = 0; j < canvasW; j++) {
            nativeimage.setColorArgb(j, i, 0);
         }
      }

      int i3 = 0xFF000000 | rgb & 16777215;
      int j3 = 16;
      int k = Math.max(0, (canvasH - lines.length * j3) / 2);

      for (int l = 0; l < lines.length; l++) {
         String s = lines[l];
         if (s != null && !s.isBlank()) {
            int[] aint = s.codePoints().toArray();
            int i1 = 0;
            int[][] aint1 = new int[aint.length][2];

            for (int j1 = 0; j1 < aint.length; j1++) {
               FontRaster.Glyph fontraster$glyph = glyphs.get(aint[j1]);
               if (aint[j1] != 32 && fontraster$glyph != null) {
                  int k1 = Integer.MAX_VALUE;
                  int l1 = -1;

                  for (int i2 : fontraster$glyph.rows()) {
                     if (i2 != 0) {
                        k1 = Math.min(k1, Integer.numberOfLeadingZeros(i2) - (32 - fontraster$glyph.width()));
                        l1 = Math.max(l1, fontraster$glyph.width() - 1 - Integer.numberOfTrailingZeros(i2));
                     }
                  }

                  if (l1 < 0) {
                     aint1[j1] = null;
                     i1 += 4;
                  } else {
                     aint1[j1] = new int[]{k1, l1};
                     i1 += l1 - k1 + 1 + 1;
                  }
               } else {
                  aint1[j1] = null;
                  i1 += 4;
               }
            }

            int k3 = Math.max(0, (canvasW - i1) / 2);
            int l3 = k + l * j3;

            for (int i4 = 0; i4 < aint.length; i4++) {
               FontRaster.Glyph fontraster$glyph1 = glyphs.get(aint[i4]);
               if (aint1[i4] != null && fontraster$glyph1 != null) {
                  int j4 = aint1[i4][0];
                  int k4 = aint1[i4][1];

                  for (int l4 = 0; l4 < 16; l4++) {
                     int i5 = fontraster$glyph1.rows()[l4];
                     if (i5 != 0) {
                        for (int j2 = j4; j2 <= k4; j2++) {
                           if ((i5 & 1 << fontraster$glyph1.width() - 1 - j2) != 0) {
                              int k2 = k3 + (j2 - j4);
                              int l2 = l3 + l4;
                              if (k2 >= 0 && k2 < canvasW && l2 >= 0 && l2 < canvasH) {
                                 nativeimage.setColorArgb(k2, l2, i3);
                              }
                           }
                        }
                     }
                  }

                  k3 += k4 - j4 + 1 + 1;
               } else {
                  k3 += 4;
               }
            }
         }
      }

      return nativeimage;
   }

   private record Glyph(int width, int[] rows) {
   }
}
