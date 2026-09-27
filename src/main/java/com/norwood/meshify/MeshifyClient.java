package com.norwood.meshify;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.norwood.meshify.MeshifyActions.Feedback;
import com.norwood.meshify.Selection.Corner;
import com.norwood.meshify.export.RegionExporter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class MeshifyClient implements ClientModInitializer {
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
      MeshifyScreen.register();
      ExportJob.register();
      ClientLifecycleEvents.CLIENT_STARTED.register(client -> Selection.init(RegionExporter.exportRoot(client)));
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
         ClientCommandManager.literal("meshify")
            .then(ClientCommandManager.literal("gui").executes(ctx -> openMenu(ctx.getSource().getClient())))
            .then(
               ClientCommandManager.literal("start")
                  .executes(ctx -> setPos(ctx.getSource(), Corner.START, null))
                  .then(
                     ClientCommandManager.argument("x", IntegerArgumentType.integer())
                        .then(
                           ClientCommandManager.argument("y", IntegerArgumentType.integer())
                              .then(
                                 ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes(ctx -> setPos(ctx.getSource(), Corner.START, posFromArgs(ctx)))
                              )
                        )
                  )
            )
            .then(
               ClientCommandManager.literal("end")
                  .executes(ctx -> setPos(ctx.getSource(), Corner.END, null))
                  .then(
                     ClientCommandManager.argument("x", IntegerArgumentType.integer())
                        .then(
                           ClientCommandManager.argument("y", IntegerArgumentType.integer())
                              .then(
                                 ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes(ctx -> setPos(ctx.getSource(), Corner.END, posFromArgs(ctx)))
                              )
                        )
                  )
            )
            .then(ClientCommandManager.literal("here").executes(ctx -> {
               MeshifyActions.selectSingleBlock(Feedback.of(ctx.getSource()));
               return 1;
            }))
            .then(
               ClientCommandManager.literal("expand")
                  .then(
                     ClientCommandManager.literal("vert")
                        .executes(ctx -> result(MeshifyActions.expandVertical(Feedback.of(ctx.getSource()))))
                  )
                  .then(resizeArgument(1))
            )
            .then(ClientCommandManager.literal("shrink").then(resizeArgument(-1)))
            .then(
               ClientCommandManager.literal("trim")
                  .executes(ctx -> result(MeshifyActions.trim(Feedback.of(ctx.getSource()))))
            )
            .then(ClientCommandManager.literal("clear").executes(ctx -> {
               MeshifyActions.clear(Feedback.of(ctx.getSource()));
               return 1;
            }))
            .then(ClientCommandManager.literal("status").executes(ctx -> {
               ctx.getSource().sendFeedback(MeshifyActions.statusLine());
               return 1;
            }))
            .then(ClientCommandManager.literal("cancel").executes(ctx -> result(MeshifyActions.cancel(Feedback.of(ctx.getSource())))))
            .then(ClientCommandManager.literal("open").executes(ctx -> {
               MeshifyActions.openFolder(Feedback.of(ctx.getSource()));
               return 1;
            }))
            .then(
               ClientCommandManager.literal("export")
                  .executes(ctx -> export(ctx, null, null))
                  .then(
                     ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> export(ctx, StringArgumentType.getString(ctx, "name"), null))
                        .then(
                           ClientCommandManager.argument("options", StringArgumentType.greedyString())
                              .suggests(MeshifyClient::suggestOptions)
                              .executes(ctx -> export(ctx, StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "options")))
                        )
                  )
            )
      ));
   }

   /** Brigadier's convention: 1 when the command did something, 0 when it was refused. */
   private static int result(boolean succeeded) {
      return succeeded ? 1 : 0;
   }

   private static int openMenu(MinecraftClient client) {
      // Deferred: the chat screen closes right after the command and would take the menu with it.
      client.send(() -> client.setScreen(new MeshifyScreen()));
      return 1;
   }

   private static int export(CommandContext<FabricClientCommandSource> ctx, String name, String options) {
      return result(MeshifyActions.export(Feedback.of(ctx.getSource()), name, options));
   }

   /** {@code <blocks> [side]}, shared by expand ({@code sign} 1) and shrink ({@code sign} -1). */
   private static RequiredArgumentBuilder<FabricClientCommandSource, Integer> resizeArgument(int sign) {
      return ClientCommandManager.argument("blocks", IntegerArgumentType.integer(1))
         .executes(ctx -> resize(ctx, sign, null))
         .then(
            ClientCommandManager.argument("side", StringArgumentType.word())
               .suggests((ctx, builder) -> suggestMatching(MeshifyActions.SIDES, builder))
               .executes(ctx -> resize(ctx, sign, StringArgumentType.getString(ctx, "side")))
         );
   }

   private static int resize(CommandContext<FabricClientCommandSource> ctx, int sign, String side) {
      int blocks = sign * IntegerArgumentType.getInteger(ctx, "blocks");
      return result(MeshifyActions.resize(Feedback.of(ctx.getSource()), blocks, side));
   }

   private static CompletableFuture<Suggestions> suggestMatching(List<String> candidates, SuggestionsBuilder builder) {
      String typed = builder.getRemaining().toLowerCase(Locale.ROOT);

      for (String candidate : candidates) {
         if (candidate.startsWith(typed)) {
            builder.suggest(candidate);
         }
      }

      return builder.buildFuture();
   }

   private static CompletableFuture<Suggestions> suggestOptions(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
      String typed = builder.getRemaining();
      int lastSpace = typed.lastIndexOf(' ');
      SuggestionsBuilder lastWord = lastSpace < 0 ? builder : builder.createOffset(builder.getStart() + lastSpace + 1);
      String partial = lastWord.getRemaining().toLowerCase(Locale.ROOT);
      String alreadyTyped = lastSpace < 0 ? "" : typed.substring(0, lastSpace).toLowerCase(Locale.ROOT);

      for (MeshifyClient.Option option : OPTIONS) {
         if (option.token().startsWith(partial) && (alreadyTyped.isEmpty() || !(" " + alreadyTyped + " ").contains(" " + option.token() + " "))) {
            lastWord.suggest(option.token(), option.description());
         }
      }

      return lastWord.buildFuture();
   }

   private static BlockPos posFromArgs(CommandContext<FabricClientCommandSource> ctx) {
      return new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"));
   }

   private static int setPos(FabricClientCommandSource source, Corner corner, BlockPos explicit) {
      BlockPos pos = explicit != null ? explicit : source.getPlayer().getBlockPos();
      MeshifyActions.setCorner(Feedback.of(source), corner, pos);
      if (Selection.isComplete()) {
         source.sendFeedback(Text.translatable("meshify.msg.hint"));
      }

      return 1;
   }

   private record Option(String token, String descKey, Object descArg) {
      Text description() {
         return this.descArg == null ? Text.translatable(this.descKey) : Text.translatable(this.descKey, this.descArg);
      }
   }
}
