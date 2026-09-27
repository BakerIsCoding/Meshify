package com.norwood.meshify;

import com.norwood.meshify.Selection.Corner;
import com.norwood.meshify.export.ExportOptions;
import com.norwood.meshify.export.RegionExporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Actions shared by the /meshify commands and the menu, so both behave the same. */
final class MeshifyActions {
   static final String ALL_SIDES = "all";
   /** Stands for whichever way the player is looking, up and down included. */
   static final String FACING = "facing";
   static final List<String> SIDES = List.of(ALL_SIDES, "up", "down", "north", "south", "east", "west", FACING);

   /** The choices the menu cycles through and the command suggests; the parser accepts anything in range. */
   static final List<Integer> TEXTURE_SCALES = List.of(1, 2, 4, 8);
   static final List<Integer> BAKE_UV_SIZES = List.of(8, 16, 32, 64, 128);
   static final List<Integer> BAKE_MAX_ATLAS_SIZES = List.of(1024, 2048, 4096, 8192);
   static final int DEFAULT_BAKE_UV_SIZE = 16;
   static final int DEFAULT_BAKE_MAX_ATLAS = 4096;

   private static final int HIGHEST_TEXTURE_SCALE = 16;
   private static final int HIGHEST_BAKE_UV_SIZE = 256;
   private static final int LOWEST_BAKE_MAX_ATLAS = 256;
   private static final int HIGHEST_BAKE_MAX_ATLAS = 16384;
   private static final long WARN_ABOVE_VOLUME = 2000000L;

   private MeshifyActions() {
   }

   /** Where an action reports back: the chat for commands, the status line for the menu. */
   record Feedback(Consumer<Text> onInfo, Consumer<Text> onError) {
      static final Feedback IGNORE = new Feedback(text -> {
      }, text -> {
      });

      static Feedback of(FabricClientCommandSource source) {
         return new Feedback(source::sendFeedback, source::sendError);
      }

      void info(Text text) {
         this.onInfo.accept(text);
      }

      void error(Text text) {
         this.onError.accept(text);
      }
   }

   /** The block under the crosshair, or the one the player is standing on when aiming at nothing. */
   static BlockPos targetedBlock(MinecraftClient client) {
      return client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
         ? hit.getBlockPos()
         : client.player.getBlockPos();
   }

   /** A corner's coordinates, or "unset". */
   static Text describeCorner(BlockPos pos) {
      return pos == null ? Text.translatable("meshify.label.unset") : Text.literal(pos.toShortString());
   }

   static void setCorner(Feedback feedback, Corner corner, BlockPos pos) {
      Selection.set(corner, pos, MinecraftClient.getInstance().world.getRegistryKey());
      Text label = Text.translatable(corner.labelKey);
      feedback.info(
         Selection.isComplete()
            ? Text.translatable("meshify.msg.pos_set_volume", label, pos.toShortString(), Selection.describeSize(), Selection.volume())
            : Text.translatable("meshify.msg.pos_set", label, pos.toShortString())
      );
   }

   static void selectSingleBlock(Feedback feedback) {
      MinecraftClient client = MinecraftClient.getInstance();
      BlockPos pos = targetedBlock(client);
      Selection.set(Corner.START, pos, client.world.getRegistryKey());
      Selection.set(Corner.END, pos, client.world.getRegistryKey());
      feedback.info(Text.translatable("meshify.msg.here", pos.toShortString()));
   }

   static void clear(Feedback feedback) {
      Selection.clear();
      feedback.info(Text.translatable("meshify.msg.cleared"));
   }

   /** Grows (or, for negative {@code blocks}, shrinks) every side, or only {@code sideName}. */
   static boolean resize(Feedback feedback, int blocks, String sideName) {
      if (!requireSelection(feedback)) {
         return false;
      }

      if (sideName == null || sideName.equalsIgnoreCase(ALL_SIDES)) {
         Selection.expand(blocks);
      } else {
         Direction side = parseSide(sideName);
         if (side == null) {
            feedback.error(Text.translatable("meshify.error.unknown_direction", sideName));
            return false;
         }

         Selection.expand(blocks, side);
      }

      feedback.info(resizedMessage());
      return true;
   }

   private static Direction parseSide(String name) {
      String side = name.toLowerCase(Locale.ROOT);
      return side.equals(FACING) ? Direction.getEntityFacingOrder(MinecraftClient.getInstance().player)[0] : Direction.byId(side);
   }

   static boolean expandVertical(Feedback feedback) {
      if (!requireSelection(feedback)) {
         return false;
      }

      ClientWorld world = MinecraftClient.getInstance().world;
      Selection.expandVertical(world.getBottomY(), world.getBottomY() + world.getHeight() - 1);
      feedback.info(resizedMessage());
      return true;
   }

   /** Shrinks the region to the blocks actually in it, dropping the air around them. */
   static boolean trim(Feedback feedback) {
      ClientWorld world = MinecraftClient.getInstance().world;
      if (!requireSelection(feedback) || !requireSameDimension(feedback, world)) {
         return false;
      }

      int unloadedChunks = RegionExporter.countUnloadedChunks(world, Selection.min(), Selection.max());
      if (unloadedChunks > 0) {
         feedback.error(Text.translatable("meshify.error.trim_unloaded", unloadedChunks));
         return false;
      }

      Optional<BlockBox> bounds = RegionExporter.solidBounds(world, Selection.min(), Selection.max());
      if (bounds.isEmpty()) {
         feedback.error(Text.translatable("meshify.error.trim_empty"));
         return false;
      }

      BlockBox box = bounds.get();
      Selection.setBounds(new BlockPos(box.getMinX(), box.getMinY(), box.getMinZ()), new BlockPos(box.getMaxX(), box.getMaxY(), box.getMaxZ()));
      feedback.info(resizedMessage());
      return true;
   }

   private static boolean requireSelection(Feedback feedback) {
      if (Selection.isComplete()) {
         return true;
      }

      feedback.error(Text.translatable("meshify.error.no_selection"));
      return false;
   }

   private static boolean requireSameDimension(Feedback feedback, ClientWorld world) {
      if (Selection.matchesDimension(world.getRegistryKey())) {
         return true;
      }

      feedback.error(Text.translatable("meshify.error.wrong_dimension", Selection.dimension.getValue().toString()));
      return false;
   }

   private static Text resizedMessage() {
      return Text.translatable(
         "meshify.msg.resized", Selection.min().toShortString(), Selection.max().toShortString(), Selection.describeSize(), Selection.volume()
      );
   }

   static Text statusLine() {
      Text start = describeCorner(Selection.start);
      Text end = describeCorner(Selection.end);
      return Selection.isComplete()
         ? Text.translatable("meshify.msg.status_volume", start, end, Selection.describeSize(), Selection.volume())
         : Text.translatable("meshify.msg.status", start, end);
   }

   static boolean cancel(Feedback feedback) {
      if (!ExportJob.cancel()) {
         feedback.error(Text.translatable("meshify.error.not_running"));
         return false;
      }

      feedback.info(Text.translatable("meshify.msg.cancelling"));
      return true;
   }

   static void openFolder(Feedback feedback) {
      Path root = RegionExporter.exportRoot(MinecraftClient.getInstance());
      root.toFile().mkdirs();
      Util.getOperatingSystem().open(root);
      feedback.info(Text.translatable("meshify.msg.open_folder", ExportJob.folderLink(root)));
   }

   /** Starts an export of the selection; a blank {@code rawOptions} reuses the previous run's options. */
   static boolean export(Feedback feedback, String name, String rawOptions) {
      if (ExportJob.isRunning()) {
         feedback.error(Text.translatable("meshify.error.busy"));
         return false;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (!requireSelection(feedback) || !requireSameDimension(feedback, client.world)) {
         return false;
      }

      long volume = Selection.volume();
      if (volume > RegionExporter.MAX_VOLUME) {
         feedback.error(Text.translatable("meshify.error.too_large", volume, RegionExporter.MAX_VOLUME));
         return false;
      }

      int unloadedChunks = RegionExporter.countUnloadedChunks(client.world, Selection.min(), Selection.max());
      if (unloadedChunks > 0) {
         feedback.info(Text.translatable("meshify.msg.pre_unloaded", unloadedChunks).formatted(Formatting.YELLOW));
      }

      if (volume > WARN_ABOVE_VOLUME) {
         feedback.info(Text.translatable("meshify.msg.pre_large", volume).formatted(Formatting.YELLOW));
      }

      boolean reusingOptions = false;
      if (rawOptions == null || rawOptions.isBlank()) {
         rawOptions = Selection.lastOptions;
         reusingOptions = !rawOptions.isBlank();
      }

      if (name == null || name.isBlank()) {
         name = "export_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
      }

      ExportOptions options = parseOptions(uniqueName(client, sanitizeName(name)), rawOptions, feedback);
      Selection.rememberOptions(rawOptions);
      if (reusingOptions) {
         feedback.info(Text.translatable("meshify.msg.reused_options", rawOptions).formatted(Formatting.GRAY));
      }

      feedback.info(Text.translatable("meshify.msg.export_start", volume, describeOptions(options)));
      ExportJob.start(Selection.start, Selection.end, options);
      return true;
   }

   /** The export folder is named after it, so only characters every file system accepts survive. */
   static String sanitizeName(String name) {
      return name.replaceAll("[^A-Za-z0-9_\\-]", "_");
   }

   /** Unknown tokens only get a warning, so a typo does not throw away the rest of the command. */
   static ExportOptions parseOptions(String name, String rawOptions, Feedback feedback) {
      boolean mergeAtlas = true;
      boolean weldVertices = false;
      boolean aggressiveCull = false;
      boolean bake = false;
      int textureScale = 1;
      int bakeUvSize = DEFAULT_BAKE_UV_SIZE;
      int bakeMaxAtlas = DEFAULT_BAKE_MAX_ATLAS;

      for (String token : rawOptions.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
         if (token.isEmpty()) {
            continue;
         }

         switch (token) {
            case "atlas" -> mergeAtlas = true;
            case "separate" -> mergeAtlas = false;
            case "weld" -> weldVertices = true;
            case "cull" -> aggressiveCull = true;
            case "bake" -> bake = true;
            default -> {
               try {
                  if (token.startsWith("uv")) {
                     bakeUvSize = Math.clamp(Integer.parseInt(token.substring(2)), 1, HIGHEST_BAKE_UV_SIZE);
                     bake = true;
                  } else if (token.startsWith("max")) {
                     bakeMaxAtlas = Math.clamp(Integer.parseInt(token.substring(3)), LOWEST_BAKE_MAX_ATLAS, HIGHEST_BAKE_MAX_ATLAS);
                     bake = true;
                  } else {
                     textureScale = Math.clamp(Integer.parseInt(token), 1, HIGHEST_TEXTURE_SCALE);
                  }
               } catch (NumberFormatException notANumber) {
                  feedback.info(Text.translatable("meshify.error.unknown_option", token).formatted(Formatting.YELLOW));
               }
            }
         }
      }

      return new ExportOptions(name, mergeAtlas, textureScale, weldVertices, aggressiveCull, bake, bakeUvSize, bakeMaxAtlas);
   }

   static String formatOptions(ExportOptions options) {
      List<String> tokens = new ArrayList<>();
      tokens.add(options.mergeAtlas() ? "atlas" : "separate");
      if (options.textureScale() > 1) {
         tokens.add(String.valueOf(options.textureScale()));
      }

      if (options.bake()) {
         tokens.add("bake");
         if (options.bakeUvSize() != DEFAULT_BAKE_UV_SIZE) {
            tokens.add("uv" + options.bakeUvSize());
         }

         if (options.bakeMaxAtlas() != DEFAULT_BAKE_MAX_ATLAS) {
            tokens.add("max" + options.bakeMaxAtlas());
         }
      }

      if (options.weldVertices()) {
         tokens.add("weld");
      }

      if (options.aggressiveCull()) {
         tokens.add("cull");
      }

      return String.join(" ", tokens);
   }

   /** Never clobbers a previous export: {@code shop}, then {@code shop_2}, {@code shop_3}… */
   private static String uniqueName(MinecraftClient client, String name) {
      Path root = RegionExporter.exportRoot(client);
      String candidate = name;

      for (int i = 2; Files.exists(root.resolve(candidate)); i++) {
         candidate = name + "_" + i;
      }

      return candidate;
   }

   private static Text describeOptions(ExportOptions options) {
      List<Text> parts = new ArrayList<>();
      parts.add(Text.translatable(options.mergeAtlas() ? "meshify.opt.atlas" : "meshify.opt.separate"));
      if (options.textureScale() > 1) {
         parts.add(Text.translatable("meshify.opt.scale", options.textureScale()));
      }

      if (options.bake()) {
         parts.add(Text.translatable("meshify.opt.bake", options.bakeUvSize(), options.bakeMaxAtlas()));
      }

      if (options.weldVertices()) {
         parts.add(Text.translatable("meshify.opt.weld"));
      }

      if (options.aggressiveCull()) {
         parts.add(Text.translatable("meshify.opt.cull"));
      }

      return Texts.join(parts, Text.literal(", "));
   }

   /** Self-check, run with {@code gradlew optionsCheck}. */
   public static void main(String[] args) {
      List<ExportOptions> cases = List.of(
         new ExportOptions("check", true, 1, false, false, false, DEFAULT_BAKE_UV_SIZE, DEFAULT_BAKE_MAX_ATLAS),
         new ExportOptions("check", false, 4, true, true, false, DEFAULT_BAKE_UV_SIZE, DEFAULT_BAKE_MAX_ATLAS),
         new ExportOptions("check", true, 2, false, true, true, DEFAULT_BAKE_UV_SIZE, DEFAULT_BAKE_MAX_ATLAS),
         new ExportOptions("check", false, 8, true, false, true, 64, 8192)
      );

      for (ExportOptions options : cases) {
         String formatted = formatOptions(options);
         Consumer<Text> reject = text -> {
            throw new AssertionError("options self-check: '" + formatted + "' has a token the parser rejects");
         };
         ExportOptions parsed = parseOptions("check", formatted, new Feedback(reject, reject));
         if (!parsed.equals(options)) {
            throw new AssertionError("options self-check failed: " + options + " -> '" + formatted + "' -> " + parsed);
         }
      }

      // Every size the menu offers must survive the parser's clamping.
      for (int size : BAKE_UV_SIZES) {
         check(parseOptions("check", "uv" + size, Feedback.IGNORE).bakeUvSize() == size, "uv" + size);
      }

      for (int size : BAKE_MAX_ATLAS_SIZES) {
         check(parseOptions("check", "max" + size, Feedback.IGNORE).bakeMaxAtlas() == size, "max" + size);
      }

      check(sanitizeName("my build/2").equals("my_build_2"), "names keep only file-safe characters");
      System.out.println("Options self-check passed");
   }

   private static void check(boolean passed, String description) {
      if (!passed) {
         throw new AssertionError("options self-check failed: " + description);
      }
   }
}
