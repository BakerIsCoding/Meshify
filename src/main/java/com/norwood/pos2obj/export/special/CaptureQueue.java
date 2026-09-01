package com.norwood.pos2obj.export.special;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import com.norwood.pos2obj.export.MeshBuilder;
import com.norwood.pos2obj.export.TexKey;
import com.norwood.pos2obj.mixin.RenderLayerAccessor;
import com.norwood.pos2obj.mixin.RenderSetupAccessor;
import com.norwood.pos2obj.mixin.SpriteAtlasTextureAccessor;
import com.norwood.pos2obj.mixin.TextureSpecAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand;
import net.minecraft.client.render.command.OrderedRenderCommandQueue.Custom;
import net.minecraft.client.render.command.OrderedRenderCommandQueue.LayeredCustom;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState.LeashData;
import net.minecraft.client.render.entity.state.EntityRenderState.ShadowPiece;
import net.minecraft.client.render.item.ItemRenderState.Glint;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.map.MapState;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

final class CaptureQueue implements OrderedRenderCommandQueue {
   private final MeshBuilder mesh;
   private final MinecraftClient mc;
   private final List<String> warnings;
   private final Map<Identifier, Map<Identifier, Sprite>> atlasCache = new HashMap<>();
   int captured;
   Integer contextMapId;

   CaptureQueue(MeshBuilder mesh, MinecraftClient mc, List<String> warnings) {
      this.mesh = mesh;
      this.mc = mc;
      this.warnings = warnings;
   }

   @Override
   public RenderCommandQueue getBatchingQueue(int order) {
      return this;
   }

   private CaptureQueue.CapturingConsumer consumer(RenderLayer layer, Sprite sprite, int tint) {
      String s = ((RenderLayerAccessor)layer).pos2obj$getName().toLowerCase(Locale.ROOT);
      if (!s.contains("glint") && !s.contains("outline") && !s.contains("crumbling") && !s.contains("shadow")) {
         int i = normTint(tint);
         CaptureQueue.QuadResolver capturequeue$quadresolver;
         if (sprite != null) {
            TexKey texkey = TexKey.sprite(sprite, i);
            capturequeue$quadresolver = uv -> texkey;
         } else {
            Identifier identifier = textureOf(layer);
            if (identifier == null) {
               this.warnOnce("BER/entity layer without resolvable texture skipped: " + s);
               return null;
            }

            CaptureQueue.QuadResolver capturequeue$quadresolver1 = this.tryMapResolver(identifier);
            if (capturequeue$quadresolver1 != null) {
               capturequeue$quadresolver = capturequeue$quadresolver1;
            } else if (identifier.getPath().startsWith("textures/atlas/")) {
               capturequeue$quadresolver = uv -> this.resolveAtlas(identifier, uv, i);
            } else {
               TexKey texkey1 = i < 0
                  ? TexKey.resource(identifier)
                  : TexKey.composite(
                     TexKey.sanitize(identifier.getPath().replaceAll("^textures/|\\.png$", "")) + String.format(Locale.ROOT, "_t%06x", i),
                     List.of(new TexKey.CompositeLayer(identifier, i))
                  );
               capturequeue$quadresolver = uv -> texkey1;
            }
         }

         boolean flag = layer.getDrawMode() == DrawMode.TRIANGLES;
         return new CaptureQueue.CapturingConsumer(this, capturequeue$quadresolver, flag);
      } else {
         return null;
      }
   }

   private TexKey resolveAtlas(Identifier atlasId, float[][] uv, int tint) {
      Map<Identifier, Sprite> map = this.atlasCache.computeIfAbsent(atlasId, id -> {
         if (this.mc.getTextureManager().getTexture(id) instanceof SpriteAtlasTexture spriteatlastexture) {
            Map<Identifier, Sprite> map1 = ((SpriteAtlasTextureAccessor)spriteatlastexture).pos2obj$getSprites();
            return map1 != null ? map1 : Map.of();
         } else {
            return Map.of();
         }
      });
      if (map.isEmpty()) {
         this.warnOnce("atlas not resolvable, quads skipped: " + atlasId);
         return null;
      } else {
         float f = (uv[0][0] + uv[1][0] + uv[2][0] + uv[3][0]) / 4.0F;
         float f1 = (uv[0][1] + uv[1][1] + uv[2][1] + uv[3][1]) / 4.0F;

         for (Sprite sprite : map.values()) {
            if (f >= sprite.getMinU() && f <= sprite.getMaxU() && f1 >= sprite.getMinV() && f1 <= sprite.getMaxV()) {
               float f2 = sprite.getMaxU() - sprite.getMinU();
               float f3 = sprite.getMaxV() - sprite.getMinV();

               for (float[] afloat : uv) {
                  afloat[0] = f2 > 0.0F ? clamp01((afloat[0] - sprite.getMinU()) / f2) : 0.0F;
                  afloat[1] = f3 > 0.0F ? clamp01((afloat[1] - sprite.getMinV()) / f3) : 0.0F;
               }

               return TexKey.sprite(sprite, tint);
            }
         }

         this.warnOnce("no sprite found for UVs on " + atlasId);
         return null;
      }
   }

   private CaptureQueue.QuadResolver tryMapResolver(Identifier texId) {
      String s = texId.getPath();
      boolean flag = s.startsWith("map/");
      boolean flag1 = s.contains("map_atlas");
      if (!flag && !flag1) {
         return null;
      } else {
         int i = -1;
         if (flag) {
            try {
               i = Integer.parseInt(s.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException numberformatexception) {
            }
         }

         if (i < 0 && this.contextMapId != null) {
            i = this.contextMapId;
         }

         if (i >= 0 && this.mc.world != null) {
            MapState mapstate = this.mc.world.getMapState(new MapIdComponent(i));
            if (mapstate == null) {
               this.warnOnce("map state not available: map id " + i);
               return null;
            } else {
               byte[] abyte = (byte[])mapstate.colors.clone();
               TexKey texkey = TexKey.generated("map_" + i, client -> {
                  NativeImage nativeimage = new NativeImage(128, 128, false);

                  for (int j = 0; j < 16384; j++) {
                     nativeimage.setColorArgb(j & 127, j >> 7, MapColor.getRenderColor(abyte[j] & 255));
                  }

                  return nativeimage;
               });
               return uv -> {
                  for (int j = 0; j < 2; j++) {
                     float f = Float.MAX_VALUE;
                     float f1 = -Float.MAX_VALUE;

                     for (float[] afloat : uv) {
                        f = Math.min(f, afloat[j]);
                        f1 = Math.max(f1, afloat[j]);
                     }

                     float f2 = f1 - f;

                     for (float[] afloat1 : uv) {
                        afloat1[j] = f2 > 1.0E-6F ? (afloat1[j] - f) / f2 : 0.0F;
                     }
                  }

                  return texkey;
               };
            }
         } else {
            this.warnOnce("map texture without resolvable map id skipped: " + texId);
            return null;
         }
      }
   }

   private static float clamp01(float f) {
      return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
   }

   private static int normTint(int argb) {
      if (argb != 0 && argb != -1) {
         int i = argb & 16777215;
         return i == 16777215 ? -1 : i;
      } else {
         return -1;
      }
   }

   private void warnOnce(String msg) {
      if (!this.warnings.contains(msg)) {
         this.warnings.add(msg);
      }
   }

   private static Identifier textureOf(RenderLayer layer) {
      try {
         RenderSetup rendersetup = ((RenderLayerAccessor)layer).pos2obj$getRenderSetup();
         Map<?, ?> map = ((RenderSetupAccessor)(Object)rendersetup).pos2obj$getTextures();

         for (Object object : map.values()) {
            if (object instanceof TextureSpecAccessor texturespecaccessor) {
               Identifier identifier = texturespecaccessor.pos2obj$getLocation();
               if (identifier != null) {
                  return identifier;
               }
            }
         }
      } catch (Exception exception) {
      }

      return null;
   }

   @Override
   public void submitModelPart(
      ModelPart part,
      MatrixStack matrices,
      RenderLayer renderLayer,
      int light,
      int overlay,
      Sprite sprite,
      boolean sheeted,
      boolean hasGlint,
      int tintedColor,
      CrumblingOverlayCommand crumblingOverlay,
      int i2
   ) {
      CaptureQueue.CapturingConsumer capturequeue$capturingconsumer = this.consumer(renderLayer, sprite, tintedColor);
      if (capturequeue$capturingconsumer != null) {
         part.render(matrices, capturequeue$capturingconsumer, light, overlay);
         capturequeue$capturingconsumer.finish();
      }
   }

   @Override
   public <S> void submitModel(
      Model<? super S> model,
      S state,
      MatrixStack matrices,
      RenderLayer renderLayer,
      int light,
      int overlay,
      int tintedColor,
      Sprite sprite,
      int outlineColor,
      CrumblingOverlayCommand crumblingOverlay
   ) {
      CaptureQueue.CapturingConsumer capturequeue$capturingconsumer = this.consumer(renderLayer, sprite, tintedColor);
      if (capturequeue$capturingconsumer != null) {
         model.setAngles(state);
         model.render(matrices, capturequeue$capturingconsumer, light, overlay);
         capturequeue$capturingconsumer.finish();
      }
   }

   @Override
   public void submitBlockStateModel(
      MatrixStack matrices, RenderLayer renderLayer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor
   ) {
      int i = (int)(r * 255.0F) << 16 | (int)(g * 255.0F) << 8 | (int)(b * 255.0F);
      this.emitBakedModel(matrices, model, i);
   }

   @Override
   public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor) {
      BlockStateModel blockstatemodel = this.mc.getBlockRenderManager().getModels().getModel(state);
      this.emitBakedModel(matrices, blockstatemodel, 16777215);
   }

   private void emitBakedModel(MatrixStack matrices, BlockStateModel model, int tint) {
      for (BlockModelPart blockmodelpart : model.getParts(Random.create(42L))) {
         for (int i = -1; i < 6; i++) {
            Direction direction = i < 0 ? null : Direction.values()[i];

            for (BakedQuad bakedquad : blockmodelpart.getQuads(direction)) {
               this.emitBakedQuad(matrices, bakedquad, tint);
            }
         }
      }
   }

   @Override
   public void submitItem(
      MatrixStack matrices,
      ItemDisplayContext displayContext,
      int light,
      int overlay,
      int outlineColors,
      int[] tintLayers,
      List<BakedQuad> quads,
      RenderLayer renderLayer,
      Glint glintType
   ) {
      for (BakedQuad bakedquad : quads) {
         int i = 16777215;
         if (bakedquad.tintIndex() >= 0 && tintLayers != null && bakedquad.tintIndex() < tintLayers.length) {
            i = tintLayers[bakedquad.tintIndex()] & 16777215;
         }

         this.emitBakedQuad(matrices, bakedquad, i);
      }
   }

   private void emitBakedQuad(MatrixStack matrices, BakedQuad quad, int tint) {
      Sprite sprite = quad.sprite();
      if (sprite != null) {
         Matrix4f matrix4f = matrices.peek().getPositionMatrix();
         float f = sprite.getMinU();
         float f1 = sprite.getMaxU();
         float f2 = sprite.getMinV();
         float f3 = sprite.getMaxV();
         float f4 = f1 - f;
         float f5 = f3 - f2;
         float[][] afloat = new float[4][3];
         float[][] afloat1 = new float[4][2];
         Vector4f vector4f = new Vector4f();

         for (int i = 0; i < 4; i++) {
            Vector3fc vector3fc = quad.getPosition(i);
            vector4f.set(vector3fc.x(), vector3fc.y(), vector3fc.z(), 1.0F);
            matrix4f.transform(vector4f);
            afloat[i][0] = vector4f.x();
            afloat[i][1] = vector4f.y();
            afloat[i][2] = vector4f.z();
            long j = quad.getTexcoords(i);
            float f6 = Vector2f.getX(j);
            float f7 = Vector2f.getY(j);
            afloat1[i][0] = f4 > 0.0F ? clamp01((f6 - f) / f4) : 0.0F;
            afloat1[i][1] = f5 > 0.0F ? clamp01((f7 - f2) / f5) : 0.0F;
         }

         Vector3f vector3f = matrices.peek().transformNormal(quad.face().getFloatVector(), new Vector3f());
         this.mesh.addQuad(TexKey.sprite(sprite, tint == 16777215 ? -1 : tint), afloat, afloat1, new float[]{vector3f.x(), vector3f.y(), vector3f.z()});
         this.captured++;
      }
   }

   @Override
   public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, Custom customRenderer) {
      CaptureQueue.CapturingConsumer capturequeue$capturingconsumer = this.consumer(renderLayer, null, -1);
      if (capturequeue$capturingconsumer != null) {
         customRenderer.render(matrices.peek(), capturequeue$capturingconsumer);
         capturequeue$capturingconsumer.finish();
      }
   }

   @Override
   public void submitCustom(LayeredCustom customRenderer) {
   }

   @Override
   public void submitText(
      MatrixStack matrices,
      float x,
      float y,
      OrderedText text,
      boolean dropShadow,
      TextLayerType layerType,
      int light,
      int color,
      int backgroundColor,
      int outlineColor
   ) {
      this.warnOnce("text rendering not captured (sign text uses the sign exporter path)");
   }

   @Override
   public void submitLabel(
      MatrixStack matrices,
      Vec3d nameLabelPos,
      int y,
      Text label,
      boolean notSneaking,
      int light,
      double squaredDistanceToCamera,
      CameraRenderState cameraState
   ) {
   }

   @Override
   public void submitShadowPieces(MatrixStack matrices, float shadowRadius, List<ShadowPiece> shadowPieces) {
   }

   @Override
   public void submitFire(MatrixStack matrices, EntityRenderState renderState, Quaternionf rotation) {
   }

   @Override
   public void submitLeash(MatrixStack matrices, LeashData leashData) {
   }

   @Override
   public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state) {
   }

   private static final class CapturingConsumer implements VertexConsumer {
      private final CaptureQueue owner;
      private final CaptureQueue.QuadResolver resolver;
      private final int groupSize;
      private final ArrayList<float[]> verts = new ArrayList<>();
      private float[] current;

      CapturingConsumer(CaptureQueue owner, CaptureQueue.QuadResolver resolver, boolean triangles) {
         this.owner = owner;
         this.resolver = resolver;
         this.groupSize = triangles ? 3 : 4;
      }

      @Override
      public VertexConsumer vertex(float x, float y, float z) {
         this.flushVertex();
         this.current = new float[]{x, y, z, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F};
         return this;
      }

      private void flushVertex() {
         if (this.current != null) {
            this.verts.add(this.current);
            this.current = null;
         }
      }

      @Override
      public VertexConsumer texture(float u, float v) {
         if (this.current != null) {
            this.current[3] = u;
            this.current[4] = v;
         }

         return this;
      }

      @Override
      public VertexConsumer normal(float x, float y, float z) {
         if (this.current != null) {
            this.current[5] = x;
            this.current[6] = y;
            this.current[7] = z;
            this.current[8] = 1.0F;
         }

         return this;
      }

      @Override
      public VertexConsumer color(int red, int green, int blue, int alpha) {
         return this;
      }

      @Override
      public VertexConsumer color(int argb) {
         return this;
      }

      @Override
      public VertexConsumer overlay(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer light(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer lineWidth(float width) {
         return this;
      }

      void finish() {
         this.flushVertex();

         for (int i = 0; i + this.groupSize <= this.verts.size(); i += this.groupSize) {
            float[][] afloat = new float[4][3];
            float[][] afloat1 = new float[4][2];
            float[] afloat2 = null;

            for (int j = 0; j < 4; j++) {
               float[] afloat3 = this.verts.get(i + Math.min(j, this.groupSize - 1));
               afloat[j][0] = afloat3[0];
               afloat[j][1] = afloat3[1];
               afloat[j][2] = afloat3[2];
               afloat1[j][0] = afloat3[3];
               afloat1[j][1] = afloat3[4];
               if (afloat2 == null && afloat3[8] != 0.0F) {
                  afloat2 = new float[]{afloat3[5], afloat3[6], afloat3[7]};
               }
            }

            TexKey texkey = this.resolver.resolve(afloat1);
            if (texkey != null) {
               if (afloat2 == null) {
                  afloat2 = computeNormal(afloat);
               }

               this.owner.mesh.addQuad(texkey, afloat, afloat1, afloat2);
               this.owner.captured++;
            }
         }

         this.verts.clear();
      }

      private static float[] computeNormal(float[][] p) {
         float f = p[1][0] - p[0][0];
         float f1 = p[1][1] - p[0][1];
         float f2 = p[1][2] - p[0][2];
         float f3 = p[2][0] - p[0][0];
         float f4 = p[2][1] - p[0][1];
         float f5 = p[2][2] - p[0][2];
         float f6 = f1 * f5 - f2 * f4;
         float f7 = f2 * f3 - f * f5;
         float f8 = f * f4 - f1 * f3;
         float f9 = (float)Math.sqrt(f6 * f6 + f7 * f7 + f8 * f8);
         return f9 < 1.0E-8F ? new float[]{0.0F, 1.0F, 0.0F} : new float[]{f6 / f9, f7 / f9, f8 / f9};
      }
   }

   private interface QuadResolver {
      TexKey resolve(float[][] var1);
   }
}
