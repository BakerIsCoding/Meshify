package com.norwood.meshify.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;

public final class TextureStore implements AutoCloseable {
   private final MinecraftClient mc;
   private final int scale;
   private final Map<String, NativeImage> images = new LinkedHashMap<>();
   private NativeImage atlasImage;

   public TextureStore(MinecraftClient mc, int scale) {
      this.mc = mc;
      this.scale = Math.max(1, Math.min(16, scale));
   }

   public NativeImage get(TexKey key, List<String> warnings) {
      return this.images.computeIfAbsent(key.id(), id -> {
         NativeImage nativeimage;
         try {
            nativeimage = key.load(this.mc);
         } catch (Exception exception) {
            warnings.add("texture failed: " + id + " (" + exception.getMessage() + ")");
            nativeimage = placeholder();
         }

         if (this.scale > 1 && !key.preScaled()) {
            NativeImage nativeimage1 = upscale(nativeimage, this.scale);
            nativeimage.close();
            return nativeimage1;
         } else {
            return nativeimage;
         }
      });
   }

   private static NativeImage upscale(NativeImage src, int s) {
      NativeImage nativeimage = new NativeImage(src.getWidth() * s, src.getHeight() * s, false);

      for (int i = 0; i < nativeimage.getHeight(); i++) {
         for (int j = 0; j < nativeimage.getWidth(); j++) {
            nativeimage.setColorArgb(j, i, src.getColorArgb(j / s, i / s));
         }
      }

      return nativeimage;
   }

   private static NativeImage placeholder() {
      NativeImage nativeimage = new NativeImage(16, 16, false);

      for (int i = 0; i < 16; i++) {
         for (int j = 0; j < 16; j++) {
            boolean flag = (j / 8 + i / 8) % 2 == 0;
            nativeimage.setColorArgb(j, i, flag ? -65281 : -16777216);
         }
      }

      return nativeimage;
   }

   public TextureStore.Atlas packAtlas() {
      int i = 4;
      List<Entry<String, NativeImage>> list = new ArrayList<>(this.images.entrySet());
      list.sort(Comparator.<Entry<String, NativeImage>>comparingInt(ex -> ((NativeImage)ex.getValue()).getHeight()).reversed());
      long j = 0L;
      int k = 64;

      for (Entry<String, NativeImage> entry : list) {
         j += (long)(entry.getValue().getWidth() + 8) * (entry.getValue().getHeight() + 8);
         k = Math.max(k, entry.getValue().getWidth() + 8);
      }

      int k3 = ceilPow2((int)Math.max((double)k, Math.ceil(Math.sqrt(j))));
      Map<String, TextureStore.AtlasEntry> map = new LinkedHashMap<>();
      int l = 0;
      int i1 = 0;
      int j1 = 0;

      for (Entry<String, NativeImage> entry1 : list) {
         NativeImage nativeimage = entry1.getValue();
         int k1 = nativeimage.getWidth() + 8;
         int l1 = nativeimage.getHeight() + 8;
         if (l + k1 > k3) {
            l = 0;
            i1 += j1;
            j1 = 0;
         }

         map.put(entry1.getKey(), new TextureStore.AtlasEntry(l + 4, i1 + 4, nativeimage.getWidth(), nativeimage.getHeight()));
         l += k1;
         j1 = Math.max(j1, l1);
      }

      int l3 = ceilPow2(i1 + j1);
      NativeImage nativeimage2 = new NativeImage(k3, l3, false);

      for (int i4 = 0; i4 < l3; i4++) {
         for (int j4 = 0; j4 < k3; j4++) {
            nativeimage2.setColorArgb(j4, i4, 0);
         }
      }

      for (Entry<String, NativeImage> entry2 : list) {
         TextureStore.AtlasEntry texturestore$atlasentry = map.get(entry2.getKey());
         NativeImage nativeimage1 = entry2.getValue();
         int i2 = texturestore$atlasentry.w();
         int j2 = texturestore$atlasentry.h();

         for (int k2 = -4; k2 < j2 + 4; k2++) {
            int l2 = Math.min(Math.max(k2, 0), j2 - 1);

            for (int i3 = -4; i3 < i2 + 4; i3++) {
               int j3 = Math.min(Math.max(i3, 0), i2 - 1);
               nativeimage2.setColorArgb(texturestore$atlasentry.x() + i3, texturestore$atlasentry.y() + k2, nativeimage1.getColorArgb(j3, l2));
            }
         }
      }

      this.atlasImage = nativeimage2;
      return new TextureStore.Atlas(nativeimage2, map, k3, l3);
   }

   private static int ceilPow2(int v) {
      int i = 1;

      while (i < v) {
         i <<= 1;
      }

      return i;
   }

   public Map<String, NativeImage> images() {
      return this.images;
   }

   @Override
   public void close() {
      for (NativeImage nativeimage : this.images.values()) {
         try {
            nativeimage.close();
         } catch (Exception exception1) {
         }
      }

      this.images.clear();
      if (this.atlasImage != null) {
         try {
            this.atlasImage.close();
         } catch (Exception exception) {
         }

         this.atlasImage = null;
      }
   }

   public record Atlas(NativeImage image, Map<String, TextureStore.AtlasEntry> entries, int width, int height) {
   }

   public record AtlasEntry(int x, int y, int w, int h) {
   }
}
