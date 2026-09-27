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
import net.minecraft.block.AbstractBannerBlock;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ConduitBlock;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3fc;

public final class RegionExporter {
   public static final long MAX_VOLUME = 16000000L;
   /** The cull faces a model part files its quads under, and null for the ones it never culls. */
   public static final Direction[] QUAD_FACES = {
      null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
   };

   private RegionExporter() {
   }

   public static Path exportRoot(MinecraftClient client) {
      return client.runDirectory.toPath().resolve("meshify");
   }

   public static Path outputDir(MinecraftClient client, String name) {
      return exportRoot(client).resolve(name);
   }

   /** Writes a finished scan to disk off the client thread; {@code onStage} runs before each step and may throw to abort. */
   public static RegionExporter.Result finish(MinecraftClient client, RegionExporter.Scan scan, Consumer<ExportStage> onStage) throws IOException {
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
      if (neighbor.isAir() || !neighbor.isOpaque()) {
         return false;
      } else if (neighbor.isOpaqueFullCube()) {
         return true;
      }

      Direction side = face.getOpposite();
      try {
         return Block.isFaceFullSquare(neighbor.getCullingFace(side), side);
      } catch (Exception exception) {
         return false;
      }
   }

   public static int countUnloadedChunks(ClientWorld world, BlockPos min, BlockPos max) {
      int unloaded = 0;
      ChunkPos first = new ChunkPos(min);
      ChunkPos last = new ChunkPos(max);

      for (int x = first.x; x <= last.x; x++) {
         for (int z = first.z; z <= last.z; z++) {
            if (!world.getChunkManager().isChunkLoaded(x, z)) {
               unloaded++;
            }
         }
      }

      return unloaded;
   }

   /** Smallest box around the non-air blocks, if any; unloaded chunks read as air, so check them first. */
   public static Optional<BlockBox> solidBounds(ClientWorld world, BlockPos min, BlockPos max) {
      // ponytail: reads every block on the client thread (~1 s at the limit); skip empty chunk sections if it stalls.
      int loX = Integer.MAX_VALUE;
      int loY = Integer.MAX_VALUE;
      int loZ = Integer.MAX_VALUE;
      int hiX = Integer.MIN_VALUE;
      int hiY = Integer.MIN_VALUE;
      int hiZ = Integer.MIN_VALUE;

      for (BlockPos pos : BlockPos.iterate(min, max)) {
         if (!world.getBlockState(pos).isAir()) {
            loX = Math.min(loX, pos.getX());
            loY = Math.min(loY, pos.getY());
            loZ = Math.min(loZ, pos.getZ());
            hiX = Math.max(hiX, pos.getX());
            hiY = Math.max(hiY, pos.getY());
            hiZ = Math.max(hiZ, pos.getZ());
         }
      }

      return loX > hiX ? Optional.empty() : Optional.of(new BlockBox(loX, loY, loZ, hiX, hiY, hiZ));
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

   /** Builds the mesh a slice at a time on the client thread; {@link #finish} does the rest off it. */
   public static final class Scanner {
      /** Reading the clock costs about as much as skipping an air block, so it is read every 256 blocks. */
      private static final int CLOCK_CHECK_MASK = 0xFF;

      private final MinecraftClient client;
      private final ClientWorld world;
      private final BlockPos min;
      private final BlockPos max;
      private final ExportOptions options;
      private final BlockBox bounds;
      private final MeshBuilder mesh = new MeshBuilder();
      private final BakeCollector baker;
      private final BlockModels models;
      private final BlockColors colors;
      private final Random random = Random.create();
      private final List<String> warnings = new ArrayList<>();
      private final Set<String> skippedTypes = new LinkedHashSet<>();
      private final Iterator<BlockPos> cursor;
      private final long volume;
      private long visitedBlocks;
      private int exportedBlocks;

      public Scanner(BlockPos firstCorner, BlockPos secondCorner, ExportOptions options) {
         this.client = MinecraftClient.getInstance();
         this.world = this.client.world;
         this.bounds = BlockBox.create(firstCorner, secondCorner);
         this.min = new BlockPos(this.bounds.getMinX(), this.bounds.getMinY(), this.bounds.getMinZ());
         this.max = new BlockPos(this.bounds.getMaxX(), this.bounds.getMaxY(), this.bounds.getMaxZ());
         this.options = options;
         this.baker = options.bake() ? new BakeCollector(options.bakeUvSize(), options.bakeMaxAtlas()) : null;
         this.models = this.client.getBlockRenderManager().getModels();
         this.colors = this.client.getBlockColors();
         this.cursor = BlockPos.iterate(this.min, this.max).iterator();
         this.volume = (long)this.bounds.getBlockCountX() * this.bounds.getBlockCountY() * this.bounds.getBlockCountZ();
         int unloadedChunks = countUnloadedChunks(this.world, this.min, this.max);
         if (unloadedChunks > 0) {
            this.warnings.add(unloadedChunks + " chunk(s) in range are not loaded; those blocks export as air");
         }
      }

      public ClientWorld world() {
         return this.world;
      }

      public double progress() {
         return (double)this.visitedBlocks / this.volume;
      }

      /** Reads blocks until {@code deadlineNanos} passes; true once the whole region has been read. */
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

      /** Captures the entities in one go and packages everything for {@link #finish}. */
      public RegionExporter.Scan complete() {
         int entities = EntityCaptureExporter.export(this.mesh, this.client, this.world, this.min, this.max, this.warnings);
         return new RegionExporter.Scan(this.options, this.mesh, this.baker, this.min, this.max, this.exportedBlocks, entities, this.skippedTypes, this.warnings);
      }

      /** Frees the bake sources of a scan that will never reach {@link #finish}. */
      public void discard() {
         if (this.baker != null) {
            this.baker.close();
         }
      }

      /** Adds one block to the mesh; false for air and for blocks nothing knows how to export. */
      private boolean exportBlock(BlockPos pos) {
         BlockState state = this.world.getBlockState(pos);
         if (state.isAir()) {
            return false;
         }

         double blockX = pos.getX() - this.min.getX();
         double blockY = pos.getY() - this.min.getY();
         double blockZ = pos.getZ() - this.min.getZ();
         boolean exported = false;
         FluidState fluid = state.getFluidState();
         if (!fluid.isEmpty()) {
            FluidExporter.export(this.mesh, this.world, pos, fluid, this.bounds, blockX, blockY, blockZ);
            exported = true;
         }

         Block block = state.getBlock();
         if (block instanceof AbstractSkullBlock skull) {
            exported |= SkullExporter.export(this.mesh, this.world, pos, state, skull, blockX, blockY, blockZ, this.warnings);
         } else if (block instanceof ChestBlock || block instanceof EnderChestBlock) {
            ChestExporter.export(this.mesh, state, blockX, blockY, blockZ);
            exported = true;
         } else if (block instanceof AbstractSignBlock sign) {
            exported |= SignExporter.export(this.mesh, this.client, this.world, pos, state, sign, blockX, blockY, blockZ, this.warnings);
         } else if (block instanceof BedBlock bed) {
            BedExporter.export(this.mesh, state, bed, blockX, blockY, blockZ);
            exported = true;
         } else if (block instanceof ShulkerBoxBlock shulkerBox) {
            ShulkerExporter.export(this.mesh, state, shulkerBox, blockX, blockY, blockZ);
            exported = true;
         } else if (block instanceof AbstractBannerBlock banner) {
            BannerExporter.export(this.mesh, this.world, pos, state, banner, blockX, blockY, blockZ);
            exported = true;
         } else if (block instanceof DecoratedPotBlock) {
            exported |= DecoratedPotExporter.export(this.mesh, this.world, pos, state, blockX, blockY, blockZ, this.warnings);
         } else {
            boolean rendererCaptured = false;
            BlockEntity blockEntity = this.world.getBlockEntity(pos);
            if (blockEntity != null) {
               rendererCaptured = BlockEntityCaptureExporter.export(this.mesh, this.client, pos, blockEntity, blockX, blockY, blockZ, this.warnings);
               exported |= rendererCaptured;
            }

            if (!rendererCaptured) {
               if (block instanceof BellBlock) {
                  BellExporter.export(this.mesh, blockX, blockY, blockZ);
                  exported = true;
               } else if (block instanceof ConduitBlock) {
                  ConduitExporter.export(this.mesh, blockX, blockY, blockZ);
                  exported = true;
               }
            }

            if (state.getRenderType() == BlockRenderType.MODEL) {
               exported |= this.exportModelBlock(state, pos);
            }
         }

         if (!exported) {
            this.skippedTypes.add(Registries.BLOCK.getId(block).toString());
         }

         return exported;
      }

      private boolean exportModelBlock(BlockState state, BlockPos pos) {
         BlockStateModel model = this.models.getModel(state);
         this.random.setSeed(state.getRenderingSeed(pos));
         List<BlockModelPart> parts = model.getParts(this.random);
         if (parts.isEmpty()) {
            return false;
         }

         BlockPos local = pos.subtract(this.min);
         QuadContext context = new QuadContext(state, pos, state.getModelOffset(pos), local.getX(), local.getY(), local.getZ(), state.getLuminance() > 0);
         boolean hasQuads = false;

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

      /** Faces on the region boundary always stay, so the cut edge of a build is not left open. */
      private boolean isHidden(BlockState state, BlockPos pos, Direction face) {
         BlockPos neighborPos = pos.offset(face);
         if (!this.bounds.contains(neighborPos)) {
            return false;
         }

         BlockState neighbor = this.world.getBlockState(neighborPos);
         return !Block.shouldDrawSide(state, neighbor, face) || this.options.aggressiveCull() && neighborFullyOccludes(neighbor, face);
      }

      private void exportQuad(BakedQuad quad, Direction face, QuadContext context) {
         Sprite sprite = quad.sprite();
         if (sprite == null) {
            return;
         }

         int tint = quad.tintIndex() >= 0 ? this.colors.getColor(context.state(), this.world, context.pos(), quad.tintIndex()) : -1;
         float[][] positions = new float[4][3];
         float[][] uvs = new float[4][];

         for (int k = 0; k < 4; k++) {
            Vector3fc vertex = quad.getPosition(k);
            positions[k][0] = (float)(context.blockX() + vertex.x() + context.offset().x);
            positions[k][1] = (float)(context.blockY() + vertex.y() + context.offset().y);
            positions[k][2] = (float)(context.blockZ() + vertex.z() + context.offset().z);
            long texcoords = quad.getTexcoords(k);
            uvs[k] = TextureKey.spriteUv(sprite, Vector2f.getX(texcoords), Vector2f.getY(texcoords));
         }

         TextureKey texture = TextureKey.sprite(sprite, tint);
         if (this.baker != null && face != null && this.baker.tryAdd(context.blockX(), context.blockY(), context.blockZ(), face, texture, positions, uvs, context.bakeLayer())) {
            return;
         }

         if (context.emissive()) {
            this.mesh.markEmissive(texture.id());
         }

         Vector3fc normal = quad.face().getFloatVector();
         this.mesh.addQuad(texture, positions, uvs, new float[]{normal.x(), normal.y(), normal.z()});
      }
   }

   private record QuadContext(BlockState state, BlockPos pos, Vec3d offset, double blockX, double blockY, double blockZ, boolean emissive) {
      int bakeLayer() {
         return (this.state.isOpaque() ? BakeCollector.LAYER_SOLID : BakeCollector.LAYER_ALPHA) | (this.emissive ? BakeCollector.LAYER_EMISSIVE : 0);
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
