package com.norwood.meshify.export;

import com.norwood.meshify.export.special.BannerExporter;
import com.norwood.meshify.export.special.BedExporter;
import com.norwood.meshify.export.special.BellExporter;
import com.norwood.meshify.export.special.BlockEntityCaptureExporter;
import com.norwood.meshify.export.special.ChestExporter;
import com.norwood.meshify.export.special.ConduitExporter;
import com.norwood.meshify.export.special.DecoratedPotExporter;
import com.norwood.meshify.export.special.EntityCaptureExporter;
import com.norwood.meshify.export.special.FluidExporter;
import com.norwood.meshify.export.special.ShulkerExporter;
import com.norwood.meshify.export.special.SignExporter;
import com.norwood.meshify.export.special.SkullExporter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
//? if >=26.1
//import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=26.1 {
/*import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
*///?} else {
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModelPart;
//?}
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ConduitBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public final class RegionExporter {
   public static final long MAX_VOLUME = 16_000_000L;
   // The cull faces a model part files its quads under, and null for the ones it never culls.
   public static final Direction[] QUAD_FACES = {
      null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
   };

   private RegionExporter() {
   }

   public static Path exportRoot(Minecraft client) {
      return client.gameDirectory.toPath().resolve("meshify");
   }

   public static Path outputDir(Minecraft client, String name) {
      return exportRoot(client).resolve(name);
   }

   // Writes a finished scan to disk off the client thread; onStage runs before each step and may throw to abort.
   public static RegionExporter.Result finish(Minecraft client, RegionExporter.Scan scan, Consumer<ExportStage> onStage) throws IOException {
      ExportOptions options = scan.options();
      MeshBuilder mesh = scan.mesh();
      List<String> warnings = scan.warnings();
      BakeCollector baker = scan.baker();

      // The bake sources are native images, so they are freed however this ends, cancellation included.
      try {
         BakeCollector.Stats bakeStats = null;
         if (baker != null && !baker.isEmpty()) {
            onStage.accept(ExportStage.BAKING);
            bakeStats = baker.flush(mesh, warnings);
         }

         int facesBeforeOptimize = mesh.faceCount();
         MeshBuilder.OptimizationStats optimizationStats = null;
         if (options.weldVertices()) {
            onStage.accept(ExportStage.OPTIMIZING);
            optimizationStats = mesh.optimize();
         }

         Path outputDir = outputDir(client, options.name());
         ObjWriter.write(client, outputDir, options, mesh, warnings, onStage);
         RegionExporter.Result result = new RegionExporter.Result(
            scan.exportedBlocks(), scan.exportedEntities(), mesh.faceCount(), scan.skippedTypes(), warnings, outputDir, bakeStats, optimizationStats, facesBeforeOptimize
         );
         writeInfoFile(scan, result);
         return result;
      } finally {
         if (baker != null) {
            baker.close();
         }
      }
   }

   private static boolean neighborFullyOccludes(BlockState neighbor, Direction face) {
      if (neighbor.isAir() || !neighbor.canOcclude()) {
         return false;
      } else if (neighbor.isSolidRender()) {
         return true;
      }

      Direction side = face.getOpposite();
      try {
         return Block.isFaceFull(neighbor.getFaceOcclusionShape(side), side);
      } catch (Exception exception) {
         return false;
      }
   }

   public static int countUnloadedChunks(ClientLevel world, BlockPos min, BlockPos max) {
      int unloaded = 0;
      int lastX = SectionPos.blockToSectionCoord(max.getX());
      int lastZ = SectionPos.blockToSectionCoord(max.getZ());

      for (int x = SectionPos.blockToSectionCoord(min.getX()); x <= lastX; x++) {
         for (int z = SectionPos.blockToSectionCoord(min.getZ()); z <= lastZ; z++) {
            if (!world.getChunkSource().hasChunk(x, z)) {
               unloaded++;
            }
         }
      }

      return unloaded;
   }

   // Smallest box around the non-air blocks, if any; unloaded chunks read as air, so check them first.
   public static Optional<BoundingBox> solidBounds(ClientLevel world, BlockPos min, BlockPos max) {
      // shortcut: reads every block on the client thread (~1 s at the limit); skip empty chunk sections if it stalls.
      int loX = Integer.MAX_VALUE;
      int loY = Integer.MAX_VALUE;
      int loZ = Integer.MAX_VALUE;
      int hiX = Integer.MIN_VALUE;
      int hiY = Integer.MIN_VALUE;
      int hiZ = Integer.MIN_VALUE;

      for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
         if (!world.getBlockState(pos).isAir()) {
            loX = Math.min(loX, pos.getX());
            loY = Math.min(loY, pos.getY());
            loZ = Math.min(loZ, pos.getZ());
            hiX = Math.max(hiX, pos.getX());
            hiY = Math.max(hiY, pos.getY());
            hiZ = Math.max(hiZ, pos.getZ());
         }
      }

      return loX > hiX ? Optional.empty() : Optional.of(new BoundingBox(loX, loY, loZ, hiX, hiY, hiZ));
   }

   private static void writeInfoFile(RegionExporter.Scan scan, RegionExporter.Result result) {
      ExportOptions options = scan.options();
      int faces = result.facesEmitted();
      BakeCollector.Stats bakeStats = result.bakeStats();
      MeshBuilder.OptimizationStats optimizationStats = result.optimizationStats();
      StringBuilder info = new StringBuilder();
      info.append("meshify export: ").append(options.name()).append('\n');
      info.append("region: ").append(scan.min().toShortString()).append(" .. ").append(scan.max().toShortString()).append('\n');
      info.append("textures: ").append(options.mergeAtlas() ? "single atlas" : "separate files").append(", scale x").append(options.textureScale()).append('\n');
      info.append("options: ").append(options.aggressiveCull() ? "aggressive-cull " : "").append(options.weldVertices() ? "weld " : "").append('\n');
      info.append("blocks exported: ").append(result.blocksExported()).append(", entities: ").append(result.entitiesExported()).append(", faces: ").append(faces).append('\n');
      if (bakeStats != null) {
         info.append(
            String.format(
               Locale.ROOT,
               "bake: %d faces -> %d quads (%.1f%% reduction, %d sheet(s), %.1f Mpx)%n",
               bakeStats.facesConsumed(),
               bakeStats.quadsEmitted(),
               bakeStats.reductionPercent(),
               bakeStats.sheets(),
               bakeStats.megapixels()
            )
         );
      }

      if (optimizationStats != null) {
         info.append(
            String.format(
               Locale.ROOT,
               "optimize: %d -> %d faces (%d degenerate, %d duplicate removed, weld grid 1/%d block)%n",
               result.facesBeforeOptimize(),
               faces,
               optimizationStats.degenerateRemoved(),
               optimizationStats.duplicateRemoved(),
               optimizationStats.weldGrid()
            )
         );
      }

      if (!result.skippedTypes().isEmpty()) {
         info.append("\nskipped block types (no baked geometry / unsupported):\n");

         for (String type : result.skippedTypes()) {
            info.append("  - ").append(type).append('\n');
         }
      }

      if (!result.warnings().isEmpty()) {
         info.append("\nwarnings:\n");

         for (String warning : result.warnings()) {
            info.append("  - ").append(warning).append('\n');
         }
      }

      try {
         Files.writeString(result.outputDir().resolve("export_info.txt"), info.toString(), StandardCharsets.UTF_8);
      } catch (IOException ignored) {
         // A summary for the curious; the export itself is already on disk.
      }
   }

   // Builds the mesh a slice at a time on the client thread; finish does the rest off it.
   public static final class Scanner {
      // Reading the clock costs about as much as skipping an air block, so it is read every 256 blocks.
      private static final int CLOCK_CHECK_MASK = 0xFF;

      private final Minecraft client;
      private final ClientLevel world;
      private final BlockPos min;
      private final BlockPos max;
      private final ExportOptions options;
      private final BoundingBox bounds;
      private final MeshBuilder mesh = new MeshBuilder();
      private final BakeCollector baker;
      //? if >=26.1 {
      /*private final BlockStateModelSet models;
      *///?} else
      private final BlockModelShaper models;
      private final BlockColors colors;
      private final RandomSource random = RandomSource.create();
      private final List<String> warnings = new ArrayList<>();
      private final Set<String> skippedTypes = new LinkedHashSet<>();
      private final Iterator<BlockPos> cursor;
      private final long volume;
      private long visitedBlocks;
      private int exportedBlocks;

      public Scanner(BlockPos firstCorner, BlockPos secondCorner, ExportOptions options) {
         this.client = Minecraft.getInstance();
         this.world = this.client.level;
         this.bounds = BoundingBox.fromCorners(firstCorner, secondCorner);
         this.min = new BlockPos(this.bounds.minX(), this.bounds.minY(), this.bounds.minZ());
         this.max = new BlockPos(this.bounds.maxX(), this.bounds.maxY(), this.bounds.maxZ());
         this.options = options;
         this.baker = options.bake() ? new BakeCollector(options.bakeUvSize(), options.bakeMaxAtlas()) : null;
         //? if >=26.1 {
         /*this.models = this.client.getModelManager().getBlockStateModelSet();
         *///?} else
         this.models = this.client.getBlockRenderer().getBlockModelShaper();
         this.colors = this.client.getBlockColors();
         this.cursor = BlockPos.betweenClosed(this.min, this.max).iterator();
         this.volume = (long)this.bounds.getXSpan() * this.bounds.getYSpan() * this.bounds.getZSpan();
         int unloadedChunks = countUnloadedChunks(this.world, this.min, this.max);
         if (unloadedChunks > 0) {
            this.warnings.add(unloadedChunks + " chunk(s) in range are not loaded; those blocks export as air");
         }
      }

      public ClientLevel world() {
         return this.world;
      }

      public double progress() {
         return (double)this.visitedBlocks / this.volume;
      }

      // Reads blocks until deadlineNanos passes; true once the whole region has been read.
      public boolean step(long deadlineNanos) {
         while (this.cursor.hasNext()) {
            if (this.exportBlock(this.cursor.next())) {
               this.exportedBlocks++;
            }

            if ((++this.visitedBlocks & CLOCK_CHECK_MASK) == 0 && System.nanoTime() > deadlineNanos) {
               break;
            }
         }

         return !this.cursor.hasNext();
      }

      // Captures the entities in one go and packages everything for finish.
      public RegionExporter.Scan complete() {
         int entities = EntityCaptureExporter.export(this.mesh, this.client, this.world, this.min, this.max, this.warnings);
         return new RegionExporter.Scan(this.options, this.mesh, this.baker, this.min, this.max, this.exportedBlocks, entities, this.skippedTypes, this.warnings);
      }

      // Frees the bake sources of a scan that will never reach finish.
      public void discard() {
         if (this.baker != null) {
            this.baker.close();
         }
      }

      // Adds one block to the mesh; false for air and for blocks nothing knows how to export.
      private boolean exportBlock(BlockPos pos) {
         BlockState state = this.world.getBlockState(pos);
         if (state.isAir()) {
            return false;
         }

         BlockContext blockContext = new BlockContext(this.client, this.world, pos, state, this.mesh, this.meshCorner(pos), this.warnings);
         boolean exported = false;
         if (!state.getFluidState().isEmpty()) {
            FluidExporter.export(blockContext, this.bounds);
            exported = true;
         }

         Block block = state.getBlock();
         if (block instanceof AbstractSkullBlock skull) {
            exported |= SkullExporter.export(blockContext, skull);
         } else if (block instanceof ChestBlock || block instanceof EnderChestBlock) {
            ChestExporter.export(blockContext);
            exported = true;
         } else if (block instanceof SignBlock sign) {
            exported |= SignExporter.export(blockContext, sign);
            // From 26.2 the sign board is a block model, and its renderer only draws the text.
            //? if >=26.2
            //exported |= this.exportModelBlock(state, pos);
         // From 26.2 beds are plain block models, exported below like any other block.
         //? if <26.2 {
         } else if (block instanceof BedBlock bed) {
            BedExporter.export(blockContext, bed);
            exported = true;
         //?}
         } else if (block instanceof ShulkerBoxBlock shulkerBox) {
            ShulkerExporter.export(blockContext, shulkerBox);
            exported = true;
         } else if (block instanceof AbstractBannerBlock banner) {
            BannerExporter.export(blockContext, banner);
            exported = true;
         } else if (block instanceof DecoratedPotBlock) {
            exported |= DecoratedPotExporter.export(blockContext);
         } else {
            boolean rendererCaptured = false;
            BlockEntity blockEntity = this.world.getBlockEntity(pos);
            if (blockEntity != null) {
               rendererCaptured = BlockEntityCaptureExporter.export(blockContext, blockEntity);
               exported |= rendererCaptured;
            }

            if (!rendererCaptured) {
               if (block instanceof BellBlock) {
                  BellExporter.export(blockContext);
                  exported = true;
               } else if (block instanceof ConduitBlock) {
                  ConduitExporter.export(blockContext);
                  exported = true;
               }
            }

            if (state.getRenderShape() == RenderShape.MODEL) {
               exported |= this.exportModelBlock(state, pos);
            }
         }

         if (!exported) {
            this.skippedTypes.add(BuiltInRegistries.BLOCK.getKey(block).toString());
         }

         return exported;
      }

      private boolean exportModelBlock(BlockState state, BlockPos pos) {
         this.random.setSeed(state.getSeed(pos));
         //? if >=26.1 {
         /*List<BlockStateModelPart> parts = new ArrayList<>();
         this.models.get(state).collectParts(this.random, parts);
         *///?} else
         List<BlockModelPart> parts = this.models.getBlockModel(state).collectParts(this.random);
         if (parts.isEmpty()) {
            return false;
         }

         QuadContext context = new QuadContext(state, pos, this.meshCorner(pos), state.getOffset(pos), state.getLightEmission() > 0);
         boolean hasQuads = false;

         //? if >=26.1 {
         /*for (BlockStateModelPart part : parts) {
         *///?} else
         for (BlockModelPart part : parts) {
            for (Direction face : QUAD_FACES) {
               List<BakedQuad> quads = part.getQuads(face);
               if (quads.isEmpty()) {
                  continue;
               }

               hasQuads = true;
               if (face != null && this.isHidden(state, pos, face)) {
                  continue;
               }

               for (BakedQuad quad : quads) {
                  this.exportQuad(quad, face, context);
               }
            }
         }

         return hasQuads;
      }

      // Where the block's lowest corner lands in the mesh, whose origin is the region's lowest corner.
      private Vec3 meshCorner(BlockPos pos) {
         return new Vec3(pos.getX() - this.min.getX(), pos.getY() - this.min.getY(), pos.getZ() - this.min.getZ());
      }

      // Faces on the region boundary always stay, so the cut edge of a build is not left open.
      private boolean isHidden(BlockState state, BlockPos pos, Direction face) {
         BlockPos neighborPos = pos.relative(face);
         if (!this.bounds.isInside(neighborPos)) {
            return false;
         }

         BlockState neighbor = this.world.getBlockState(neighborPos);
         return !Block.shouldRenderFace(state, neighbor, face) || this.options.aggressiveCull() && neighborFullyOccludes(neighbor, face);
      }

      private void exportQuad(BakedQuad quad, Direction face, QuadContext context) {
         TextureAtlasSprite sprite = BakedQuads.sprite(quad);
         if (sprite == null) {
            return;
         }

         int tintIndex = BakedQuads.tintIndex(quad);
         //? if >=26.1 {
         /*BlockTintSource tintSource = tintIndex >= 0 ? this.colors.getTintSource(context.state(), tintIndex) : null;
         int tint = tintSource != null ? tintSource.colorInWorld(context.state(), this.world, context.pos()) : -1;
         *///?} else
         int tint = tintIndex >= 0 ? this.colors.getColor(context.state(), this.world, context.pos(), tintIndex) : -1;
         float[][] positions = new float[4][3];
         float[][] uvs = new float[4][];

         for (int k = 0; k < 4; k++) {
            Vector3fc vertex = BakedQuads.position(quad, k);
            positions[k][0] = (float)(context.origin().x + vertex.x() + context.offset().x);
            positions[k][1] = (float)(context.origin().y + vertex.y() + context.offset().y);
            positions[k][2] = (float)(context.origin().z + vertex.z() + context.offset().z);
            uvs[k] = BakedQuads.spriteUv(quad, sprite, k);
         }

         TextureKey texture = TextureKey.sprite(sprite, tint);
         if (this.baker != null && face != null && this.baker.tryAdd(context.origin(), face, texture, positions, uvs, context.bakeLayer())) {
            return;
         }

         if (context.emissive()) {
            this.mesh.markEmissive(texture.id());
         }

         Vector3fc normal = quad.direction().getUnitVec3f();
         this.mesh.addQuad(texture, positions, uvs, new float[]{normal.x(), normal.y(), normal.z()});
      }
   }

   // What every quad of one model block shares: its corner in the mesh and the random offset some plants get.
   private record QuadContext(BlockState state, BlockPos pos, Vec3 origin, Vec3 offset, boolean emissive) {
      int bakeLayer() {
         return (this.state.canOcclude() ? BakeCollector.LAYER_SOLID : BakeCollector.LAYER_ALPHA) | (this.emissive ? BakeCollector.LAYER_EMISSIVE : 0);
      }
   }

   public record Scan(
      ExportOptions options, MeshBuilder mesh, BakeCollector baker, BlockPos min, BlockPos max, int exportedBlocks, int exportedEntities, Set<String> skippedTypes, List<String> warnings
   ) {
   }

   public record Result(
      int blocksExported,
      int entitiesExported,
      int facesEmitted,
      Set<String> skippedTypes,
      List<String> warnings,
      Path outputDir,
      BakeCollector.Stats bakeStats,
      MeshBuilder.OptimizationStats optimizationStats,
      int facesBeforeOptimize
   ) {
   }
}
