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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3fc;

public final class RegionExporter {
   public static final long MAX_VOLUME = 16000000L;
   private static final Direction[] DIRECTIONS = Direction.values();

   /**
    * Reads the region and builds the mesh. Touches the world, the model manager and the block
    * entity renderers, so it has to run on the client thread; {@link #finish} does the rest.
    */
   public static RegionExporter.Scan scan(MinecraftClient mc, ClientWorld world, BlockPos a, BlockPos b, ExportOptions opts) {
      BlockPos blockpos = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
      BlockPos blockpos1 = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
      MeshBuilder meshbuilder = new MeshBuilder();
      BakeCollector bakecollector = opts.bake() ? new BakeCollector(opts.bakeUvSize(), opts.bakeMaxAtlas()) : null;
      BlockModels blockmodels = mc.getBlockRenderManager().getModels();
      BlockColors blockcolors = mc.getBlockColors();
      Random random = Random.create();
      List<String> list = new ArrayList<>();
      Set<String> set = new LinkedHashSet<>();
      int i = 0;
      int j = countUnloadedChunks(world, blockpos, blockpos1);
      if (j > 0) {
         list.add(j + " chunk(s) in range are not loaded; those blocks export as air");
      }

      for (BlockPos blockpos2 : BlockPos.iterate(blockpos, blockpos1)) {
         BlockState blockstate = world.getBlockState(blockpos2);
         if (!blockstate.isAir()) {
            double d0 = blockpos2.getX() - blockpos.getX();
            double d1 = blockpos2.getY() - blockpos.getY();
            double d2 = blockpos2.getZ() - blockpos.getZ();
            boolean flag = false;
            FluidState fluidstate = blockstate.getFluidState();
            if (!fluidstate.isEmpty()) {
               FluidExporter.export(meshbuilder, world, blockpos2, fluidstate, blockpos, blockpos1, d0, d1, d2);
               flag = true;
            }

            Block block = blockstate.getBlock();
            if (block instanceof AbstractSkullBlock abstractskullblock) {
               flag |= SkullExporter.export(meshbuilder, world, blockpos2, blockstate, abstractskullblock, d0, d1, d2, list);
            } else if (block instanceof ChestBlock || block instanceof EnderChestBlock) {
               ChestExporter.export(meshbuilder, blockstate, d0, d1, d2);
               flag = true;
            } else if (block instanceof AbstractSignBlock abstractsignblock) {
               flag |= SignExporter.export(meshbuilder, mc, world, blockpos2, blockstate, abstractsignblock, d0, d1, d2, list);
            } else if (block instanceof BedBlock bedblock) {
               BedExporter.export(meshbuilder, blockstate, bedblock, d0, d1, d2);
               flag = true;
            } else if (block instanceof ShulkerBoxBlock shulkerboxblock) {
               ShulkerExporter.export(meshbuilder, blockstate, shulkerboxblock, d0, d1, d2);
               flag = true;
            } else if (block instanceof AbstractBannerBlock abstractbannerblock) {
               BannerExporter.export(meshbuilder, world, blockpos2, blockstate, abstractbannerblock, d0, d1, d2);
               flag = true;
            } else if (block instanceof DecoratedPotBlock) {
               flag |= DecoratedPotExporter.export(meshbuilder, world, blockpos2, blockstate, d0, d1, d2, list);
            } else {
               boolean flag1 = false;
               BlockEntity blockentity = world.getBlockEntity(blockpos2);
               if (blockentity != null) {
                  flag1 = BerCaptureExporter.export(meshbuilder, mc, blockpos2, blockentity, d0, d1, d2, list);
                  flag |= flag1;
               }

               if (!flag1) {
                  if (block instanceof BellBlock) {
                     BellExporter.export(meshbuilder, d0, d1, d2);
                     flag = true;
                  } else if (block instanceof ConduitBlock) {
                     ConduitExporter.export(meshbuilder, d0, d1, d2);
                     flag = true;
                  }
               }

               if (blockstate.getRenderType() == BlockRenderType.MODEL) {
                  flag |= exportModelBlock(
                     meshbuilder,
                     world,
                     blockmodels,
                     blockcolors,
                     random,
                     blockstate,
                     blockpos2,
                     blockpos,
                     blockpos1,
                     d0,
                     d1,
                     d2,
                     opts.aggressiveCull(),
                     bakecollector
                  );
               }
            }

            if (flag) {
               i++;
            } else if (fluidstate.isEmpty()) {
               set.add(Registries.BLOCK.getId(block).toString());
            }
         }
      }

      int k = EntityCaptureExporter.export(meshbuilder, mc, world, blockpos, blockpos1, list);
      return new RegionExporter.Scan(meshbuilder, bakecollector, blockpos, blockpos1, i, k, set, list, j);
   }

   /**
    * Turns a {@link #scan} into files on disk. Only touches textures already pulled into memory,
    * so it is safe to run off the client thread and keep the game responsive.
    */
   public static RegionExporter.Result finish(MinecraftClient mc, RegionExporter.Scan scan, ExportOptions opts) throws IOException {
      MeshBuilder meshbuilder = scan.mesh();
      List<String> list = scan.warnings();
      BakeCollector bakecollector = scan.baker();
      BakeCollector.Stats bakecollector$stats = null;
      if (bakecollector != null && !bakecollector.isEmpty()) {
         bakecollector$stats = bakecollector.flush(meshbuilder, list);
      }

      MeshBuilder.OptStats meshbuilder$optstats = null;
      int l = meshbuilder.faceCount();
      if (opts.weldVertices()) {
         meshbuilder$optstats = meshbuilder.optimize();
      }

      Path path = mc.runDirectory.toPath().resolve("meshify").resolve(opts.name());

      try {
         ObjWriter.write(mc, path, opts, meshbuilder, list);
      } finally {
         if (bakecollector != null) {
            bakecollector.close();
         }
      }

      writeInfo(
         path, opts, scan.min(), scan.max(), scan.blocks(), scan.entities(), meshbuilder.faceCount(), scan.skipped(), list, bakecollector$stats,
         meshbuilder$optstats, l
      );
      return new RegionExporter.Result(
         scan.blocks(), scan.entities(), meshbuilder.faceCount(), scan.skipped(), scan.unloadedChunks(), list, path, bakecollector$stats,
         meshbuilder$optstats, l
      );
   }

   private static boolean exportModelBlock(
      MeshBuilder mesh,
      ClientWorld world,
      BlockModels models,
      BlockColors colors,
      Random random,
      BlockState state,
      BlockPos pos,
      BlockPos min,
      BlockPos max,
      double bx,
      double by,
      double bz,
      boolean aggressiveCull,
      BakeCollector baker
   ) {
      BlockStateModel blockstatemodel = models.getModel(state);
      random.setSeed(state.getRenderingSeed(pos));
      List<BlockModelPart> list = blockstatemodel.getParts(random);
      if (list.isEmpty()) {
         return false;
      } else {
         Vec3d vec3d = state.getModelOffset(pos);
         boolean flag = false;
         boolean flag2 = state.getLuminance() > 0;
         int i2 = (state.isOpaque() ? 0 : 1) | (flag2 ? 2 : 0);

         for (BlockModelPart blockmodelpart : list) {
            for (int i = -1; i < DIRECTIONS.length; i++) {
               Direction direction = i < 0 ? null : DIRECTIONS[i];
               List<BakedQuad> list1 = blockmodelpart.getQuads(direction);
               if (!list1.isEmpty()) {
                  flag = true;
                  if (direction != null && !isBoundary(pos, direction, min, max)) {
                     BlockState blockstate = world.getBlockState(pos.offset(direction));
                     boolean flag1 = !Block.shouldDrawSide(state, blockstate, direction);
                     if (!flag1 && aggressiveCull) {
                        flag1 = neighborFullyOccludes(blockstate, direction);
                     }

                     if (flag1) {
                        continue;
                     }
                  }

                  for (BakedQuad bakedquad : list1) {
                     Sprite sprite = bakedquad.sprite();
                     if (sprite != null) {
                        int j = -1;
                        if (bakedquad.tintIndex() >= 0) {
                           j = colors.getColor(state, world, pos, bakedquad.tintIndex()) & 16777215;
                           if (j == 16777215) {
                              j = -1;
                           }
                        }

                        float f = sprite.getMinU();
                        float f1 = sprite.getMaxU();
                        float f2 = sprite.getMinV();
                        float f3 = sprite.getMaxV();
                        float f4 = f1 - f;
                        float f5 = f3 - f2;
                        float[][] afloat = new float[4][3];
                        float[][] afloat1 = new float[4][2];

                        for (int k = 0; k < 4; k++) {
                           Vector3fc vector3fc = bakedquad.getPosition(k);
                           afloat[k][0] = (float)(bx + vector3fc.x() + vec3d.x);
                           afloat[k][1] = (float)(by + vector3fc.y() + vec3d.y);
                           afloat[k][2] = (float)(bz + vector3fc.z() + vec3d.z);
                           long l = bakedquad.getTexcoords(k);
                           float f6 = Vector2f.getX(l);
                           float f7 = Vector2f.getY(l);
                           afloat1[k][0] = f4 > 0.0F ? clamp01((f6 - f) / f4) : 0.0F;
                           afloat1[k][1] = f5 > 0.0F ? clamp01((f7 - f2) / f5) : 0.0F;
                        }

                        TexKey texkey = TexKey.sprite(sprite, j);
                        if (baker == null || direction == null || !baker.tryAdd(bx, by, bz, direction, texkey, afloat, afloat1, i2)) {
                           if (flag2) {
                              mesh.markEmissive(texkey.id());
                           }

                           Vector3fc vector3fc1 = bakedquad.face().getFloatVector();
                           mesh.addQuad(texkey, afloat, afloat1, new float[]{vector3fc1.x(), vector3fc1.y(), vector3fc1.z()});
                        }
                     }
                  }
               }
            }
         }

         return flag;
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

   private static boolean isBoundary(BlockPos pos, Direction dir, BlockPos min, BlockPos max) {
      BlockPos blockpos = pos.offset(dir);
      return blockpos.getX() < min.getX()
         || blockpos.getX() > max.getX()
         || blockpos.getY() < min.getY()
         || blockpos.getY() > max.getY()
         || blockpos.getZ() < min.getZ()
         || blockpos.getZ() > max.getZ();
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

   private static void writeInfo(
      Path outDir,
      ExportOptions opts,
      BlockPos min,
      BlockPos max,
      int blocks,
      int entities,
      int faces,
      Set<String> skipped,
      List<String> warnings,
      BakeCollector.Stats bakeStats,
      MeshBuilder.OptStats optStats,
      int facesBeforeOptimize
   ) {
      try {
         StringBuilder stringbuilder = new StringBuilder();
         stringbuilder.append("meshify export: ").append(opts.name()).append('\n');
         stringbuilder.append("region: ").append(min.toShortString()).append(" .. ").append(max.toShortString()).append('\n');
         stringbuilder.append("textures: ")
            .append(opts.mergeAtlas() ? "single atlas" : "separate files")
            .append(", scale x")
            .append(opts.texScale())
            .append('\n');
         stringbuilder.append("options: ").append(opts.aggressiveCull() ? "aggressive-cull " : "").append(opts.weldVertices() ? "weld " : "").append('\n');
         stringbuilder.append("blocks exported: ").append(blocks).append(", entities: ").append(entities).append(", faces: ").append(faces).append('\n');
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
                  facesBeforeOptimize,
                  faces,
                  optStats.degenerateRemoved(),
                  optStats.duplicateRemoved(),
                  optStats.weldGrid()
               )
            );
         }

         if (!skipped.isEmpty()) {
            stringbuilder.append("\nskipped block types (no baked geometry / unsupported):\n");

            for (String s : skipped) {
               stringbuilder.append("  - ").append(s).append('\n');
            }
         }

         if (!warnings.isEmpty()) {
            stringbuilder.append("\nwarnings:\n");

            for (String s1 : warnings) {
               stringbuilder.append("  - ").append(s1).append('\n');
            }
         }

         Files.writeString(outDir.resolve("export_info.txt"), stringbuilder.toString(), StandardCharsets.UTF_8);
      } catch (IOException ioexception) {
      }
   }

   /** Everything {@link #finish} needs, so it can run without touching the world again. */
   public record Scan(
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
