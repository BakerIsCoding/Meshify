package com.norwood.meshify.export;

import com.mojang.blaze3d.platform.NativeImage;
import com.norwood.meshify.mixin.SpriteContentsAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

public abstract class TextureKey {
   private final String id;
   private final boolean preScaled;

   protected TextureKey(String id) {
      this(id, false);
   }

   protected TextureKey(String id, boolean preScaled) {
      this.id = id;
      this.preScaled = preScaled;
   }

   public final String id() {
      return this.id;
   }

   public final boolean preScaled() {
      return this.preScaled;
   }

   public abstract NativeImage load(Minecraft client) throws IOException;

   public static String sanitize(String name) {
      return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
   }

   /** A tint's RGB, or -1 for none; white multiplies by one, so it is no tint either. */
   public static int normalizeTint(int argb) {
      int rgb = argb & 0xFFFFFF;
      return rgb == 0xFFFFFF ? -1 : rgb;
   }

   public static TextureKey sprite(TextureAtlasSprite sprite, int tint) {
      int rgb = normalizeTint(tint);
      Identifier id = sprite.contents().name();
      String name = id.getNamespace().equals("minecraft") ? id.getPath() : id.getNamespace() + "_" + id.getPath();
      return new TextureKey.SpriteKey(sanitize(name) + tintSuffix(rgb), sprite, rgb);
   }

   /** An atlas UV restated within {@code sprite}'s own 0..1 square. */
   public static float[] spriteUv(TextureAtlasSprite sprite, float u, float v) {
      float spanU = sprite.getU1() - sprite.getU0();
      float spanV = sprite.getV1() - sprite.getV0();
      return new float[]{
         spanU > 0.0F ? Math.clamp((u - sprite.getU0()) / spanU, 0.0F, 1.0F) : 0.0F,
         spanV > 0.0F ? Math.clamp((v - sprite.getV0()) / spanV, 0.0F, 1.0F) : 0.0F
      };
   }

   public static TextureKey resource(Identifier pngPath) {
      return new TextureKey.ResourceKey(resourceName(pngPath), pngPath);
   }

   /** A resource texture multiplied by {@code tint}, or the plain one when there is no tint. */
   public static TextureKey resource(Identifier pngPath, int tint) {
      int rgb = normalizeTint(tint);
      return rgb < 0 ? resource(pngPath) : composite(resourceName(pngPath) + tintSuffix(rgb), List.of(new TextureKey.CompositeLayer(pngPath, rgb)));
   }

   private static String resourceName(Identifier pngPath) {
      String name = pngPath.getPath().replaceAll("^textures/", "").replaceAll("\\.png$", "");
      return sanitize(pngPath.getNamespace().equals("minecraft") ? name : pngPath.getNamespace() + "_" + name);
   }

   private static String tintSuffix(int rgb) {
      return rgb < 0 ? "" : String.format(Locale.ROOT, "_t%06x", rgb);
   }

   public static TextureKey playerSkin(String url) {
      String file = url.substring(url.lastIndexOf('/') + 1);
      if (file.length() > 12) {
         file = file.substring(0, 12);
      }

      return new TextureKey.SkinKey("player_skin_" + sanitize(file), url);
   }

   public static TextureKey generated(String id, TextureKey.ImageSupplier supplier) {
      return generated(id, supplier, false);
   }

   public static TextureKey generated(String id, TextureKey.ImageSupplier supplier, boolean preScaled) {
      return new TextureKey(id, preScaled) {
         @Override
         public NativeImage load(Minecraft client) throws IOException {
            return supplier.produce(client);
         }
      };
   }

   public static TextureKey composite(String id, List<TextureKey.CompositeLayer> layers) {
      return new TextureKey.CompositeKey(id, layers);
   }

   /** Reads a PNG from the loaded resource packs. */
   static NativeImage readResource(Minecraft client, Identifier pngPath) throws IOException {
      Resource resource = client.getResourceManager().getResource(pngPath).orElseThrow(() -> new IOException("resource not found: " + pngPath));

      try (InputStream in = resource.open()) {
         return NativeImage.read(in);
      }
   }

   static NativeImage copyRegion(NativeImage src, int x, int y, int w, int h, int tint) {
      float[] factor = tintFactors(tint);
      NativeImage copy = new NativeImage(w, h, false);

      for (int row = 0; row < h; row++) {
         for (int col = 0; col < w; col++) {
            int argb = src.getPixel(x + col, y + row);
            int red = (int)((argb >> 16 & 0xFF) * factor[0]);
            int green = (int)((argb >> 8 & 0xFF) * factor[1]);
            int blue = (int)((argb & 0xFF) * factor[2]);
            copy.setPixel(col, row, argb & 0xFF000000 | red << 16 | green << 8 | blue);
         }
      }

      return copy;
   }

   /** A tint's red, green and blue as 0..1 multipliers; all ones for no tint. */
   private static float[] tintFactors(int tint) {
      return tint < 0
         ? new float[]{1.0F, 1.0F, 1.0F}
         : new float[]{(tint >> 16 & 0xFF) / 255.0F, (tint >> 8 & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F};
   }

   private static final class CompositeKey extends TextureKey {
      private final List<TextureKey.CompositeLayer> layers;

      CompositeKey(String id, List<TextureKey.CompositeLayer> layers) {
         super(id);
         this.layers = layers;
      }

      @Override
      public NativeImage load(Minecraft client) throws IOException {
         NativeImage result = null;

         for (TextureKey.CompositeLayer layer : this.layers) {
            NativeImage image = readResource(client, layer.texturePath());

            try {
               if (result == null) {
                  result = copyRegion(image, 0, 0, image.getWidth(), image.getHeight(), layer.tint());
               } else {
                  blend(result, image, layer.tint());
               }
            } finally {
               image.close();
            }
         }

         if (result == null) {
            throw new IOException("composite with no layers: " + this.id());
         }

         return result;
      }

      /** Draws {@code src}, tinted, over {@code dst} with ordinary alpha blending. */
      private static void blend(NativeImage dst, NativeImage src, int tint) {
         float[] factor = tintFactors(tint);
         int w = Math.min(dst.getWidth(), src.getWidth());
         int h = Math.min(dst.getHeight(), src.getHeight());

         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
               int top = src.getPixel(x, y);
               int topAlpha = top >>> 24;
               if (topAlpha == 0) {
                  continue;
               }

               float a = topAlpha / 255.0F;
               int bottom = dst.getPixel(x, y);
               int red = (int)((top >> 16 & 0xFF) * factor[0] * a + (bottom >> 16 & 0xFF) * (1.0F - a));
               int green = (int)((top >> 8 & 0xFF) * factor[1] * a + (bottom >> 8 & 0xFF) * (1.0F - a));
               int blue = (int)((top & 0xFF) * factor[2] * a + (bottom & 0xFF) * (1.0F - a));
               int alpha = Math.min(255, (int)(topAlpha + (bottom >>> 24) * (1.0F - a)));
               dst.setPixel(x, y, alpha << 24 | red << 16 | green << 8 | blue);
            }
         }
      }
   }

   public record CompositeLayer(Identifier texturePath, int tint) {
   }

   @FunctionalInterface
   public interface ImageSupplier {
      NativeImage produce(Minecraft client) throws IOException;
   }

   private static final class ResourceKey extends TextureKey {
      private final Identifier path;

      ResourceKey(String id, Identifier path) {
         super(id);
         this.path = path;
      }

      @Override
      public NativeImage load(Minecraft client) throws IOException {
         return readResource(client, this.path);
      }
   }

   private static final class SkinKey extends TextureKey {
      private static final int TIMEOUT_MS = 4000;
      private static final int SKIN_SIZE = 64;
      private static final int LEGACY_SKIN_HEIGHT = 32;
      private static final Identifier STEVE_SKIN = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
      private final String url;

      SkinKey(String id, String url) {
         super(id);
         this.url = url;
      }

      @Override
      public NativeImage load(Minecraft client) throws IOException {
         try {
            return this.download();
         } catch (Exception exception) {
            // Offline, a slow skin server or a foreign host: a Steve head beats a missing one.
            return readResource(client, STEVE_SKIN);
         }
      }

      private NativeImage download() throws IOException {
         URI uri = URI.create(this.url);
         String host = uri.getHost() == null ? "" : uri.getHost();
         // The URL comes from the head's block data, which any server can set, so only Mojang's skin host is fetched.
         if (!host.endsWith(".minecraft.net")) {
            throw new IOException("unexpected skin host: " + host);
         }

         HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
         connection.setConnectTimeout(TIMEOUT_MS);
         connection.setReadTimeout(TIMEOUT_MS);

         try (InputStream in = connection.getInputStream()) {
            NativeImage skin = NativeImage.read(in);
            if (skin.getWidth() != SKIN_SIZE || skin.getHeight() != LEGACY_SKIN_HEIGHT) {
               return skin;
            }

            NativeImage expanded = expandLegacy(skin);
            skin.close();
            return expanded;
         }
      }

      /** Pads a pre-1.8 64x32 skin to the square layout the head UVs assume. */
      private static NativeImage expandLegacy(NativeImage src) {
         NativeImage expanded = new NativeImage(SKIN_SIZE, SKIN_SIZE, false);

         for (int y = 0; y < SKIN_SIZE; y++) {
            for (int x = 0; x < SKIN_SIZE; x++) {
               expanded.setPixel(x, y, y < LEGACY_SKIN_HEIGHT ? src.getPixel(x, y) : 0);
            }
         }

         return expanded;
      }
   }

   private static final class SpriteKey extends TextureKey {
      private final TextureAtlasSprite sprite;
      private final int tint;

      SpriteKey(String id, TextureAtlasSprite sprite, int tint) {
         super(id);
         this.sprite = sprite;
         this.tint = tint;
      }

      @Override
      public NativeImage load(Minecraft client) {
         SpriteContents contents = this.sprite.contents();
         NativeImage image = ((SpriteContentsAccessor)contents).meshify$getImage();
         // Animated sprites stack their frames below the first; only the first is copied.
         int w = Math.min(contents.width(), image.getWidth());
         int h = Math.min(contents.height(), image.getHeight());
         return copyRegion(image, 0, 0, w, h, this.tint);
      }
   }
}
