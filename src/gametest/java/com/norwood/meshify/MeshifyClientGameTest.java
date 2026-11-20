package com.norwood.meshify;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.norwood.meshify.export.RegionExporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Starts a real client, builds a scene with something for every exporter, runs /meshify export
 * through the client commands with different options and checks the files each export writes.
 */
public class MeshifyClientGameTest implements FabricClientGameTest {
   // Two minutes; exporting this scene takes a few seconds.
   private static final int EXPORT_TIMEOUT_TICKS = 2400;
   private static final AABB REGION = new AABB(0, -61, 0, 17, -57, 7);
   // The 17 x 7 grass floor of the region plus the 16 blocks placed on it.
   private static final int BLOCK_COUNT = 135;
   private static final int ENTITY_COUNT = 3;
   // The flat test world has grass at y -61: water goes into the ground so it cannot spread, the rest stands on it.
   private static final List<String> SCENE = List.of(
      "setblock 1 -60 1 minecraft:stone",
      "setblock 2 -60 1 minecraft:oak_stairs",
      "setblock 3 -60 1 minecraft:glass",
      "setblock 4 -60 1 minecraft:oak_leaves[persistent=true]",
      "setblock 5 -60 1 minecraft:chest",
      "setblock 6 -60 1 minecraft:oak_sign{front_text:{messages:[\"Meshify\",\"test\",\"\",\"\"]}}",
      "setblock 7 -60 1 minecraft:white_banner{patterns:[{pattern:\"minecraft:stripe_top\",color:\"red\"}]}",
      "setblock 8 -60 1 minecraft:red_bed[part=foot,facing=south]",
      "setblock 8 -60 2 minecraft:red_bed[part=head,facing=south]",
      "setblock 9 -60 1 minecraft:skeleton_skull",
      "setblock 10 -60 1 minecraft:player_head",
      "setblock 11 -60 1 minecraft:purple_shulker_box",
      "setblock 12 -60 1 minecraft:bell",
      "setblock 13 -60 1 minecraft:decorated_pot",
      // A conduit is waterlogged by default, and that water would spread over the scene while it exports.
      "setblock 14 -60 1 minecraft:conduit[waterlogged=false]",
      "setblock 15 -60 1 minecraft:enchanting_table",
      "setblock 1 -61 3 minecraft:water",
      "setblock 2 -61 3 minecraft:water",
      "summon armor_stand 2 -60 5 {NoGravity:1b}",
      "summon pig 5 -60 5 {NoAI:1b}",
      "summon item_frame 8 -60 5 {Facing:1b,Fixed:1b,Item:{id:\"minecraft:diamond\",count:1}}"
   );
   // Parts of OBJ group (texture) names that only show up when that exporter produced geometry.
   private static final List<String> EXPECTED_GROUPS = List.of(
      "stone",
      "glass",
      "oak_leaves",
      "grass_block_top_t",
      "water",
      "chest",
      "sign_text_",
      "banner",
      "bed",
      "skeleton",
      "player",
      "shulker",
      "bell",
      "decorated_pot",
      "conduit",
      "enchanting_table_book",
      "armorstand",
      "pig",
      "item_frame",
      "diamond"
   );
   private static final String SEPARATE_WARNING = "no .glb written";
   private static final Pattern COUNTS = Pattern.compile("blocks exported: (\\d+), entities: (\\d+)");

   @Override
   public void runTest(ClientGameTestContext context) {
      try (TestSingleplayerContext world = context.worldBuilder().create()) {
         SCENE.forEach(world.getServer()::runCommand);
         context.waitFor(client -> client.level.getEntities((Entity) null, REGION, entity -> !(entity instanceof Player)).size() == ENTITY_COUNT);
         // Block entity data (sign text, banner patterns) arrives in packets of its own.
         context.waitTicks(20);

         meshify(context, "meshify start 0 -61 0");
         meshify(context, "meshify end 16 -58 6");
         List<String> problems = new ArrayList<>();
         problems.addAll(export(context, "atlas", "atlas"));
         problems.addAll(export(context, "separate", "separate 2"));
         problems.addAll(export(context, "baked", "bake weld cull"));
         if (!problems.isEmpty()) {
            throw new AssertionError("Meshify export problems:\n  " + String.join("\n  ", problems));
         }
      }
   }

   private static void meshify(ClientGameTestContext context, String command) {
      int result;
      // Fabric mixes FabricClientCommandSource into the suggestions provider at runtime, so javac needs the cast through Object.
      try {
         result = context.computeOnClient(
            client -> ClientCommandManager.getActiveDispatcher().execute(command, (FabricClientCommandSource) (Object) client.getConnection().getSuggestionsProvider())
         );
      } catch (CommandSyntaxException exception) {
         throw new AssertionError("/" + command + ": " + exception.getMessage(), exception);
      }

      if (result == 0) {
         throw new AssertionError("/" + command + " was refused, see [CHAT] in the log");
      }
   }

   private static List<String> export(ClientGameTestContext context, String name, String options) {
      meshify(context, "meshify export " + name + " " + options);
      context.waitFor(client -> !ExportJob.isRunning(), EXPORT_TIMEOUT_TICKS);
      Path dir = context.computeOnClient(client -> RegionExporter.outputDir(client, name));
      try {
         return checkOutput(dir, name, options).stream().map(problem -> name + ": " + problem).toList();
      } catch (IOException exception) {
         throw new UncheckedIOException(exception);
      }
   }

   private static List<String> checkOutput(Path dir, String name, String options) throws IOException {
      List<String> problems = new ArrayList<>();
      Path info = dir.resolve("export_info.txt");
      if (!Files.exists(info)) {
         problems.add("no export_info.txt, the export failed: see [CHAT] in the log");
         return problems;
      }

      String infoText = Files.readString(info);
      if (infoText.contains("skipped block types")) {
         problems.add("blocks skipped, see " + info);
      }

      boolean separate = options.contains("separate");
      int warningsAt = infoText.indexOf("warnings:");
      if (warningsAt >= 0) {
         infoText.substring(warningsAt).lines().skip(1).map(String::strip).filter(line -> !(separate && line.contains(SEPARATE_WARNING))).forEach(line -> problems.add("warning " + line));
      }

      Matcher counts = COUNTS.matcher(infoText);
      String exported = counts.find() ? counts.group(1) + " blocks, " + counts.group(2) + " entities" : "nothing";
      String expected = BLOCK_COUNT + " blocks, " + ENTITY_COUNT + " entities";
      if (!exported.equals(expected)) {
         problems.add("expected " + expected + ", exported " + exported);
      }

      problems.addAll(checkObj(dir.resolve(name + ".obj"), !options.contains("bake")));
      problems.addAll(checkMtl(dir, dir.resolve(name + ".mtl")));
      Path glb = dir.resolve(name + ".glb");
      if (!separate && !(Files.exists(glb) && new String(Files.readAllBytes(glb), 0, 4).equals("glTF"))) {
         problems.add("missing or broken " + glb.getFileName());
      }

      return problems;
   }

   // Every face must point at vertices, UVs and normals that exist.
   private static List<String> checkObj(Path obj, boolean checkGroups) throws IOException {
      List<String> problems = new ArrayList<>();
      int[] counts = new int[3];
      int faces = 0;
      List<String> groups = new ArrayList<>();
      for (String line : Files.readAllLines(obj)) {
         String[] parts = line.split(" ");
         switch (parts[0]) {
            case "v" -> counts[0]++;
            case "vt" -> counts[1]++;
            case "vn" -> counts[2]++;
            case "g" -> groups.add(parts[1]);
            case "f" -> {
               faces++;
               for (int corner = 1; corner < parts.length; corner++) {
                  String[] indices = parts[corner].split("/");
                  for (int k = 0; k < 3; k++) {
                     int index = Integer.parseInt(indices[k]);
                     if (index < 1 || index > counts[k]) {
                        problems.add("face '" + line + "' points past the " + counts[k] + " entries of its kind");
                        return problems;
                     }
                  }
               }
            }
            default -> {
            }
         }
      }

      if (faces == 0) {
         problems.add("the OBJ has no faces");
      }

      if (checkGroups) {
         for (String expected : EXPECTED_GROUPS) {
            if (groups.stream().noneMatch(group -> group.contains(expected))) {
               problems.add("no OBJ group containing '" + expected + "'");
            }
         }
      }

      return problems;
   }

   private static List<String> checkMtl(Path dir, Path mtl) throws IOException {
      List<String> problems = new ArrayList<>();
      for (String line : Files.readAllLines(mtl)) {
         if (line.startsWith("map_")) {
            Path texture = dir.resolve(line.substring(line.indexOf(' ') + 1));
            if (!Files.exists(texture) || Files.size(texture) == 0) {
               problems.add("missing texture " + dir.relativize(texture));
            }
         }
      }

      return problems;
   }
}
