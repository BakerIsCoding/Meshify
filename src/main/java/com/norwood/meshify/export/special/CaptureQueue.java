package com.norwood.meshify.export.special;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
*///?} elif >=26.2 {
/*import com.mojang.blaze3d.PrimitiveTopology;
*///?} else
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.norwood.meshify.export.BakedQuads;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.RegionExporter;
import com.norwood.meshify.export.TextureKey;
import com.norwood.meshify.mixin.RenderSetupAccessor;
import com.norwood.meshify.mixin.RenderTypeNameAccessor;
import com.norwood.meshify.mixin.RenderTypeStateAccessor;
import com.norwood.meshify.mixin.TextureAtlasAccessor;
import com.norwood.meshify.mixin.TextureBindingAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
//? if >=26.2 <26.4 {
/*import java.util.function.Function;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
*///?}
//? if >=26.1 <26.4
//import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
//? if >=26.1 {
/*import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
*///?} else {
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
//?}
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState;
import net.minecraft.client.renderer.entity.state.EntityRenderState.ShadowPiece;
//? if <1.21.11
/*import net.minecraft.client.renderer.entity.state.HitboxesRenderState;*/
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
//? if >=1.21.11
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//? if >=26.2 {
/*import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.phys.shapes.VoxelShape;
*///?}
//? if >=26.3 {
/*import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
*///?}
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

// Stands in for the game's render queue, so block-entity and entity renderers draw into the mesh instead.
final class CaptureQueue implements SubmitNodeCollector {
   private static final int MAP_SIZE = 128;
   private final MeshBuilder mesh;
   private final Minecraft client;
   private final List<String> warnings;
   private final Map<Identifier, Map<Identifier, TextureAtlasSprite>> atlasCache = new HashMap<>();
   int capturedQuads;
   Integer itemFrameMapId;

   CaptureQueue(MeshBuilder mesh, Minecraft client, List<String> warnings) {
      this.mesh = mesh;
      this.client = client;
      this.warnings = warnings;
   }

   @Override
   public OrderedSubmitNodeCollector order(int order) {
      return this;
   }

   // A consumer that turns the layer's vertices into quads, or null for overlay layers that are not geometry.
   private CaptureQueue.CapturingConsumer capturingConsumer(RenderType layer, TextureAtlasSprite sprite, int tint) {
      String name = ((RenderTypeNameAccessor)layer).meshify$getName().toLowerCase(Locale.ROOT);
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

      //? if >=26.2 {
      /*return new CaptureQueue.CapturingConsumer(this, resolver, layer.primitiveTopology() == PrimitiveTopology.TRIANGLES);
      *///?} else
      return new CaptureQueue.CapturingConsumer(this, resolver, layer.mode() == Mode.TRIANGLES);
   }

   // Finds the sprite an atlas quad samples by the centre of its UVs, and restates the UVs within that sprite.
   private TextureKey resolveAtlas(Identifier atlasId, float[][] uv, int tint) {
      Map<Identifier, TextureAtlasSprite> sprites = this.atlasCache.computeIfAbsent(atlasId, id -> {
         if (this.client.getTextureManager().getTexture(id) instanceof TextureAtlas atlas) {
            Map<Identifier, TextureAtlasSprite> all = ((TextureAtlasAccessor)atlas).meshify$getSprites();
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

      for (TextureAtlasSprite sprite : sprites.values()) {
         if (centreU >= sprite.getU0() && centreU <= sprite.getU1() && centreV >= sprite.getV0() && centreV <= sprite.getV1()) {
            for (int k = 0; k < uv.length; k++) {
               uv[k] = TextureKey.spriteUv(sprite, uv[k][0], uv[k][1]);
            }

            return TextureKey.sprite(sprite, tint);
         }
      }

      this.warnOnce("no sprite found for UVs on " + atlasId);
      return null;
   }

   // Maps are drawn from their pixel data, found by the id in the texture name or else the item frame holding them.
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

      if (mapId < 0 || this.client.level == null) {
         this.warnOnce("map texture without resolvable map id skipped: " + textureId);
         return null;
      }

      MapItemSavedData state = this.client.level.getMapData(new MapId(mapId));
      if (state == null) {
         this.warnOnce("map state not available: map id " + mapId);
         return null;
      }

      byte[] colors = state.colors.clone();
      TextureKey texture = TextureKey.generated("map_" + mapId, client -> {
         NativeImage image = new NativeImage(MAP_SIZE, MAP_SIZE, false);

         for (int i = 0; i < MAP_SIZE * MAP_SIZE; i++) {
            image.setPixel(i % MAP_SIZE, i / MAP_SIZE, MapColor.getColorFromPackedId(colors[i] & 0xFF));
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

   // Renderers pass 0 as well as white for an uncoloured layer.
   private static int normalizeRendererTint(int argb) {
      return argb == 0 ? -1 : TextureKey.normalizeTint(argb);
   }

   private void warnOnce(String message) {
      if (!this.warnings.contains(message)) {
         this.warnings.add(message);
      }
   }

   private static Identifier textureOf(RenderType layer) {
      try {
         //? if >=1.21.11 {
         RenderSetup setup = ((RenderTypeStateAccessor)layer).meshify$getState();

         for (Object texture : ((RenderSetupAccessor)(Object)setup).meshify$getTextures().values()) {
            if (texture instanceof TextureBindingAccessor binding && binding.meshify$getLocation() != null) {
               return binding.meshify$getLocation();
            }
         }
         //?} else {
         /*if (layer instanceof RenderType.CompositeRenderType composite) {
            RenderType.CompositeState state = ((RenderTypeStateAccessor)(Object)composite).meshify$getState();
            return ((TextureBindingAccessor)((RenderSetupAccessor)(Object)state).meshify$getTextureState()).meshify$getLocation().orElse(null);
         }
         *///?}
      } catch (Exception ignored) {
         // A layer built some other way; the caller warns and skips it.
      }

      return null;
   }

   private void capture(TextureKey texture, float[][] positions, float[][] uv, float[] normal) {
      this.mesh.addQuad(texture, positions, uv, normal);
      this.capturedQuads++;
   }

   // From 26.2 the model part submits default to submitModel, which captures them.
   //? if <26.2 {
   @Override
   public void submitModelPart(
      ModelPart part,
      PoseStack matrices,
      RenderType renderLayer,
      int light,
      int overlay,
      TextureAtlasSprite sprite,
      boolean sheeted,
      boolean hasGlint,
      int tintedColor,
      CrumblingOverlay crumblingOverlay,
      int outlineColor
   ) {
      CaptureQueue.CapturingConsumer consumer = this.capturingConsumer(renderLayer, sprite, tintedColor);
      if (consumer != null) {
         part.render(matrices, consumer, light, overlay);
         consumer.finish();
      }
   }
   //?}

   @Override
   public <S> void submitModel(
      Model<? super S> model,
      S state,
      PoseStack matrices,
      RenderType renderLayer,
      int light,
      int overlay,
      int tintedColor,
      //? if >=26.3 {
      /*UvMapping uvMapping,
      int outlineColor
      *///?} else {
      TextureAtlasSprite sprite,
      int outlineColor,
      CrumblingOverlay crumblingOverlay
      //?}
   ) {
      //? if >=26.3 {
      /*// Armor trims map through a generated paletted texture that is not a resource, so they are left out.
      if (uvMapping != null && !(uvMapping instanceof TextureAtlasSprite)) {
         this.warnOnce("paletted texture (armor trim) skipped");
         return;
      }

      TextureAtlasSprite sprite = (TextureAtlasSprite)uvMapping;
      *///?}
      CaptureQueue.CapturingConsumer consumer = this.capturingConsumer(renderLayer, sprite, tintedColor);
      if (consumer != null) {
         model.setupAnim(state);
         model.renderToBuffer(matrices, consumer, light, overlay, -1);
         consumer.finish();
      }
   }

   //? if >=26.1 {
   /*@Override
   public void submitBlockModel(
      PoseStack matrices, RenderType renderLayer, List<BlockStateModelPart> parts, int[] tintLayers, int light, int overlay, int outlineColor
   ) {
      this.emitParts(matrices, parts, tintLayers);
   }

   private void emitParts(PoseStack matrices, List<BlockStateModelPart> parts, int[] tintLayers) {
      for (BlockStateModelPart part : parts) {
         for (Direction face : RegionExporter.QUAD_FACES) {
            for (BakedQuad quad : part.getQuads(face)) {
               this.emitBakedQuad(matrices, quad, layerTint(tintLayers, BakedQuads.tintIndex(quad)));
            }
         }
      }
   }
   *///?} else {
   @Override
   public void submitBlockModel(
      PoseStack matrices, RenderType renderLayer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor
   ) {
      int rgb = (int)(r * 255.0F) << 16 | (int)(g * 255.0F) << 8 | (int)(b * 255.0F);
      this.emitBakedModel(matrices, model, rgb);
   }

   @Override
   public void submitBlock(PoseStack matrices, BlockState state, int light, int overlay, int outlineColor) {
      this.emitBakedModel(matrices, this.client.getBlockRenderer().getBlockModelShaper().getBlockModel(state), -1);
   }

   private void emitBakedModel(PoseStack matrices, BlockStateModel model, int tint) {
      // A fixed seed, so a model with random variants comes out the same every export.
      for (BlockModelPart part : model.collectParts(RandomSource.create(42L))) {
         for (Direction face : RegionExporter.QUAD_FACES) {
            for (BakedQuad quad : part.getQuads(face)) {
               this.emitBakedQuad(matrices, quad, tint);
            }
         }
      }
   }
   //?}

   //? if >=26.2 <26.4 {
   /*// With Fabric API's renderer loaded, block models (the item frame's frame among them) arrive here instead.
   @Override
   public void submitBlockModel(
      PoseStack matrices,
      Function<ChunkSectionLayer, RenderType> renderTypes,
      boolean translucent,
      List<BlockStateModelPart> parts,
      Mesh mesh,
      int[] tintLayers,
      int light,
      int overlay,
      int outlineColor
   ) {
      this.emitFabricBlockModel(matrices, parts, mesh, tintLayers);
   }
   *///?} elif >=26.1 <26.2 {
   /*// With Fabric API's renderer loaded, block models (the item frame's frame among them) arrive here instead.
   @Override
   public void submitBlockModel(
      PoseStack matrices, RenderType renderLayer, List<BlockStateModelPart> parts, Mesh mesh, int[] tintLayers, int light, int overlay, int outlineColor
   ) {
      this.emitFabricBlockModel(matrices, parts, mesh, tintLayers);
   }
   *///?}
   //? if >=26.1 <26.4 {
   /*private void emitFabricBlockModel(PoseStack matrices, List<BlockStateModelPart> parts, Mesh mesh, int[] tintLayers) {
      this.emitParts(matrices, parts, tintLayers);
      if (mesh == null) {
         return;
      }

      // Mesh quads carry block atlas UVs but no sprite, so the sprite is looked up by UV.
      Matrix4f matrix = matrices.last().pose();
      mesh.forEach(quad -> {
         float[][] positions = new float[4][];
         float[][] uv = new float[4][];

         for (int k = 0; k < 4; k++) {
            Vector4f vertex = matrix.transform(new Vector4f(quad.x(k), quad.y(k), quad.z(k), 1.0F));
            positions[k] = new float[]{vertex.x(), vertex.y(), vertex.z()};
            uv[k] = new float[]{quad.u(k), quad.v(k)};
         }

         TextureKey texture = this.resolveAtlas(TextureAtlas.LOCATION_BLOCKS, uv, layerTint(tintLayers, quad.tintIndex()));
         if (texture != null) {
            Vector3f normal = matrices.last().transformNormal(new Vector3f(quad.faceNormal()), new Vector3f());
            this.capture(texture, positions, uv, new float[]{normal.x(), normal.y(), normal.z()});
         }
      });
   }
   *///?}

   // The block breaking overlay is not geometry.
   //? if >=26.3 {
   /*@Override
   public void submitBreakingBlockModel(PoseStack matrices, List<BlockStateModelPart> parts, int progress, boolean isBlockTranslucent) {
   }
   *///?} elif >=26.2 {
   /*@Override
   public void submitBreakingBlockModel(PoseStack matrices, List<BlockStateModelPart> parts, int progress) {
   }
   *///?} elif >=26.1 {
   /*@Override
   public void submitBreakingBlockModel(PoseStack matrices, BlockStateModel model, long seed, int progress) {
   }
   *///?}

   @Override
   public void submitItem(
      PoseStack matrices,
      ItemDisplayContext displayContext,
      int light,
      int overlay,
      int outlineColors,
      int[] tintLayers,
      //? if >=26.3 {
      /*ItemQuads quads,
      *///?} else
      List<BakedQuad> quads,
      //? if <26.1
      RenderType renderLayer,
      FoilType glintType
   ) {
      //? if >=26.3 {
      /*for (BakedQuad quad : quads.all()) {
      *///?} else
      for (BakedQuad quad : quads) {
         this.emitBakedQuad(matrices, quad, layerTint(tintLayers, BakedQuads.tintIndex(quad)));
      }
   }

   // The colour the renderer gave the quad's tint layer, or -1 when the quad is not tinted.
   private static int layerTint(int[] tintLayers, int layer) {
      return layer >= 0 && tintLayers != null && layer < tintLayers.length ? tintLayers[layer] : -1;
   }

   private void emitBakedQuad(PoseStack matrices, BakedQuad quad, int tint) {
      TextureAtlasSprite sprite = BakedQuads.sprite(quad);
      if (sprite == null) {
         return;
      }

      Matrix4f matrix = matrices.last().pose();
      float[][] positions = new float[4][3];
      float[][] uv = new float[4][];
      Vector4f vertex = new Vector4f();

      for (int k = 0; k < 4; k++) {
         Vector3fc local = BakedQuads.position(quad, k);
         vertex.set(local.x(), local.y(), local.z(), 1.0F);
         matrix.transform(vertex);
         positions[k][0] = vertex.x();
         positions[k][1] = vertex.y();
         positions[k][2] = vertex.z();
         uv[k] = BakedQuads.spriteUv(quad, sprite, k);
      }

      Vector3f normal = matrices.last().transformNormal(quad.direction().getUnitVec3f(), new Vector3f());
      this.capture(TextureKey.sprite(sprite, tint), positions, uv, new float[]{normal.x(), normal.y(), normal.z()});
   }

   @Override
   public void submitCustomGeometry(PoseStack matrices, RenderType renderLayer, CustomGeometryRenderer customRenderer) {
      CaptureQueue.CapturingConsumer consumer = this.capturingConsumer(renderLayer, null, -1);
      if (consumer != null) {
         customRenderer.render(matrices.last(), consumer);
         consumer.finish();
      }
   }

   //? if >=26.2 {
   /*@Override
   public void submitQuadParticleGroup(QuadParticleRenderState particles) {
   }
   *///?} else {
   @Override
   public void submitParticleGroup(ParticleGroupRenderer customRenderer) {
   }
   //?}

   @Override
   public void submitText(
      PoseStack matrices,
      float x,
      float y,
      FormattedCharSequence text,
      boolean dropShadow,
      DisplayMode layerType,
      int light,
      int color,
      int backgroundColor,
      int outlineColor
   ) {
      this.warnOnce("text rendering not captured (sign text uses the sign exporter path)");
   }

   @Override
   public void submitNameTag(
      PoseStack matrices,
      Vec3 nameLabelPos,
      int y,
      Component label,
      boolean notSneaking,
      int light,
      //? if <26.2
      double squaredDistanceToCamera,
      CameraRenderState cameraState
   ) {
   }

   //? if <1.21.11 {
   /*@Override
   public void submitHitbox(PoseStack matrices, EntityRenderState renderState, HitboxesRenderState hitboxes) {
   }
   *///?}

   @Override
   public void submitShadow(PoseStack matrices, float shadowRadius, List<ShadowPiece> shadowPieces) {
   }

   @Override
   public void submitFlame(PoseStack matrices, EntityRenderState renderState, Quaternionf rotation) {
   }

   @Override
   public void submitLeash(PoseStack matrices, LeashState leashData) {
   }

   //? if >=26.2 {
   /*@Override
   public void submitMovingBlock(PoseStack matrices, MovingBlockRenderState state, int outlineColor) {
   }

   @Override
   public void submitShapeOutline(PoseStack matrices, VoxelShape shape, RenderType renderLayer, int color, float width, boolean afterTerrain) {
   }

   @Override
   public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
   }
   *///?} else {
   @Override
   public void submitMovingBlock(PoseStack matrices, MovingBlockRenderState state) {
   }
   //?}

   //? if >=26.3 {
   /*@Override
   public <S> void submitCrumblingOverlay(
      Model<? super S> model, S state, PoseStack matrices, RenderType renderLayer, int light, int overlay, int tintedColor, CrumblingOverlay crumblingOverlay
   ) {
   }

   @Override
   public void submitTextBackground(PoseStack matrices, float x0, float y0, float x1, float y1, int color, DisplayMode displayMode, int light) {
   }
   *///?}

   private static final class CapturingConsumer implements VertexConsumer {
      // Per vertex: x, y, z, u, v, the normal's x, y, z, then 1 once a normal was given.
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
      public VertexConsumer addVertex(float x, float y, float z) {
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
      public VertexConsumer setUv(float u, float v) {
         if (this.currentVertex != null) {
            this.currentVertex[U] = u;
            this.currentVertex[V] = v;
         }

         return this;
      }

      @Override
      public VertexConsumer setNormal(float x, float y, float z) {
         if (this.currentVertex != null) {
            this.currentVertex[NORMAL] = x;
            this.currentVertex[NORMAL + 1] = y;
            this.currentVertex[NORMAL + 2] = z;
            this.currentVertex[HAS_NORMAL] = 1.0F;
         }

         return this;
      }

      @Override
      public VertexConsumer setColor(int red, int green, int blue, int alpha) {
         return this;
      }

      @Override
      public VertexConsumer setColor(int argb) {
         return this;
      }

      @Override
      public VertexConsumer setUv1(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer setUv2(int u, int v) {
         return this;
      }

      //? if >=1.21.11
      @Override
      public VertexConsumer setLineWidth(float width) {
         return this;
      }

      //? if >=26.3
      //@Override
      public VertexConsumer setUv3(float u, float v) {
         return this;
      }

      // Groups the vertices into quads; a triangle repeats its last corner to make one.
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
      // The texture for a quad, or null to drop it; may rewrite uv into that texture's space.
      TextureKey resolve(float[][] uv);
   }
}
