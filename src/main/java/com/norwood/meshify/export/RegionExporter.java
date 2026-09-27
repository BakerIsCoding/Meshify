package com.norwood.meshify.export;

import com.norwood.meshify.export.special.BannerExporter;
import com.norwood.meshify.export.special.BedExporter;
import com.norwood.meshify.export.special.BellExporter;
import com.norwood.meshify.export.special.BerCaptureExporter;
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
   private static final Direction[] DIRECTIONS = Direction.values();
   /** Bit 1 of a bake layer, next to {@link BakeCollector#LAYER_ALPHA} in bit 0. */
   private static final int BAKE_EMISSIVE_BIT = 2;

   private RegionExporter() {
   }

   public static Path exportRoot(MinecraftClient mc) {
      return mc.runDirectory.toPath().resolve("meshify");
   }

   public static Path outputDir(MinecraftClient mc, String name) {
      return exportRoot(mc).resolve(name);
   }

   /** Writes a finished scan to disk off the client thread; {@code onStage} runs before each step and may throw to abort. */
   public static RegionExporter.Result finish(MinecraftClient mc, RegionExporter.Scan scan, Consumer<ExportStage> onStage) throws IOException {
      ExportOptions opts = scan.options();
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
         MeshBuilder.OptStats optStats = null;
         if (opts.weldVertices()) {
            onStage.accept(ExportStage.OPTIMIZING);
            optStats = mesh.optimize();
         }

         Path outDir = outputDir(mc, opts.name());
         ObjWriter.write(mc, outDir, opts, mesh, warnings, onStage);
         RegionExporter.Result result = new RegionExporter.Result(
            scan.blocks(), scan.entities(), mesh.faceCount(), scan.skipped(), scan.unloadedChunks(), warnings, outDir, bakeStats, optStats,
            facesBeforeOptimize
         );
         writeInfo(scan, result);
         return result;
      } finally {
         if (baker != null) {
            baker.close();
         }
      }
   }

   private static float clamp01(float f) {
      return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
   }

   private static boolean neighborFullyOccludes(BlockState neighbor, Direction cull) {
      if (neighbor.isAir() || !neighbor.isOpaque()) {
         return false;
      } else if (neighbor.isOpaqueFullCube()) {
         return true;
      } else {
         Direction direction = cull.getOpposite();

         try {
            return Block.isFaceFullSquare(neighbor.getCullingFace(direction), direction);
         } catch (Exception exception) {
            return false;
         }
      }
   }

   public static int countUnloadedChunks(ClientWorld world, BlockPos min, BlockPos max) {
      int i = 0;
      ChunkPos chunkpos = new ChunkPos(min);
      ChunkPos chunkpos1 = new ChunkPos(max);

      for (int j = chunkpos.x; j <= chunkpos1.x; j++) {
         for (int k = chunkpos.z; k <= chunkpos1.z; k++) {
            if (!world.getChunkManager().isChunkLoaded(j, k)) {
               i++;
            }
         }
      }

      return i;
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

   private static void writeInfo(RegionExporter.Scan scan, RegionExporter.Result result) {
      ExportOptions opts = scan.options();
      int faces = result.facesEmitted();
      BakeCollector.Stats bakeStats = result.bakeStats();
      MeshBuilder.OptStats optStats = result.optStats();

      try {
         StringBuilder stringbuilder = new StringBuilder();
         stringbuilder.append("meshify export: ").append(opts.name()).append('\n');
         stringbuilder.append("region: ").append(scan.min().toShortString()).append(" .. ").append(scan.max().toShortString()).append('\n');
         stringbuilder.append("textures: ")
            .append(opts.mergeAtlas() ? "single atlas" : "separate files")
            .append(", scale x")
            .append(opts.texScale())
            .append('\n');
         stringbuilder.append("options: ").append(opts.aggressiveCull() ? "aggressive-cull " : "").append(opts.weldVertices() ? "weld " : "").append('\n');
         stringbuilder.append("blocks exported: ").append(result.blocksExported()).append(", entities: ").append(result.entitiesExported()).append(", faces: ").append(faces).append('\n');
         if (bakeStats != null) {
            stringbuilder.append(
               String.format(
                  Locale.ROOT,
                  "bake: %d faces -> %d quads (%.1f%% reduction, %d sheet(s), %.1f Mpx)%n",
                  bakeStats.facesConsumed(),
                  bakeStats.quadsEmitted(),
                  bakeStats.facesConsumed() > 0 ? 100.0 * (1.0 - (double)bakeStats.quadsEmitted() / bakeStats.facesConsumed()) : 0.0,
                  bakeStats.sheets(),
                  bakeStats.bakedPixels() / 1000000.0
               )
            );
         }

         if (optStats != null) {
            stringbuilder.append(
               String.format(
                  Locale.ROOT,
                  "optimize: %d -> %d faces (%d degenerate, %d duplicate removed, weld grid 1/%d block)%n",
                  result.facesBeforeOptimize(),
                  faces,
                  optStats.degenerateRemoved(),
                  optStats.duplicateRemoved(),
                  optStats.weldGrid()
               )
            );
         }

         if (!result.skippedTypes().isEmpty()) {
            stringbuilder.append("\nskipped block types (no baked geometry / unsupported):\n");

            for (String s : result.skippedTypes()) {
               stringbuilder.append("  - ").append(s).append('\n');
            }
         }

         if (!result.warnings().isEmpty()) {
            stringbuilder.append("\nwarnings:\n");

            for (String s1 : result.warnings()) {
               stringbuilder.append("  - ").append(s1).append('\n');
            }
         }

         Files.writeString(result.outDir().resolve("export_info.txt"), stringbuilder.toString(), StandardCharsets.UTF_8);
      } catch (IOException ioexception) {
      }
   }

   /** Builds the mesh a slice at a time on the client thread; {@link #finish} does the rest off it. */
   public static final class Scanner {
      /** Reading the clock costs about as much as skipping an air block, so it is read every 256 blocks. */
      private static final int CLOCK_CHECK_MASK = 0xFF;

      private final MinecraftClient mc;
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
      private final Set<String> skipped = new LinkedHashSet<>();
      private final Iterator<BlockPos> cursor;
      private final long volume;
      private final int unloadedChunks;
      private long visited;
      private int exportedBlocks;

      public Scanner(BlockPos a, BlockPos b, ExportOptions opts) {
         this.mc = MinecraftClient.getInstance();
         this.world = this.mc.world;
         this.bounds = BlockBox.create(a, b);
         this.min = new BlockPos(this.bounds.getMinX(), this.bounds.getMinY(), this.bounds.getMinZ());
         this.max = new BlockPos(this.bounds.getMaxX(), this.bounds.getMaxY(), this.bounds.getMaxZ());
         this.options = opts;
         this.baker = opts.bake() ? new BakeCollector(opts.bakeUvSize(), opts.bakeMaxAtlas()) : null;
         this.models = mc.getBlockRenderManager().getModels();
         this.colors = mc.getBlockColors();
         this.cursor = BlockPos.iterate(this.min, this.max).iterator();
         this.volume = (this.max.getX() - this.min.getX() + 1L) * (this.max.getY() - this.min.getY() + 1L) * (this.max.getZ() - this.min.getZ() + 1L);
         this.unloadedChunks = countUnloadedChunks(this.world, this.min, this.max);
         if (this.unloadedChunks > 0) {
            this.warnings.add(this.unloadedChunks + " chunk(s) in range are not loaded; those blocks export as air");
         }
      }

      public ClientWorld world() {
         return this.world;
      }

      public double progress() {
         return (double)this.visited / this.volume;
      }

      /** Reads blocks until {@code deadlineNanos} passes; true once the whole region has been read. */
      public boolean step(long deadlineNanos) {
         while (this.cursor.hasNext()) {
            if (this.exportBlock(this.cursor.next())) {
               this.exportedBlocks++;
            }

            if ((++this.visited & CLOCK_CHECK_MASK) == 0 && System.nanoTime() > deadlineNanos) {
               break;
            }
         }

         return !this.cursor.hasNext();
      }

      /** Captures the entities in one go and packages everything for {@link #finish}. */
      public RegionExporter.Scan complete() {
         int entities = EntityCaptureExporter.export(this.mesh, this.mc, this.world, this.min, this.max, this.warnings);
         return new RegionExporter.Scan(
            this.options, this.mesh, this.baker, this.min, this.max, this.exportedBlocks, entities, this.skipped, this.warnings, this.unloadedChunks
         );
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

         double bx = pos.getX() - this.min.getX();
         double by = pos.getY() - this.min.getY();
         double bz = pos.getZ() - this.min.getZ();
         boolean exported = false;
         FluidState fluid = state.getFluidState();
         if (!fluid.isEmpty()) {
            FluidExporter.export(this.mesh, this.world, pos, fluid, this.min, this.max, bx, by, bz);
            exported = true;
         }

         Block block = state.getBlock();
         if (block instanceof AbstractSkullBlock skull) {
            exported |= SkullExporter.export(this.mesh, this.world, pos, state, skull, bx, by, bz, this.warnings);
         } else if (block instanceof ChestBlock || block instanceof EnderChestBlock) {
            ChestExporter.export(this.mesh, state, bx, by, bz);
            exported = true;
         } else if (block instanceof AbstractSignBlock sign) {
            exported |= SignExporter.export(this.mesh, this.mc, this.world, pos, state, sign, bx, by, bz, this.warnings);
         } else if (block instanceof BedBlock bed) {
            BedExporter.export(this.mesh, state, bed, bx, by, bz);
            exported = true;
         } else if (block instanceof ShulkerBoxBlock shulkerBox) {
            ShulkerExporter.export(this.mesh, state, shulkerBox, bx, by, bz);
            exported = true;
         } else if (block instanceof AbstractBannerBlock banner) {
            BannerExporter.export(this.mesh, this.world, pos, state, banner, bx, by, bz);
            exported = true;
         } else if (block instanceof DecoratedPotBlock) {
            exported |= DecoratedPotExporter.export(this.mesh, this.world, pos, state, bx, by, bz, this.warnings);
         } else {
            boolean rendererCaptured = false;
            BlockEntity blockEntity = this.world.getBlockEntity(pos);
            if (blockEntity != null) {
               rendererCaptured = BerCaptureExporter.export(this.mesh, this.mc, pos, blockEntity, bx, by, bz, this.warnings);
               exported |= rendererCaptured;
            }

            if (!rendererCaptured) {
               if (block instanceof BellBlock) {
                  BellExporter.export(this.mesh, bx, by, bz);
                  exported = true;
               } else if (block instanceof ConduitBlock) {
                  ConduitExporter.export(this.mesh, bx, by, bz);
                  exported = true;
               }
            }

            if (state.getRenderType() == BlockRenderType.MODEL) {
               exported |= this.exportModelBlock(state, pos);
            }
         }

         if (!exported) {
            this.skipped.add(Registries.BLOCK.getId(block).toString());
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
            for (int i = -1; i < DIRECTIONS.length; i++) {
               Direction face = i < 0 ? null : DIRECTIONS[i];
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

         int tint = -1;
         if (quad.tintIndex() >= 0) {
            tint = this.colors.getColor(context.state(), this.world, context.pos(), quad.tintIndex()) & 0xFFFFFF;
            if (tint == 0xFFFFFF) {
               tint = -1;
            }
         }

         float minU = sprite.getMinU();
         float minV = sprite.getMinV();
         float spanU = sprite.getMaxU() - minU;
         float spanV = sprite.getMaxV() - minV;
         float[][] positions = new float[4][3];
         float[][] uvs = new float[4][2];

         for (int k = 0; k < 4; k++) {
            Vector3fc vertex = quad.getPosition(k);
            positions[k][0] = (float)(context.bx() + vertex.x() + context.offset().x);
            positions[k][1] = (float)(context.by() + vertex.y() + context.offset().y);
            positions[k][2] = (float)(context.bz() + vertex.z() + context.offset().z);
            long texcoords = quad.getTexcoords(k);
            uvs[k][0] = spanU > 0.0F ? clamp01((Vector2f.getX(texcoords) - minU) / spanU) : 0.0F;
            uvs[k][1] = spanV > 0.0F ? clamp01((Vector2f.getY(texcoords) - minV) / spanV) : 0.0F;
         }

         TexKey texKey = TexKey.sprite(sprite, tint);
         if (this.baker != null && face != null && this.baker.tryAdd(context.bx(), context.by(), context.bz(), face, texKey, positions, uvs, context.bakeLayer())) {
            return;
         }

         if (context.emissive()) {
            this.mesh.markEmissive(texKey.id());
         }

         Vector3fc normal = quad.face().getFloatVector();
         this.mesh.addQuad(texKey, positions, uvs, new float[]{normal.x(), normal.y(), normal.z()});
      }
   }

   private record QuadContext(BlockState state, BlockPos pos, Vec3d offset, double bx, double by, double bz, boolean emissive) {
      int bakeLayer() {
         return (this.state.isOpaque() ? BakeCollector.LAYER_SOLID : BakeCollector.LAYER_ALPHA) | (this.emissive ? BAKE_EMISSIVE_BIT : 0);
      }
   }

   public record Scan(
      ExportOptions options,
      MeshBuilder mesh,
      BakeCollector baker,
      BlockPos min,
      BlockPos max,
      int blocks,
      int entities,
      Set<String> skipped,
      List<String> warnings,
      int unloadedChunks
   ) {
   }

   public record Result(
      int blocksExported,
      int entitiesExported,
      int facesEmitted,
      Set<String> skippedTypes,
      int unloadedChunks,
      List<String> warnings,
      Path outDir,
      BakeCollector.Stats bakeStats,
      MeshBuilder.OptStats optStats,
      int facesBeforeOptimize
   ) {
   }
}
