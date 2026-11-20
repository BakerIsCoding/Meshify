package com.norwood.meshify.export;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;

public final class TextureStore implements AutoCloseable {
   // Edge pixels repeated around each atlas entry, so sampling never bleeds in a neighbour.
   private static final int PADDING = 4;
   private static final int MIN_ATLAS_WIDTH = 64;
   private static final int PLACEHOLDER_SIZE = 16;
   private static final int PLACEHOLDER_CHECKER = 8;
   private static final int PLACEHOLDER_MAGENTA = 0xFFFF00FF;
   private static final int PLACEHOLDER_BLACK = 0xFF000000;
   private final Minecraft client;
   private final int scale;
   private final Map<String, NativeImage> images = new LinkedHashMap<>();
   private NativeImage atlasImage;

   public TextureStore(Minecraft client, int scale) {
      this.client = client;
      this.scale = scale;
   }

   public NativeImage getOrLoad(TextureKey key, List<String> warnings) {
      return this.images.computeIfAbsent(key.id(), id -> {
         NativeImage image;
         try {
            image = key.load(this.client);
         } catch (Exception exception) {
            warnings.add("texture failed: " + id + " (" + exception.getMessage() + ")");
            image = placeholder();
         }

         if (this.scale > 1 && !key.preScaled()) {
            NativeImage scaled = upscale(image, this.scale);
            image.close();
            return scaled;
         } else {
            return image;
         }
      });
   }

   private static NativeImage upscale(NativeImage source, int factor) {
      NativeImage scaled = new NativeImage(source.getWidth() * factor, source.getHeight() * factor, false);

      for (int y = 0; y < scaled.getHeight(); y++) {
         for (int x = 0; x < scaled.getWidth(); x++) {
            scaled.setPixel(x, y, source.getPixel(x / factor, y / factor));
         }
      }

      return scaled;
   }

   // The magenta and black checkerboard the game shows for a missing texture.
   private static NativeImage placeholder() {
      NativeImage image = new NativeImage(PLACEHOLDER_SIZE, PLACEHOLDER_SIZE, false);

      for (int y = 0; y < PLACEHOLDER_SIZE; y++) {
         for (int x = 0; x < PLACEHOLDER_SIZE; x++) {
            boolean magenta = (x / PLACEHOLDER_CHECKER + y / PLACEHOLDER_CHECKER) % 2 == 0;
            image.setPixel(x, y, magenta ? PLACEHOLDER_MAGENTA : PLACEHOLDER_BLACK);
         }
      }

      return image;
   }

   // Shelf-packs every texture, tallest first, into one power-of-two atlas.
   public TextureStore.Atlas packAtlas() {
      List<Entry<String, NativeImage>> sorted = new ArrayList<>(this.images.entrySet());
      sorted.sort(Comparator.<Entry<String, NativeImage>>comparingInt(entry -> entry.getValue().getHeight()).reversed());
      long area = 0L;
      int widest = MIN_ATLAS_WIDTH;

      for (Entry<String, NativeImage> entry : sorted) {
         area += (long)(entry.getValue().getWidth() + 2 * PADDING) * (entry.getValue().getHeight() + 2 * PADDING);
         widest = Math.max(widest, entry.getValue().getWidth() + 2 * PADDING);
      }

      int width = ceilPowerOfTwo((int)Math.max((double)widest, Math.ceil(Math.sqrt(area))));
      Map<String, TextureStore.Slot> slots = new LinkedHashMap<>();
      int x = 0;
      int y = 0;
      int shelfHeight = 0;

      for (Entry<String, NativeImage> entry : sorted) {
         NativeImage image = entry.getValue();
         int paddedWidth = image.getWidth() + 2 * PADDING;
         int paddedHeight = image.getHeight() + 2 * PADDING;
         if (x + paddedWidth > width) {
            x = 0;
            y += shelfHeight;
            shelfHeight = 0;
         }

         slots.put(entry.getKey(), new TextureStore.Slot(x + PADDING, y + PADDING, image.getWidth(), image.getHeight()));
         x += paddedWidth;
         shelfHeight = Math.max(shelfHeight, paddedHeight);
      }

      int height = ceilPowerOfTwo(y + shelfHeight);
      NativeImage atlas = new NativeImage(width, height, false);
      atlas.fillRect(0, 0, width, height, 0);
      Map<String, TextureStore.UvTransform> uvs = new LinkedHashMap<>();

      for (Entry<String, NativeImage> entry : sorted) {
         TextureStore.Slot slot = slots.get(entry.getKey());
         NativeImage image = entry.getValue();

         for (int dy = -PADDING; dy < slot.height() + PADDING; dy++) {
            int srcY = Math.clamp(dy, 0, slot.height() - 1);

            for (int dx = -PADDING; dx < slot.width() + PADDING; dx++) {
               atlas.setPixel(slot.x() + dx, slot.y() + dy, image.getPixel(Math.clamp(dx, 0, slot.width() - 1), srcY));
            }
         }

         uvs.put(
            entry.getKey(),
            new TextureStore.UvTransform((float)slot.width() / width, (float)slot.height() / height, (float)slot.x() / width, (float)slot.y() / height)
         );
      }

      this.atlasImage = atlas;
      return new TextureStore.Atlas(atlas, uvs);
   }

   static int ceilPowerOfTwo(int value) {
      int power = 1;

      while (power < value) {
         power <<= 1;
      }

      return power;
   }

   public Map<String, NativeImage> images() {
      return this.images;
   }

   @Override
   public void close() {
      this.images.values().forEach(NativeImage::close);
      this.images.clear();
      if (this.atlasImage != null) {
         this.atlasImage.close();
         this.atlasImage = null;
      }
   }

   public record Atlas(NativeImage image, Map<String, TextureStore.UvTransform> uvs) {
   }

   private record Slot(int x, int y, int width, int height) {
   }

   // Takes a texture's own 0..1 UVs to where it sits in the atlas.
   public record UvTransform(float scaleU, float scaleV, float offsetU, float offsetV) {
      public static final TextureStore.UvTransform IDENTITY = new TextureStore.UvTransform(1.0F, 1.0F, 0.0F, 0.0F);

      public float u(float u) {
         return this.offsetU + u * this.scaleU;
      }

      public float v(float v) {
         return this.offsetV + v * this.scaleV;
      }
   }
}
