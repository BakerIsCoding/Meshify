package com.norwood.meshify.export;

import com.norwood.meshify.mixin.SpriteContentsAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

public abstract class TexKey {
   private final String id;
   private final boolean preScaled;

   protected TexKey(String id) {
      this(id, false);
   }

   protected TexKey(String id, boolean preScaled) {
      this.id = id;
      this.preScaled = preScaled;
   }

   public final String id() {
      return this.id;
   }

   public final boolean preScaled() {
      return this.preScaled;
   }

   public abstract NativeImage load(MinecraftClient var1) throws IOException;

   public static String sanitize(String s) {
      return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
   }

   public static TexKey sprite(Sprite sprite, int tint) {
      Identifier identifier = sprite.getContents().getId();
      String s = identifier.getNamespace().equals("minecraft") ? identifier.getPath() : identifier.getNamespace() + "_" + identifier.getPath();
      String s1 = sanitize(s) + (tint >= 0 ? String.format(Locale.ROOT, "_t%06x", tint & 16777215) : "");
      return new TexKey.SpriteKey(s1, sprite, tint);
   }

   public static TexKey resource(Identifier pngPath) {
      String s = pngPath.getPath().replaceAll("^textures/", "").replaceAll("\\.png$", "");
      if (!pngPath.getNamespace().equals("minecraft")) {
         s = pngPath.getNamespace() + "_" + s;
      }

      return new TexKey.ResourceKey(sanitize(s), pngPath);
   }

   public static TexKey playerSkin(String url) {
      String s = url.substring(url.lastIndexOf(47) + 1);
      if (s.length() > 12) {
         s = s.substring(0, 12);
      }

      return new TexKey.SkinKey("player_skin_" + sanitize(s), url);
   }

   public static TexKey generated(String id, TexKey.ImageSupplier supplier) {
      return generated(id, supplier, false);
   }

   public static TexKey generated(String id, TexKey.ImageSupplier supplier, boolean preScaled) {
      return new TexKey(id, preScaled) {
         @Override
         public NativeImage load(MinecraftClient mc) throws IOException {
            return supplier.produce(mc);
         }
      };
   }

   public static TexKey composite(String id, List<TexKey.CompositeLayer> layers) {
      return new TexKey.CompositeKey(id, layers);
   }

   static NativeImage copyRegion(NativeImage src, int x, int y, int w, int h, int tint) {
      NativeImage nativeimage = new NativeImage(w, h, false);
      float f = 1.0F;
      float f1 = 1.0F;
      float f2 = 1.0F;
      if (tint >= 0) {
         f = (tint >> 16 & 0xFF) / 255.0F;
         f1 = (tint >> 8 & 0xFF) / 255.0F;
         f2 = (tint & 0xFF) / 255.0F;
      }

      for (int i = 0; i < h; i++) {
         for (int j = 0; j < w; j++) {
            int k = src.getColorArgb(x + j, y + i);
            if (tint >= 0) {
               int l = k >>> 24 & 0xFF;
               int i1 = (int)((k >> 16 & 0xFF) * f);
               int j1 = (int)((k >> 8 & 0xFF) * f1);
               int k1 = (int)((k & 0xFF) * f2);
               k = l << 24 | i1 << 16 | j1 << 8 | k1;
            }

            nativeimage.setColorArgb(j, i, k);
         }
      }

      return nativeimage;
   }

   private static final class CompositeKey extends TexKey {
      private final List<TexKey.CompositeLayer> layers;

      CompositeKey(String id, List<TexKey.CompositeLayer> layers) {
         super(id);
         this.layers = layers;
      }

      @Override
      public NativeImage load(MinecraftClient mc) throws IOException {
         NativeImage nativeimage = null;

         for (TexKey.CompositeLayer texkey$compositelayer : this.layers) {
            Resource resource = mc.getResourceManager()
               .getResource(texkey$compositelayer.png())
               .orElseThrow(() -> new IOException("resource not found: " + texkey$compositelayer.png()));

            NativeImage nativeimage1;
            try (InputStream inputstream = resource.getInputStream()) {
               nativeimage1 = NativeImage.read(inputstream);
            }

            try {
               if (nativeimage == null) {
                  nativeimage = copyRegion(nativeimage1, 0, 0, nativeimage1.getWidth(), nativeimage1.getHeight(), texkey$compositelayer.tint());
               } else {
                  blend(nativeimage, nativeimage1, texkey$compositelayer.tint());
               }
            } finally {
               nativeimage1.close();
            }
         }

         if (nativeimage == null) {
            throw new IOException("composite with no layers: " + this.id());
         } else {
            return nativeimage;
         }
      }

      private static void blend(NativeImage dst, NativeImage src, int tint) {
         float f = 1.0F;
         float f1 = 1.0F;
         float f2 = 1.0F;
         if (tint >= 0) {
            f = (tint >> 16 & 0xFF) / 255.0F;
            f1 = (tint >> 8 & 0xFF) / 255.0F;
            f2 = (tint & 0xFF) / 255.0F;
         }

         int i = Math.min(dst.getWidth(), src.getWidth());
         int j = Math.min(dst.getHeight(), src.getHeight());

         for (int k = 0; k < j; k++) {
            for (int l = 0; l < i; l++) {
               int i1 = src.getColorArgb(l, k);
               int j1 = i1 >>> 24 & 0xFF;
               if (j1 != 0) {
                  float f3 = j1 / 255.0F;
                  float f4 = (i1 >> 16 & 0xFF) * f;
                  float f5 = (i1 >> 8 & 0xFF) * f1;
                  float f6 = (i1 & 0xFF) * f2;
                  int k1 = dst.getColorArgb(l, k);
                  int l1 = k1 >>> 24 & 0xFF;
                  int i2 = (int)(f4 * f3 + (k1 >> 16 & 0xFF) * (1.0F - f3));
                  int j2 = (int)(f5 * f3 + (k1 >> 8 & 0xFF) * (1.0F - f3));
                  int k2 = (int)(f6 * f3 + (k1 & 0xFF) * (1.0F - f3));
                  int l2 = Math.min(255, (int)(j1 + l1 * (1.0F - f3)));
                  dst.setColorArgb(l, k, l2 << 24 | i2 << 16 | j2 << 8 | k2);
               }
            }
         }
      }
   }

   public record CompositeLayer(Identifier png, int tint) {
   }

   @FunctionalInterface
   public interface ImageSupplier {
      NativeImage produce(MinecraftClient var1) throws IOException;
   }

   private static final class ResourceKey extends TexKey {
      private final Identifier path;

      ResourceKey(String id, Identifier path) {
         super(id);
         this.path = path;
      }

      @Override
      public NativeImage load(MinecraftClient mc) throws IOException {
         Resource resource = mc.getResourceManager().getResource(this.path).orElseThrow(() -> new IOException("resource not found: " + this.path));

         NativeImage nativeimage;
         try (InputStream inputstream = resource.getInputStream()) {
            nativeimage = NativeImage.read(inputstream);
         }

         return nativeimage;
      }
   }

   private static final class SkinKey extends TexKey {
      private final String url;

      SkinKey(String id, String url) {
         super(id);
         this.url = url;
      }

      @Override
      public NativeImage load(MinecraftClient mc) throws IOException {
         try {
            URI uri = URI.create(this.url);
            String s = uri.getHost() == null ? "" : uri.getHost();
            if (!s.endsWith(".minecraft.net") && !s.equals("textures.minecraft.net")) {
               throw new IOException("unexpected skin host: " + s);
            } else {
               HttpURLConnection httpurlconnection = (HttpURLConnection)uri.toURL().openConnection();
               httpurlconnection.setConnectTimeout(4000);
               httpurlconnection.setReadTimeout(4000);

               NativeImage nativeimage2;
               try (InputStream inputstream1 = httpurlconnection.getInputStream()) {
                  NativeImage nativeimage1 = NativeImage.read(inputstream1);
                  if (nativeimage1.getHeight() == 32 && nativeimage1.getWidth() == 64) {
                     nativeimage2 = copyExpand(nativeimage1);
                     nativeimage1.close();
                     return nativeimage2;
                  }

                  nativeimage2 = nativeimage1;
               }

               return nativeimage2;
            }
         } catch (Exception exception) {
            Resource resource = mc.getResourceManager()
               .getResource(Identifier.of("minecraft", "textures/entity/player/wide/steve.png"))
               .orElseThrow(() -> new IOException("steve fallback missing"));

            NativeImage in;
            try (InputStream inputstream = resource.getInputStream()) {
               in = NativeImage.read(inputstream);
            }

            return in;
         }
      }

      private static NativeImage copyExpand(NativeImage src) {
         NativeImage nativeimage = new NativeImage(64, 64, false);

         for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
               nativeimage.setColorArgb(j, i, i < 32 ? src.getColorArgb(j, i) : 0);
            }
         }

         return nativeimage;
      }
   }

   private static final class SpriteKey extends TexKey {
      private final Sprite sprite;
      private final int tint;

      SpriteKey(String id, Sprite sprite, int tint) {
         super(id);
         this.sprite = sprite;
         this.tint = tint;
      }

      @Override
      public NativeImage load(MinecraftClient mc) {
         SpriteContents spritecontents = this.sprite.getContents();
         NativeImage nativeimage = ((SpriteContentsAccessor)spritecontents).meshify$getImage();
         int i = Math.min(spritecontents.getWidth(), nativeimage.getWidth());
         int j = Math.min(spritecontents.getHeight(), nativeimage.getHeight());
         return copyRegion(nativeimage, 0, 0, i, j, this.tint);
      }
   }
}
