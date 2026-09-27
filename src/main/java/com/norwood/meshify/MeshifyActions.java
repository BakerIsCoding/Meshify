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
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
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

   private static final long WARN_VOLUME = 2000000L;
   private static final int DEFAULT_BAKE_UV = 16;
   private static final int DEFAULT_BAKE_MAX = 4096;

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

   static Text cornerSetMessage(Corner corner, BlockPos pos) {
      Text label = Text.translatable(corner.labelKey);
      return Selection.isComplete()
         ? Text.translatable("meshify.msg.pos_set_volume", label, pos.toShortString(), Selection.dimensions(), Selection.volume())
         : Text.translatable("meshify.msg.pos_set", label, pos.toShortString());
   }

   static void setCorner(Feedback out, Corner corner, BlockPos pos) {
      Selection.set(corner, pos, MinecraftClient.getInstance().world.getRegistryKey());
      out.info(cornerSetMessage(corner, pos));
   }

   static void selectSingleBlock(Feedback out) {
      MinecraftClient client = MinecraftClient.getInstance();
      BlockPos pos = targetedBlock(client);
      Selection.set(Corner.START, pos, client.world.getRegistryKey());
      Selection.set(Corner.END, pos, client.world.getRegistryKey());
      out.info(Text.translatable("meshify.msg.here", pos.toShortString()));
   }

   static void clear(Feedback out) {
      Selection.clear();
      out.info(Text.translatable("meshify.msg.cleared"));
   }

   /** Grows (or, for negative {@code blocks}, shrinks) every side, or only {@code sideName}. */
   static boolean resize(Feedback out, int blocks, String sideName) {
      if (!Selection.isComplete()) {
         out.error(Text.translatable("meshify.error.no_selection"));
         return false;
      }

      if (sideName == null || sideName.equalsIgnoreCase(ALL_SIDES)) {
         Selection.expand(blocks);
      } else {
         Direction side = parseSide(sideName);
         if (side == null) {
            out.error(Text.translatable("meshify.error.unknown_direction", sideName));
            return false;
         }

         Selection.expand(blocks, side);
      }

      out.info(resizedMessage());
      return true;
   }

   private static Direction parseSide(String name) {
      String side = name.toLowerCase(Locale.ROOT);
      return side.equals(FACING) ? Direction.getEntityFacingOrder(MinecraftClient.getInstance().player)[0] : Direction.byId(side);
   }

   static boolean expandVertical(Feedback out) {
      if (!Selection.isComplete()) {
         out.error(Text.translatable("meshify.error.no_selection"));
         return false;
      }

      ClientWorld world = MinecraftClient.getInstance().world;
      Selection.expandVertical(world.getBottomY(), world.getBottomY() + world.getHeight() - 1);
      out.info(resizedMessage());
      return true;
   }

   /** Shrinks the region to the blocks actually in it, dropping the air around them. */
   static boolean trim(Feedback out) {
      if (!Selection.isComplete()) {
         out.error(Text.translatable("meshify.error.no_selection"));
         return false;
      }

      ClientWorld world = MinecraftClient.getInstance().world;
      if (!Selection.matches(world.getRegistryKey())) {
         out.error(wrongDimensionMessage());
         return false;
      }

      int unloadedChunks = RegionExporter.countUnloadedChunks(world, Selection.min(), Selection.max());
      if (unloadedChunks > 0) {
         out.error(Text.translatable("meshify.error.trim_unloaded", unloadedChunks));
         return false;
      }

      Optional<BlockBox> bounds = RegionExporter.solidBounds(world, Selection.min(), Selection.max());
      if (bounds.isEmpty()) {
         out.error(Text.translatable("meshify.error.trim_empty"));
         return false;
      }

      BlockBox box = bounds.get();
      Selection.setBounds(new BlockPos(box.getMinX(), box.getMinY(), box.getMinZ()), new BlockPos(box.getMaxX(), box.getMaxY(), box.getMaxZ()));
      out.info(resizedMessage());
      return true;
   }

   private static Text resizedMessage() {
      return Text.translatable(
         "meshify.msg.resized", Selection.min().toShortString(), Selection.max().toShortString(), Selection.dimensions(), Selection.volume()
      );
   }

   private static Text wrongDimensionMessage() {
      return Text.translatable("meshify.error.wrong_dimension", Selection.dimension.getValue().toString());
   }

   static Text statusLine() {
      Object start = Selection.start == null ? Text.translatable("meshify.label.unset") : Selection.start.toShortString();
      Object end = Selection.end == null ? Text.translatable("meshify.label.unset") : Selection.end.toShortString();
      return Selection.isComplete()
         ? Text.translatable("meshify.msg.status_volume", start, end, Selection.dimensions(), Selection.volume())
         : Text.translatable("meshify.msg.status", start, end);
   }

   static boolean cancel(Feedback out) {
      if (!ExportJob.cancel()) {
         out.error(Text.translatable("meshify.error.not_running"));
         return false;
      }

      out.info(Text.translatable("meshify.msg.cancelling"));
      return true;
   }

   static void openFolder(Feedback out) {
      Path root = RegionExporter.exportRoot(MinecraftClient.getInstance());
      root.toFile().mkdirs();
      Util.getOperatingSystem().open(root);
      out.info(Text.translatable("meshify.msg.open_folder", ExportJob.folderLink(root)));
   }

   /** Starts an export of the selection; a blank {@code optionsStr} reuses the previous run's options. */
   static boolean export(Feedback out, String name, String optionsStr) {
      if (ExportJob.isRunning()) {
         out.error(Text.translatable("meshify.error.busy"));
         return false;
      }

      if (!Selection.isComplete()) {
         out.error(Text.translatable("meshify.error.no_selection"));
         return false;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      ClientWorld world = client.world;
      if (!Selection.matches(world.getRegistryKey())) {
         out.error(wrongDimensionMessage());
         return false;
      }

      long volume = Selection.volume();
      if (volume > RegionExporter.MAX_VOLUME) {
         out.error(Text.translatable("meshify.error.too_large", volume, RegionExporter.MAX_VOLUME));
         return false;
      }

      int unloadedChunks = RegionExporter.countUnloadedChunks(world, Selection.min(), Selection.max());
      if (unloadedChunks > 0) {
         out.info(Text.translatable("meshify.msg.pre_unloaded", unloadedChunks).formatted(Formatting.YELLOW));
      }

      if (volume > WARN_VOLUME) {
         out.info(Text.translatable("meshify.msg.pre_large", volume).formatted(Formatting.YELLOW));
      }

      boolean reusingOptions = false;
      if (optionsStr == null || optionsStr.isBlank()) {
         optionsStr = Selection.lastOptions;
         reusingOptions = !optionsStr.isBlank();
      }

      if (name == null || name.isBlank()) {
         name = "export_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
      }

      ExportOptions options = parseOptions(uniqueName(client, name.replaceAll("[^A-Za-z0-9_\\-]", "_")), optionsStr, out);
      Selection.rememberOptions(optionsStr);
      if (reusingOptions) {
         out.info(Text.translatable("meshify.msg.reused_options", optionsStr).formatted(Formatting.GRAY));
      }

      out.info(Text.translatable("meshify.msg.export_start", volume, describeOptions(options)));
      ExportJob.start(Selection.start, Selection.end, options);
      return true;
   }

   /** Unknown tokens only get a warning, so a typo does not throw away the rest of the command. */
   static ExportOptions parseOptions(String name, String optionsStr, Feedback out) {
      boolean atlas = true;
      boolean weld = false;
      boolean cull = false;
      boolean bake = false;
      int texScale = 1;
      int bakeUv = DEFAULT_BAKE_UV;
      int bakeMax = DEFAULT_BAKE_MAX;

      for (String token : optionsStr.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
         if (token.isEmpty()) {
            continue;
         }

         switch (token) {
            case "atlas" -> atlas = true;
            case "separate" -> atlas = false;
            case "weld" -> weld = true;
            case "cull" -> cull = true;
            case "bake" -> bake = true;
            default -> {
               try {
                  if (token.startsWith("uv")) {
                     bakeUv = Math.max(1, Math.min(256, Integer.parseInt(token.substring(2))));
                     bake = true;
                  } else if (token.startsWith("max")) {
                     bakeMax = Math.max(256, Math.min(16384, Integer.parseInt(token.substring(3))));
                     bake = true;
                  } else {
                     texScale = Math.max(1, Math.min(16, Integer.parseInt(token)));
                  }
               } catch (NumberFormatException notANumber) {
                  out.info(Text.translatable("meshify.error.unknown_option", token).formatted(Formatting.YELLOW));
               }
            }
         }
      }

      return new ExportOptions(name, atlas, texScale, weld, cull, bake, bakeUv, bakeMax);
   }

   static String formatOptions(ExportOptions options) {
      List<String> tokens = new ArrayList<>();
      tokens.add(options.mergeAtlas() ? "atlas" : "separate");
      if (options.texScale() > 1) {
         tokens.add(String.valueOf(options.texScale()));
      }

      if (options.bake()) {
         tokens.add("bake");
         if (options.bakeUvSize() != DEFAULT_BAKE_UV) {
            tokens.add("uv" + options.bakeUvSize());
         }

         if (options.bakeMaxAtlas() != DEFAULT_BAKE_MAX) {
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
      if (options.texScale() > 1) {
         parts.add(Text.translatable("meshify.opt.scale", options.texScale()));
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

      MutableText joined = Text.empty();

      for (int i = 0; i < parts.size(); i++) {
         if (i > 0) {
            joined.append(", ");
         }

         joined.append(parts.get(i));
      }

      return joined;
   }

   /** Self-check, run with {@code gradlew optionsCheck}. */
   public static void main(String[] args) {
      List<ExportOptions> cases = List.of(
         new ExportOptions("check", true, 1, false, false, false, DEFAULT_BAKE_UV, DEFAULT_BAKE_MAX),
         new ExportOptions("check", false, 4, true, true, false, DEFAULT_BAKE_UV, DEFAULT_BAKE_MAX),
         new ExportOptions("check", true, 2, false, true, true, DEFAULT_BAKE_UV, DEFAULT_BAKE_MAX),
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

      System.out.println("Options self-check passed");
   }
}
