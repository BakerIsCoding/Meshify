package com.norwood.meshify.export.special;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.RegionExporter;
import com.norwood.meshify.export.TextureKey;
import com.norwood.meshify.mixin.RenderLayerAccessor;
import com.norwood.meshify.mixin.RenderSetupAccessor;
import com.norwood.meshify.mixin.SpriteAtlasTextureAccessor;
import com.norwood.meshify.mixin.TextureSpecAccessor;
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

/** Stands in for the game's render queue, so block-entity and entity renderers draw into the mesh instead. */
final class CaptureQueue implements OrderedRenderCommandQueue {
   private static final int MAP_SIZE = 128;
   private final MeshBuilder mesh;
   private final MinecraftClient client;
   private final List<String> warnings;
   private final Map<Identifier, Map<Identifier, Sprite>> atlasCache = new HashMap<>();
   int capturedQuads;
   Integer itemFrameMapId;

   CaptureQueue(MeshBuilder mesh, MinecraftClient client, List<String> warnings) {
      this.mesh = mesh;
      this.client = client;
      this.warnings = warnings;
   }

   @Override
   public RenderCommandQueue getBatchingQueue(int order) {
      return this;
   }

   /** A consumer that turns the layer's vertices into quads, or null for overlay layers that are not geometry. */
   private CaptureQueue.CapturingConsumer capturingConsumer(RenderLayer layer, Sprite sprite, int tint) {
      String name = ((RenderLayerAccessor)layer).meshify$getName().toLowerCase(Locale.ROOT);
      if (name.contains("glint") || name.contains("outline") || name.contains("crumbling") || name.contains("shadow")) {
         return null;
      }

      int rgb = normalizeRendererTint(tint);
      CaptureQueue.QuadResolver resolver;
      if (sprite != null) {
         TextureKey texture = TextureKey.sprite(sprite, rgb);
         resolver = uv -> texture;
      } else {
         Identifier textureId = textureOf(layer);
         if (textureId == null) {
            this.warnOnce("BER/entity layer without resolvable texture skipped: " + name);
            return null;
         }

         CaptureQueue.QuadResolver mapResolver = this.tryMapResolver(textureId);
         if (mapResolver != null) {
            resolver = mapResolver;
         } else if (textureId.getPath().startsWith("textures/atlas/")) {
            resolver = uv -> this.resolveAtlas(textureId, uv, rgb);
         } else {
            TextureKey texture = TextureKey.resource(textureId, rgb);
            resolver = uv -> texture;
         }
      }

      return new CaptureQueue.CapturingConsumer(this, resolver, layer.getDrawMode() == DrawMode.TRIANGLES);
   }

   /** Finds the sprite an atlas quad samples by the centre of its UVs, and restates the UVs within that sprite. */
   private TextureKey resolveAtlas(Identifier atlasId, float[][] uv, int tint) {
      Map<Identifier, Sprite> sprites = this.atlasCache.computeIfAbsent(atlasId, id -> {
         if (this.client.getTextureManager().getTexture(id) instanceof SpriteAtlasTexture atlas) {
            Map<Identifier, Sprite> all = ((SpriteAtlasTextureAccessor)atlas).meshify$getSprites();
            return all != null ? all : Map.of();
         }

         return Map.of();
      });
      if (sprites.isEmpty()) {
         this.warnOnce("atlas not resolvable, quads skipped: " + atlasId);
         return null;
      }

      float centreU = (uv[0][0] + uv[1][0] + uv[2][0] + uv[3][0]) / 4.0F;
      float centreV = (uv[0][1] + uv[1][1] + uv[2][1] + uv[3][1]) / 4.0F;

      for (Sprite sprite : sprites.values()) {
         if (centreU >= sprite.getMinU() && centreU <= sprite.getMaxU() && centreV >= sprite.getMinV() && centreV <= sprite.getMaxV()) {
            for (int k = 0; k < uv.length; k++) {
               uv[k] = TextureKey.spriteUv(sprite, uv[k][0], uv[k][1]);
            }

            return TextureKey.sprite(sprite, tint);
         }
      }

      this.warnOnce("no sprite found for UVs on " + atlasId);
      return null;
   }

   /** Maps are drawn from their pixel data, found by the id in the texture name or else the item frame holding them. */
   private CaptureQueue.QuadResolver tryMapResolver(Identifier textureId) {
      String path = textureId.getPath();
      boolean mapTexture = path.startsWith("map/");
      if (!mapTexture && !path.contains("map_atlas")) {
         return null;
      }

      int mapId = -1;
      if (mapTexture) {
         try {
            mapId = Integer.parseInt(path.replaceAll("[^0-9]", ""));
         } catch (NumberFormatException ignored) {
            // No digits in the name: fall back to the item frame's map.
         }
      }

      if (mapId < 0 && this.itemFrameMapId != null) {
         mapId = this.itemFrameMapId;
      }

      if (mapId < 0 || this.client.world == null) {
         this.warnOnce("map texture without resolvable map id skipped: " + textureId);
         return null;
      }

      MapState state = this.client.world.getMapState(new MapIdComponent(mapId));
      if (state == null) {
         this.warnOnce("map state not available: map id " + mapId);
         return null;
      }

      byte[] colors = state.colors.clone();
      TextureKey texture = TextureKey.generated("map_" + mapId, client -> {
         NativeImage image = new NativeImage(MAP_SIZE, MAP_SIZE, false);

         for (int i = 0; i < MAP_SIZE * MAP_SIZE; i++) {
            image.setColorArgb(i % MAP_SIZE, i / MAP_SIZE, MapColor.getRenderColor(colors[i] & 0xFF));
         }

         return image;
      });
      // The map fills the whole quad, whichever part of the map atlas its UVs pointed at.
      return uv -> {
         for (int axis = 0; axis < 2; axis++) {
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;

            for (float[] corner : uv) {
               min = Math.min(min, corner[axis]);
               max = Math.max(max, corner[axis]);
            }

            float span = max - min;

            for (float[] corner : uv) {
               corner[axis] = span > 1.0E-6F ? (corner[axis] - min) / span : 0.0F;
            }
         }

         return texture;
      };
   }

   /** Renderers pass 0 as well as white for an uncoloured layer. */
   private static int normalizeRendererTint(int argb) {
      return argb == 0 ? -1 : TextureKey.normalizeTint(argb);
   }

   private void warnOnce(String message) {
      if (!this.warnings.contains(message)) {
         this.warnings.add(message);
      }
   }

   private static Identifier textureOf(RenderLayer layer) {
      try {
         RenderSetup setup = ((RenderLayerAccessor)layer).meshify$getRenderSetup();
         Map<?, ?> textures = ((RenderSetupAccessor)(Object)setup).meshify$getTextures();

         for (Object texture : textures.values()) {
            if (texture instanceof TextureSpecAccessor spec && spec.meshify$getLocation() != null) {
               return spec.meshify$getLocation();
            }
         }
      } catch (Exception ignored) {
         // A layer built some other way; the caller warns and skips it.
      }

      return null;
   }

   private void capture(TextureKey texture, float[][] positions, float[][] uv, float[] normal) {
      this.mesh.addQuad(texture, positions, uv, normal);
      this.capturedQuads++;
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
      int outlineColor
   ) {
      CaptureQueue.CapturingConsumer consumer = this.capturingConsumer(renderLayer, sprite, tintedColor);
      if (consumer != null) {
         part.render(matrices, consumer, light, overlay);
         consumer.finish();
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
      CaptureQueue.CapturingConsumer consumer = this.capturingConsumer(renderLayer, sprite, tintedColor);
      if (consumer != null) {
         model.setAngles(state);
         model.render(matrices, consumer, light, overlay);
         consumer.finish();
      }
   }

   @Override
   public void submitBlockStateModel(
      MatrixStack matrices, RenderLayer renderLayer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor
   ) {
      int rgb = (int)(r * 255.0F) << 16 | (int)(g * 255.0F) << 8 | (int)(b * 255.0F);
      this.emitBakedModel(matrices, model, rgb);
   }

   @Override
   public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor) {
      this.emitBakedModel(matrices, this.client.getBlockRenderManager().getModels().getModel(state), -1);
   }

   private void emitBakedModel(MatrixStack matrices, BlockStateModel model, int tint) {
      // A fixed seed, so a model with random variants comes out the same every export.
      for (BlockModelPart part : model.getParts(Random.create(42L))) {
         for (Direction face : RegionExporter.QUAD_FACES) {
            for (BakedQuad quad : part.getQuads(face)) {
               this.emitBakedQuad(matrices, quad, tint);
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
      for (BakedQuad quad : quads) {
         int layer = quad.tintIndex();
         this.emitBakedQuad(matrices, quad, layer >= 0 && tintLayers != null && layer < tintLayers.length ? tintLayers[layer] : -1);
      }
   }

   private void emitBakedQuad(MatrixStack matrices, BakedQuad quad, int tint) {
      Sprite sprite = quad.sprite();
      if (sprite == null) {
         return;
      }

      Matrix4f matrix = matrices.peek().getPositionMatrix();
      float[][] positions = new float[4][3];
      float[][] uv = new float[4][];
      Vector4f vertex = new Vector4f();

      for (int k = 0; k < 4; k++) {
         Vector3fc local = quad.getPosition(k);
         vertex.set(local.x(), local.y(), local.z(), 1.0F);
         matrix.transform(vertex);
         positions[k][0] = vertex.x();
         positions[k][1] = vertex.y();
         positions[k][2] = vertex.z();
         long texcoords = quad.getTexcoords(k);
         uv[k] = TextureKey.spriteUv(sprite, Vector2f.getX(texcoords), Vector2f.getY(texcoords));
      }

      Vector3f normal = matrices.peek().transformNormal(quad.face().getFloatVector(), new Vector3f());
      this.capture(TextureKey.sprite(sprite, tint), positions, uv, new float[]{normal.x(), normal.y(), normal.z()});
   }

   @Override
   public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, Custom customRenderer) {
      CaptureQueue.CapturingConsumer consumer = this.capturingConsumer(renderLayer, null, -1);
      if (consumer != null) {
         customRenderer.render(matrices.peek(), consumer);
         consumer.finish();
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
      /** Per vertex: x, y, z, u, v, the normal's x, y, z, then 1 once a normal was given. */
      private static final int U = 3;
      private static final int V = 4;
      private static final int NORMAL = 5;
      private static final int HAS_NORMAL = 8;
      private static final int VERTEX_SIZE = 9;
      private final CaptureQueue owner;
      private final CaptureQueue.QuadResolver resolver;
      private final int groupSize;
      private final ArrayList<float[]> vertices = new ArrayList<>();
      private float[] currentVertex;

      CapturingConsumer(CaptureQueue owner, CaptureQueue.QuadResolver resolver, boolean triangles) {
         this.owner = owner;
         this.resolver = resolver;
         this.groupSize = triangles ? 3 : 4;
      }

      @Override
      public VertexConsumer vertex(float x, float y, float z) {
         this.flushVertex();
         this.currentVertex = new float[VERTEX_SIZE];
         this.currentVertex[0] = x;
         this.currentVertex[1] = y;
         this.currentVertex[2] = z;
         return this;
      }

      private void flushVertex() {
         if (this.currentVertex != null) {
            this.vertices.add(this.currentVertex);
            this.currentVertex = null;
         }
      }

      @Override
      public VertexConsumer texture(float u, float v) {
         if (this.currentVertex != null) {
            this.currentVertex[U] = u;
            this.currentVertex[V] = v;
         }

         return this;
      }

      @Override
      public VertexConsumer normal(float x, float y, float z) {
         if (this.currentVertex != null) {
            this.currentVertex[NORMAL] = x;
            this.currentVertex[NORMAL + 1] = y;
            this.currentVertex[NORMAL + 2] = z;
            this.currentVertex[HAS_NORMAL] = 1.0F;
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

      /** Groups the vertices into quads; a triangle repeats its last corner to make one. */
      void finish() {
         this.flushVertex();

         for (int first = 0; first + this.groupSize <= this.vertices.size(); first += this.groupSize) {
            float[][] positions = new float[4][3];
            float[][] uv = new float[4][2];
            float[] normal = null;

            for (int k = 0; k < 4; k++) {
               float[] vertex = this.vertices.get(first + Math.min(k, this.groupSize - 1));
               positions[k][0] = vertex[0];
               positions[k][1] = vertex[1];
               positions[k][2] = vertex[2];
               uv[k][0] = vertex[U];
               uv[k][1] = vertex[V];
               if (normal == null && vertex[HAS_NORMAL] != 0.0F) {
                  normal = new float[]{vertex[NORMAL], vertex[NORMAL + 1], vertex[NORMAL + 2]};
               }
            }

            TextureKey texture = this.resolver.resolve(uv);
            if (texture != null) {
               this.owner.capture(texture, positions, uv, normal != null ? normal : MeshBuilder.faceNormal(positions));
            }
         }

         this.vertices.clear();
      }
   }

   private interface QuadResolver {
      /** The texture for a quad, or null to drop it; may rewrite {@code uv} into that texture's space. */
      TextureKey resolve(float[][] uv);
   }
}
