package com.norwood.pos2obj;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.norwood.pos2obj.export.BakeCollector;
import com.norwood.pos2obj.export.ExportOptions;
import com.norwood.pos2obj.export.MeshBuilder;
import com.norwood.pos2obj.export.RegionExporter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class Pos2ObjClient implements ClientModInitializer {
   private static final int DEFAULT_BAKE_UV = 16;
   private static final int DEFAULT_BAKE_MAX = 4096;
   private static final List<Pos2ObjClient.Option> OPTIONS = List.of(
      new Pos2ObjClient.Option("atlas", "pos2obj.opt.desc.atlas", null),
      new Pos2ObjClient.Option("separate", "pos2obj.opt.desc.separate", null),
      new Pos2ObjClient.Option("bake", "pos2obj.opt.desc.bake", null),
      new Pos2ObjClient.Option("weld", "pos2obj.opt.desc.weld", null),
      new Pos2ObjClient.Option("cull", "pos2obj.opt.desc.cull", null),
      new Pos2ObjClient.Option("uv8", "pos2obj.opt.desc.uv", 8),
      new Pos2ObjClient.Option("uv16", "pos2obj.opt.desc.uv_default", 16),
      new Pos2ObjClient.Option("uv32", "pos2obj.opt.desc.uv", 32),
      new Pos2ObjClient.Option("uv64", "pos2obj.opt.desc.uv", 64),
      new Pos2ObjClient.Option("uv128", "pos2obj.opt.desc.uv", 128),
      new Pos2ObjClient.Option("max1024", "pos2obj.opt.desc.max", 1024),
      new Pos2ObjClient.Option("max2048", "pos2obj.opt.desc.max", 2048),
      new Pos2ObjClient.Option("max4096", "pos2obj.opt.desc.max_default", 4096),
      new Pos2ObjClient.Option("max8192", "pos2obj.opt.desc.max", 8192),
      new Pos2ObjClient.Option("2", "pos2obj.opt.desc.scale", 2),
      new Pos2ObjClient.Option("4", "pos2obj.opt.desc.scale", 4),
      new Pos2ObjClient.Option("8", "pos2obj.opt.desc.scale", 8)
   );

   @Override
   public void onInitializeClient() {
      ClientCommandRegistrationCallback.EVENT
         .register(
            (ClientCommandRegistrationCallback)(dispatcher, registryAccess) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal(
                                 "pos"
                              )
                              .then(
                                 ((LiteralArgumentBuilder)ClientCommandManager.literal("start")
                                       .executes(ctx -> setPos((FabricClientCommandSource)ctx.getSource(), true, null)))
                                    .then(
                                       ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                          .then(
                                             ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                .then(
                                                   ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                                      .executes(ctx -> setPos((FabricClientCommandSource)ctx.getSource(), true, posFromArgs(ctx)))
                                                )
                                          )
                                    )
                              ))
                           .then(
                              ((LiteralArgumentBuilder)ClientCommandManager.literal("end")
                                    .executes(ctx -> setPos((FabricClientCommandSource)ctx.getSource(), false, null)))
                                 .then(
                                    ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                       .then(
                                          ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                             .then(
                                                ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                                   .executes(ctx -> setPos((FabricClientCommandSource)ctx.getSource(), false, posFromArgs(ctx)))
                                             )
                                       )
                                 )
                           ))
                        .then(ClientCommandManager.literal("clear").executes(ctx -> {
                           Selection.clear();
                           ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Text.translatable("pos2obj.msg.cleared"));
                           return 1;
                        })))
                     .then(ClientCommandManager.literal("status").executes(ctx -> {
                        ((FabricClientCommandSource)ctx.getSource()).sendFeedback(statusLine());
                        return 1;
                     })))
                  .then(
                     ((LiteralArgumentBuilder)ClientCommandManager.literal("export")
                           .executes(ctx -> export((FabricClientCommandSource)ctx.getSource(), null, "")))
                        .then(
                           ((RequiredArgumentBuilder)ClientCommandManager.argument("name", StringArgumentType.word())
                                 .executes(ctx -> export((FabricClientCommandSource)ctx.getSource(), StringArgumentType.getString(ctx, "name"), "")))
                              .then(
                                 ClientCommandManager.argument("options", StringArgumentType.greedyString())
                                    .suggests(Pos2ObjClient::suggestOptions)
                                    .executes(
                                       ctx -> export(
                                          (FabricClientCommandSource)ctx.getSource(),
                                          StringArgumentType.getString(ctx, "name"),
                                          StringArgumentType.getString(ctx, "options")
                                       )
                                    )
                              )
                        )
                  )
            )
         );
   }

   private static CompletableFuture<Suggestions> suggestOptions(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
      String s = builder.getRemaining();
      int i = s.lastIndexOf(32);
      SuggestionsBuilder suggestionsbuilder = i < 0 ? builder : builder.createOffset(builder.getStart() + i + 1);
      String s1 = suggestionsbuilder.getRemaining().toLowerCase(Locale.ROOT);
      String s2 = i < 0 ? "" : s.substring(0, i).toLowerCase(Locale.ROOT);

      for (Pos2ObjClient.Option pos2objclient$option : OPTIONS) {
         if (pos2objclient$option.token().startsWith(s1) && (s2.isEmpty() || !(" " + s2 + " ").contains(" " + pos2objclient$option.token() + " "))) {
            suggestionsbuilder.suggest(pos2objclient$option.token(), pos2objclient$option.description());
         }
      }

      return suggestionsbuilder.buildFuture();
   }

   private static BlockPos posFromArgs(CommandContext<FabricClientCommandSource> ctx) {
      return new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"));
   }

   private static int setPos(FabricClientCommandSource source, boolean start, BlockPos explicit) {
      BlockPos blockpos = explicit != null ? explicit : source.getPlayer().getBlockPos();
      if (start) {
         Selection.start = blockpos;
      } else {
         Selection.end = blockpos;
      }

      Text text = Text.translatable(start ? "pos2obj.label.start" : "pos2obj.label.end");
      source.sendFeedback(
         Selection.isComplete()
            ? Text.translatable("pos2obj.msg.pos_set_volume", text, blockpos.toShortString(), Selection.volume())
            : Text.translatable("pos2obj.msg.pos_set", text, blockpos.toShortString())
      );
      if (Selection.isComplete()) {
         source.sendFeedback(Text.translatable("pos2obj.msg.hint"));
      }

      return 1;
   }

   private static Text statusLine() {
      Object object = Selection.start == null ? Text.translatable("pos2obj.label.unset") : Selection.start.toShortString();
      Object object1 = Selection.end == null ? Text.translatable("pos2obj.label.unset") : Selection.end.toShortString();
      return Selection.isComplete()
         ? Text.translatable("pos2obj.msg.status_volume", object, object1, Selection.volume())
         : Text.translatable("pos2obj.msg.status", object, object1);
   }

   private static int export(FabricClientCommandSource source, String name, String optionsStr) {
      if (!Selection.isComplete()) {
         source.sendError(Text.translatable("pos2obj.error.no_selection"));
         return 0;
      } else {
         long i = Selection.volume();
         if (i > 16000000L) {
            source.sendError(Text.translatable("pos2obj.error.too_large", i, 16000000L));
            return 0;
         } else {
            if (name == null || name.isBlank()) {
               name = "export_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            }

            name = name.replaceAll("[^A-Za-z0-9_\\-]", "_");
            boolean flag = true;
            boolean flag1 = false;
            boolean flag2 = false;
            boolean flag3 = false;
            int j = 1;
            int k = 16;
            int l = 4096;

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
                              k = Math.max(1, Math.min(256, Integer.parseInt(s.substring(2))));
                              flag3 = true;
                           } else if (s.startsWith("max")) {
                              l = Math.max(256, Math.min(16384, Integer.parseInt(s.substring(3))));
                              flag3 = true;
                           } else {
                              j = Math.max(1, Math.min(16, Integer.parseInt(s)));
                           }
                        } catch (NumberFormatException numberformatexception) {
                           source.sendFeedback(Text.translatable("pos2obj.error.unknown_option", s).formatted(Formatting.YELLOW));
                        }
                  }
               }
            }

            source.sendFeedback(Text.translatable("pos2obj.msg.export_start", i, describeOptions(flag, j, flag3, k, l, flag1, flag2)));
            long i1 = System.currentTimeMillis();

            try {
               RegionExporter.Result regionexporter$result = RegionExporter.export(
                  source.getClient(), source.getWorld(), Selection.start, Selection.end, new ExportOptions(name, flag, j, flag1, flag2, flag3, k, l)
               );
               long j1 = System.currentTimeMillis() - i1;
               source.sendFeedback(
                  Text.translatable(
                     "pos2obj.msg.done",
                     String.format(Locale.ROOT, "%.1f", j1 / 1000.0),
                     regionexporter$result.blocksExported(),
                     regionexporter$result.entitiesExported(),
                     regionexporter$result.facesEmitted(),
                     regionexporter$result.outDir().toString()
                  )
               );
               BakeCollector.Stats bakecollector$stats = regionexporter$result.bakeStats();
               if (bakecollector$stats != null) {
                  double d0 = bakecollector$stats.facesConsumed() > 0
                     ? 100.0 * (1.0 - (double)bakecollector$stats.quadsEmitted() / bakecollector$stats.facesConsumed())
                     : 0.0;
                  source.sendFeedback(
                     Text.translatable(
                           "pos2obj.msg.bake_summary",
                           bakecollector$stats.facesConsumed(),
                           bakecollector$stats.quadsEmitted(),
                           String.format(Locale.ROOT, "%.1f", d0),
                           bakecollector$stats.sheets(),
                           String.format(Locale.ROOT, "%.1f", bakecollector$stats.bakedPixels() / 1000000.0)
                        )
                        .formatted(Formatting.AQUA)
                  );
               }

               MeshBuilder.OptStats meshbuilder$optstats = regionexporter$result.optStats();
               if (meshbuilder$optstats != null) {
                  source.sendFeedback(
                     Text.translatable(
                           "pos2obj.msg.weld_summary",
                           regionexporter$result.facesBeforeOptimize(),
                           regionexporter$result.facesEmitted(),
                           meshbuilder$optstats.degenerateRemoved(),
                           meshbuilder$optstats.duplicateRemoved(),
                           meshbuilder$optstats.weldGrid()
                        )
                        .formatted(Formatting.AQUA)
                  );
               }

               if (!regionexporter$result.skippedTypes().isEmpty()) {
                  source.sendFeedback(
                     Text.translatable("pos2obj.msg.skipped", String.join(", ", regionexporter$result.skippedTypes())).formatted(Formatting.YELLOW)
                  );
               }

               for (String s1 : regionexporter$result.warnings()) {
                  source.sendFeedback(Text.translatable("pos2obj.msg.warning", s1).formatted(Formatting.YELLOW));
               }
            } catch (Exception exception) {
               source.sendError(Text.translatable("pos2obj.error.failed", String.valueOf(exception)));
            }

            return 1;
         }
      }
   }

   private static Text describeOptions(boolean atlas, int texScale, boolean bake, int bakeUv, int bakeMax, boolean weld, boolean cull) {
      List<Text> list = new ArrayList<>();
      list.add(Text.translatable(atlas ? "pos2obj.opt.atlas" : "pos2obj.opt.separate"));
      if (texScale > 1) {
         list.add(Text.translatable("pos2obj.opt.scale", texScale));
      }

      if (bake) {
         list.add(Text.translatable("pos2obj.opt.bake", bakeUv, bakeMax));
      }

      if (weld) {
         list.add(Text.translatable("pos2obj.opt.weld"));
      }

      if (cull) {
         list.add(Text.translatable("pos2obj.opt.cull"));
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
