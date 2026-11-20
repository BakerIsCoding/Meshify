package com.norwood.meshify;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.norwood.meshify.MeshifyActions.Feedback;
import com.norwood.meshify.Selection.Corner;
import com.norwood.meshify.export.RegionExporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class MeshifyClient implements ClientModInitializer {
   private static final List<OptionSuggestion> OPTION_SUGGESTIONS = optionSuggestions();

   @Override
   public void onInitializeClient() {
      SelectionRenderer.register();
      MeshifyScreen.register();
      ExportJob.register();
      ClientLifecycleEvents.CLIENT_STARTED.register(client -> Selection.loadSession(RegionExporter.exportRoot(client)));
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
         ClientCommandManager.literal("meshify")
            .then(ClientCommandManager.literal("gui").executes(ctx -> openMenu(ctx.getSource().getClient())))
            .then(cornerCommand("start", Corner.START))
            .then(cornerCommand("end", Corner.END))
            .then(ClientCommandManager.literal("here").executes(always(MeshifyActions::selectSingleBlock)))
            .then(
               ClientCommandManager.literal("expand")
                  .then(ClientCommandManager.literal("vert").executes(attempt(MeshifyActions::expandVertical)))
                  .then(resizeArgument(1))
            )
            .then(ClientCommandManager.literal("shrink").then(resizeArgument(-1)))
            .then(ClientCommandManager.literal("trim").executes(attempt(MeshifyActions::trim)))
            .then(ClientCommandManager.literal("clear").executes(always(MeshifyActions::clear)))
            .then(ClientCommandManager.literal("status").executes(ctx -> {
               ctx.getSource().sendFeedback(MeshifyActions.statusLine());
               return Command.SINGLE_SUCCESS;
            }))
            .then(ClientCommandManager.literal("cancel").executes(attempt(MeshifyActions::cancel)))
            .then(ClientCommandManager.literal("open").executes(always(MeshifyActions::openFolder)))
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

   // The export options offered as suggestions: every flag and every size the menu offers except the default scale.
   private static List<OptionSuggestion> optionSuggestions() {
      List<OptionSuggestion> suggestions = new ArrayList<>();

      for (String flag : List.of("atlas", "separate", "bake", "weld", "cull")) {
         suggestions.add(new OptionSuggestion(flag, "meshify.opt.desc." + flag, null));
      }

      for (int size : MeshifyActions.BAKE_UV_SIZES) {
         suggestions.add(new OptionSuggestion("uv" + size, size == MeshifyActions.DEFAULT_BAKE_UV_SIZE ? "meshify.opt.desc.uv_default" : "meshify.opt.desc.uv", size));
      }

      for (int size : MeshifyActions.BAKE_MAX_ATLAS_SIZES) {
         suggestions.add(
            new OptionSuggestion("max" + size, size == MeshifyActions.DEFAULT_BAKE_MAX_ATLAS ? "meshify.opt.desc.max_default" : "meshify.opt.desc.max", size)
         );
      }

      for (int scale : MeshifyActions.TEXTURE_SCALES) {
         if (scale > 1) {
            suggestions.add(new OptionSuggestion(String.valueOf(scale), "meshify.opt.desc.scale", scale));
         }
      }

      return suggestions;
   }

   // Brigadier counts 0 as a refused command.
   private static int commandResult(boolean succeeded) {
      return succeeded ? Command.SINGLE_SUCCESS : 0;
   }

   // A command that always goes through.
   private static Command<FabricClientCommandSource> always(Consumer<Feedback> action) {
      return ctx -> {
         action.accept(Feedback.of(ctx.getSource()));
         return Command.SINGLE_SUCCESS;
      };
   }

   // A command the action may refuse, reporting why.
   private static Command<FabricClientCommandSource> attempt(Predicate<Feedback> action) {
      return ctx -> commandResult(action.test(Feedback.of(ctx.getSource())));
   }

   private static int openMenu(Minecraft client) {
      // Deferred: the chat screen closes right after the command and would take the menu with it.
      client.schedule(() -> client.setScreen(new MeshifyScreen()));
      return Command.SINGLE_SUCCESS;
   }

   private static int export(CommandContext<FabricClientCommandSource> ctx, String name, String rawOptions) {
      return commandResult(MeshifyActions.export(Feedback.of(ctx.getSource()), name, rawOptions));
   }

   // <literal> sets the corner where the player stands, <literal> x y z sets it there.
   private static LiteralArgumentBuilder<FabricClientCommandSource> cornerCommand(String literal, Corner corner) {
      return ClientCommandManager.literal(literal)
         .executes(ctx -> setCorner(ctx.getSource(), corner, ctx.getSource().getPlayer().blockPosition()))
         .then(
            ClientCommandManager.argument("x", IntegerArgumentType.integer())
               .then(
                  ClientCommandManager.argument("y", IntegerArgumentType.integer())
                     .then(ClientCommandManager.argument("z", IntegerArgumentType.integer()).executes(ctx -> setCorner(ctx.getSource(), corner, positionFromArguments(ctx))))
               )
         );
   }

   // <blocks> [side], shared by expand (sign 1) and shrink (sign -1).
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
      return commandResult(MeshifyActions.resize(Feedback.of(ctx.getSource()), blocks, side));
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

      for (OptionSuggestion suggestion : OPTION_SUGGESTIONS) {
         if (suggestion.token().startsWith(partial) && (alreadyTyped.isEmpty() || !(" " + alreadyTyped + " ").contains(" " + suggestion.token() + " "))) {
            lastWord.suggest(suggestion.token(), suggestion.description());
         }
      }

      return lastWord.buildFuture();
   }

   private static BlockPos positionFromArguments(CommandContext<FabricClientCommandSource> ctx) {
      return new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"));
   }

   private static int setCorner(FabricClientCommandSource source, Corner corner, BlockPos pos) {
      MeshifyActions.setCorner(Feedback.of(source), corner, pos);
      if (Selection.isComplete()) {
         source.sendFeedback(Component.translatable("meshify.msg.hint"));
      }

      return Command.SINGLE_SUCCESS;
   }

   private record OptionSuggestion(String token, String descriptionKey, Object descriptionArgument) {
      Component description() {
         return this.descriptionArgument == null ? Component.translatable(this.descriptionKey) : Component.translatable(this.descriptionKey, this.descriptionArgument);
      }
   }
}
