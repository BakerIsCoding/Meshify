package com.norwood.meshify;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.norwood.meshify.export.BakeCollector;
import com.norwood.meshify.export.ExportOptions;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.RegionExporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public class MeshifyClient implements ClientModInitializer {
   /** Above this, an export is slow enough to be worth a heads-up before it starts. */
   private static final long WARN_VOLUME = 2000000L;

   private static final AtomicBoolean BUSY = new AtomicBoolean();

   private static final List<MeshifyClient.Option> OPTIONS = List.of(
      new MeshifyClient.Option("atlas", "meshify.opt.desc.atlas", null),
      new MeshifyClient.Option("separate", "meshify.opt.desc.separate", null),
      new MeshifyClient.Option("bake", "meshify.opt.desc.bake", null),
      new MeshifyClient.Option("weld", "meshify.opt.desc.weld", null),
      new MeshifyClient.Option("cull", "meshify.opt.desc.cull", null),
      new MeshifyClient.Option("uv8", "meshify.opt.desc.uv", 8),
      new MeshifyClient.Option("uv16", "meshify.opt.desc.uv_default", 16),
      new MeshifyClient.Option("uv32", "meshify.opt.desc.uv", 32),
      new MeshifyClient.Option("uv64", "meshify.opt.desc.uv", 64),
      new MeshifyClient.Option("uv128", "meshify.opt.desc.uv", 128),
      new MeshifyClient.Option("max1024", "meshify.opt.desc.max", 1024),
      new MeshifyClient.Option("max2048", "meshify.opt.desc.max", 2048),
      new MeshifyClient.Option("max4096", "meshify.opt.desc.max_default", 4096),
      new MeshifyClient.Option("max8192", "meshify.opt.desc.max", 8192),
      new MeshifyClient.Option("2", "meshify.opt.desc.scale", 2),
      new MeshifyClient.Option("4", "meshify.opt.desc.scale", 4),
      new MeshifyClient.Option("8", "meshify.opt.desc.scale", 8)
   );

   @Override
   public void onInitializeClient() {
      SelectionRenderer.register();
      ClientLifecycleEvents.CLIENT_STARTED.register(client -> Selection.init(client.runDirectory.toPath()));
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
         ClientCommandManager.literal("pos")
            .then(
               ClientCommandManager.literal("start")
                  .executes(ctx -> setPos(ctx.getSource(), true, null))
                  .then(
                     ClientCommandManager.argument("x", IntegerArgumentType.integer())
                        .then(
                           ClientCommandManager.argument("y", IntegerArgumentType.integer())
                              .then(
                                 ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes(ctx -> setPos(ctx.getSource(), true, posFromArgs(ctx)))
                              )
                        )
                  )
            )
            .then(
               ClientCommandManager.literal("end")
                  .executes(ctx -> setPos(ctx.getSource(), false, null))
                  .then(
                     ClientCommandManager.argument("x", IntegerArgumentType.integer())
                        .then(
                           ClientCommandManager.argument("y", IntegerArgumentType.integer())
                              .then(
                                 ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes(ctx -> setPos(ctx.getSource(), false, posFromArgs(ctx)))
                              )
                        )
                  )
            )
            .then(ClientCommandManager.literal("here").executes(ctx -> here(ctx.getSource())))
            .then(
               ClientCommandManager.literal("expand")
                  .then(ClientCommandManager.literal("vert").executes(ctx -> expandVertical(ctx.getSource())))
                  .then(
                     ClientCommandManager.argument("blocks", IntegerArgumentType.integer(1))
                        .executes(ctx -> resize(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "blocks")))
                  )
            )
            .then(
               ClientCommandManager.literal("shrink")
                  .then(
                     ClientCommandManager.argument("blocks", IntegerArgumentType.integer(1))
                        .executes(ctx -> resize(ctx.getSource(), -IntegerArgumentType.getInteger(ctx, "blocks")))
                  )
            )
            .then(ClientCommandManager.literal("clear").executes(ctx -> {
               Selection.clear();
               ctx.getSource().sendFeedback(Text.translatable("meshify.msg.cleared"));
               return 1;
            }))
            .then(ClientCommandManager.literal("status").executes(ctx -> {
               ctx.getSource().sendFeedback(statusLine());
               return 1;
            }))
            .then(
               ClientCommandManager.literal("export")
                  .executes(ctx -> export(ctx.getSource(), null, null))
                  .then(
                     ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> export(ctx.getSource(), StringArgumentType.getString(ctx, "name"), null))
                        .then(
                           ClientCommandManager.argument("options", StringArgumentType.greedyString())
                              .suggests(MeshifyClient::suggestOptions)
                              .executes(
                                 ctx -> export(
                                    ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "options")
                                 )
                              )
                        )
                  )
            )
      ));
   }

   private static CompletableFuture<Suggestions> suggestOptions(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
      String s = builder.getRemaining();
      int i = s.lastIndexOf(32);
      SuggestionsBuilder suggestionsbuilder = i < 0 ? builder : builder.createOffset(builder.getStart() + i + 1);
      String s1 = suggestionsbuilder.getRemaining().toLowerCase(Locale.ROOT);
      String s2 = i < 0 ? "" : s.substring(0, i).toLowerCase(Locale.ROOT);

      for (MeshifyClient.Option meshifyclient$option : OPTIONS) {
         if (meshifyclient$option.token().startsWith(s1) && (s2.isEmpty() || !(" " + s2 + " ").contains(" " + meshifyclient$option.token() + " "))) {
            suggestionsbuilder.suggest(meshifyclient$option.token(), meshifyclient$option.description());
         }
      }

      return suggestionsbuilder.buildFuture();
   }

   private static BlockPos posFromArgs(CommandContext<FabricClientCommandSource> ctx) {
      return new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"));
   }

   /** The block under the crosshair, or the one the player is standing on when aiming at nothing. */
   private static BlockPos targeted(FabricClientCommandSource source) {
      MinecraftClient minecraftclient = source.getClient();
      return minecraftclient.crosshairTarget instanceof BlockHitResult blockhitresult && minecraftclient.crosshairTarget.getType() == HitResult.Type.BLOCK
         ? blockhitresult.getBlockPos()
         : source.getPlayer().getBlockPos();
   }

   private static int setPos(FabricClientCommandSource source, boolean start, BlockPos explicit) {
      BlockPos blockpos = explicit != null ? explicit : source.getPlayer().getBlockPos();
      Selection.set(start, blockpos, source.getWorld().getRegistryKey());
      Text text = Text.translatable(start ? "meshify.label.start" : "meshify.label.end");
      source.sendFeedback(
         Selection.isComplete()
            ? Text.translatable("meshify.msg.pos_set_volume", text, blockpos.toShortString(), Selection.volume())
            : Text.translatable("meshify.msg.pos_set", text, blockpos.toShortString())
      );
      if (Selection.isComplete()) {
         source.sendFeedback(Text.translatable("meshify.msg.hint"));
      }

      return 1;
   }

   private static int here(FabricClientCommandSource source) {
      BlockPos blockpos = targeted(source);
      Selection.set(true, blockpos, source.getWorld().getRegistryKey());
      Selection.set(false, blockpos, source.getWorld().getRegistryKey());
      source.sendFeedback(Text.translatable("meshify.msg.here", blockpos.toShortString()));
      return 1;
   }

   private static int resize(FabricClientCommandSource source, int blocks) {
      if (!Selection.isComplete()) {
         source.sendError(Text.translatable("meshify.error.no_selection"));
         return 0;
      } else {
         Selection.expand(blocks);
         source.sendFeedback(
            Text.translatable("meshify.msg.resized", Selection.min().toShortString(), Selection.max().toShortString(), Selection.volume())
         );
         return 1;
      }
   }

   private static int expandVertical(FabricClientCommandSource source) {
      if (!Selection.isComplete()) {
         source.sendError(Text.translatable("meshify.error.no_selection"));
         return 0;
      } else {
         ClientWorld clientworld = source.getWorld();
         Selection.expandVertical(clientworld.getBottomY(), clientworld.getBottomY() + clientworld.getHeight() - 1);
         source.sendFeedback(
            Text.translatable("meshify.msg.resized", Selection.min().toShortString(), Selection.max().toShortString(), Selection.volume())
         );
         return 1;
      }
   }

   private static Text statusLine() {
      Object object = Selection.start == null ? Text.translatable("meshify.label.unset") : Selection.start.toShortString();
      Object object1 = Selection.end == null ? Text.translatable("meshify.label.unset") : Selection.end.toShortString();
      return Selection.isComplete()
         ? Text.translatable("meshify.msg.status_volume", object, object1, Selection.volume())
         : Text.translatable("meshify.msg.status", object, object1);
   }

   private static int export(FabricClientCommandSource source, String name, String optionsStr) {
      MinecraftClient minecraftclient = source.getClient();
      if (BUSY.get()) {
         source.sendError(Text.translatable("meshify.error.busy"));
         return 0;
      } else if (!Selection.isComplete()) {
         source.sendError(Text.translatable("meshify.error.no_selection"));
         return 0;
      } else {
         ClientWorld clientworld = source.getWorld();
         if (!Selection.matches(clientworld.getRegistryKey())) {
            source.sendError(Text.translatable("meshify.error.wrong_dimension", Selection.dimension.getValue().toString()));
            return 0;
         }

         long i = Selection.volume();
         if (i > 16000000L) {
            source.sendError(Text.translatable("meshify.error.too_large", i, 16000000L));
            return 0;
         }

         // Everything the user should know before the game locks up for a while, not after.
         int j = RegionExporter.countUnloadedChunks(clientworld, Selection.min(), Selection.max());
         if (j > 0) {
            source.sendFeedback(Text.translatable("meshify.msg.pre_unloaded", j).formatted(Formatting.YELLOW));
         }

         if (i > WARN_VOLUME) {
            source.sendFeedback(Text.translatable("meshify.msg.pre_large", i).formatted(Formatting.YELLOW));
         }

         // Falling back to the previous run's options makes repeat exports one word long.
         boolean flag4 = false;
         if (optionsStr == null || optionsStr.isBlank()) {
            optionsStr = Selection.lastOptions;
            flag4 = !optionsStr.isBlank();
         }

         if (name == null || name.isBlank()) {
            name = "export_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
         }

         name = uniqueName(minecraftclient, name.replaceAll("[^A-Za-z0-9_\\-]", "_"));
         boolean flag = true;
         boolean flag1 = false;
         boolean flag2 = false;
         boolean flag3 = false;
         int k = 1;
         int l = 16;
         int i1 = 4096;

         for (String s : optionsStr.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!s.isEmpty()) {
               switch (s) {
                  case "atlas":
                     flag = true;
                     break;
                  case "separate":
                     flag = false;
                     break;
                  case "weld":
                     flag1 = true;
                     break;
                  case "cull":
                     flag2 = true;
                     break;
                  case "bake":
                     flag3 = true;
                     break;
                  default:
                     try {
                        if (s.startsWith("uv")) {
                           l = Math.max(1, Math.min(256, Integer.parseInt(s.substring(2))));
                           flag3 = true;
                        } else if (s.startsWith("max")) {
                           i1 = Math.max(256, Math.min(16384, Integer.parseInt(s.substring(3))));
                           flag3 = true;
                        } else {
                           k = Math.max(1, Math.min(16, Integer.parseInt(s)));
                        }
                     } catch (NumberFormatException numberformatexception) {
                        source.sendFeedback(Text.translatable("meshify.error.unknown_option", s).formatted(Formatting.YELLOW));
                     }
               }
            }
         }

         Selection.rememberOptions(optionsStr);
         if (flag4) {
            source.sendFeedback(Text.translatable("meshify.msg.reused_options", optionsStr).formatted(Formatting.GRAY));
         }

         source.sendFeedback(Text.translatable("meshify.msg.export_start", i, describeOptions(flag, k, flag3, l, i1, flag1, flag2)));
         ExportOptions exportoptions = new ExportOptions(name, flag, k, flag1, flag2, flag3, l, i1);
         long j1 = System.currentTimeMillis();

         RegionExporter.Scan regionexporter$scan;
         try {
            regionexporter$scan = RegionExporter.scan(minecraftclient, clientworld, Selection.start, Selection.end, exportoptions);
         } catch (Exception exception) {
            source.sendError(Text.translatable("meshify.error.failed", String.valueOf(exception)));
            return 0;
         }

         // Packing the atlas, encoding PNGs and writing the files never touch the world again, so
         // they run off the client thread and the game keeps rendering while they finish.
         BUSY.set(true);
         Thread.ofVirtual().name("meshify-export").start(() -> {
            try {
               RegionExporter.Result regionexporter$result = RegionExporter.finish(minecraftclient, regionexporter$scan, exportoptions);
               report(minecraftclient, regionexporter$result, System.currentTimeMillis() - j1);
            } catch (Exception exception1) {
               post(minecraftclient, Text.translatable("meshify.error.failed", String.valueOf(exception1)).formatted(Formatting.RED));
            } finally {
               BUSY.set(false);
            }
         });
         return 1;
      }
   }

   /** Never clobbers a previous export: {@code shop}, then {@code shop_2}, {@code shop_3}… */
   private static String uniqueName(MinecraftClient mc, String name) {
      Path path = mc.runDirectory.toPath().resolve("meshify");
      String s = name;

      for (int i = 2; Files.exists(path.resolve(s)); i++) {
         s = name + "_" + i;
      }

      return s;
   }

   private static void post(MinecraftClient mc, Text text) {
      mc.execute(() -> {
         if (mc.player != null) {
            mc.player.sendMessage(text, false);
         }
      });
   }

   private static void report(MinecraftClient mc, RegionExporter.Result result, long millis) {
      post(
         mc,
         Text.translatable(
            "meshify.msg.done",
            String.format(Locale.ROOT, "%.1f", millis / 1000.0),
            result.blocksExported(),
            result.entitiesExported(),
            result.facesEmitted(),
            result.outDir().toString()
         )
      );
      BakeCollector.Stats bakecollector$stats = result.bakeStats();
      if (bakecollector$stats != null) {
         double d0 = bakecollector$stats.facesConsumed() > 0
            ? 100.0 * (1.0 - (double)bakecollector$stats.quadsEmitted() / bakecollector$stats.facesConsumed())
            : 0.0;
         post(
            mc,
            Text.translatable(
                  "meshify.msg.bake_summary",
                  bakecollector$stats.facesConsumed(),
                  bakecollector$stats.quadsEmitted(),
                  String.format(Locale.ROOT, "%.1f", d0),
                  bakecollector$stats.sheets(),
                  String.format(Locale.ROOT, "%.1f", bakecollector$stats.bakedPixels() / 1000000.0)
               )
               .formatted(Formatting.AQUA)
         );
      }

      MeshBuilder.OptStats meshbuilder$optstats = result.optStats();
      if (meshbuilder$optstats != null) {
         post(
            mc,
            Text.translatable(
                  "meshify.msg.weld_summary",
                  result.facesBeforeOptimize(),
                  result.facesEmitted(),
                  meshbuilder$optstats.degenerateRemoved(),
                  meshbuilder$optstats.duplicateRemoved(),
                  meshbuilder$optstats.weldGrid()
               )
               .formatted(Formatting.AQUA)
         );
      }

      if (!result.skippedTypes().isEmpty()) {
         post(mc, Text.translatable("meshify.msg.skipped", String.join(", ", result.skippedTypes())).formatted(Formatting.YELLOW));
      }

      for (String s : result.warnings()) {
         post(mc, Text.translatable("meshify.msg.warning", s).formatted(Formatting.YELLOW));
      }
   }

   private static Text describeOptions(boolean atlas, int texScale, boolean bake, int bakeUv, int bakeMax, boolean weld, boolean cull) {
      List<Text> list = new ArrayList<>();
      list.add(Text.translatable(atlas ? "meshify.opt.atlas" : "meshify.opt.separate"));
      if (texScale > 1) {
         list.add(Text.translatable("meshify.opt.scale", texScale));
      }

      if (bake) {
         list.add(Text.translatable("meshify.opt.bake", bakeUv, bakeMax));
      }

      if (weld) {
         list.add(Text.translatable("meshify.opt.weld"));
      }

      if (cull) {
         list.add(Text.translatable("meshify.opt.cull"));
      }

      MutableText mutabletext = Text.empty();

      for (int i = 0; i < list.size(); i++) {
         if (i > 0) {
            mutabletext.append(", ");
         }

         mutabletext.append(list.get(i));
      }

      return mutabletext;
   }

   private record Option(String token, String descKey, Object descArg) {
      Text description() {
         return this.descArg == null ? Text.translatable(this.descKey) : Text.translatable(this.descKey, this.descArg);
      }
   }
}
