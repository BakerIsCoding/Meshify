package com.norwood.meshify;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class Selection {
   public static BlockPos start;
   public static BlockPos end;
   public static ResourceKey<Level> dimension;
   public static String lastOptions = "";

   private static Path sessionFile;

   private Selection() {
   }

   public enum Corner {
      START("meshify.label.start"),
      END("meshify.label.end");

      public final String labelKey;

      Corner(String labelKey) {
         this.labelKey = labelKey;
      }
   }

   public static boolean isComplete() {
      return start != null && end != null;
   }

   public static void clear() {
      start = null;
      end = null;
      dimension = null;
      save();
   }

   public static void set(Corner corner, BlockPos pos, ResourceKey<Level> worldKey) {
      // Corners from different dimensions describe nothing, so the stale one is dropped.
      if (dimension != null && !dimension.equals(worldKey)) {
         start = null;
         end = null;
      }

      dimension = worldKey;
      if (corner == Corner.START) {
         start = pos;
      } else {
         end = pos;
      }

      save();
   }

   public static boolean matchesDimension(ResourceKey<Level> worldKey) {
      return dimension == null || dimension.equals(worldKey);
   }

   public static BlockPos min() {
      return new BlockPos(Math.min(start.getX(), end.getX()), Math.min(start.getY(), end.getY()), Math.min(start.getZ(), end.getZ()));
   }

   public static BlockPos max() {
      return new BlockPos(Math.max(start.getX(), end.getX()), Math.max(start.getY(), end.getY()), Math.max(start.getZ(), end.getZ()));
   }

   // The region's size in blocks along X, Y and Z.
   private static BlockPos size() {
      return max().subtract(min()).offset(1, 1, 1);
   }

   // The region's size along X, Y and Z, e.g. 32×18×40.
   public static String describeSize() {
      BlockPos size = size();
      return size.getX() + "×" + size.getY() + "×" + size.getZ();
   }

   // Stops at Long.MAX_VALUE, so coordinates typed in the billions cannot wrap around under the size limit.
   public static long volume() {
      if (!isComplete()) {
         return 0L;
      }

      BlockPos min = min();
      BlockPos max = max();
      try {
         long area = Math.multiplyExact((long)max.getX() - min.getX() + 1, (long)max.getY() - min.getY() + 1);
         return Math.multiplyExact(area, (long)max.getZ() - min.getZ() + 1);
      } catch (ArithmeticException overflow) {
         return Long.MAX_VALUE;
      }
   }

   // Grows all six sides by that many blocks; a negative count shrinks them.
   public static void expand(int blocks) {
      BlockPos lo = min();
      BlockPos hi = max();
      int[] x = growRange(lo.getX(), hi.getX(), blocks);
      int[] y = growRange(lo.getY(), hi.getY(), blocks);
      int[] z = growRange(lo.getZ(), hi.getZ(), blocks);
      setBounds(new BlockPos(x[0], y[0], z[0]), new BlockPos(x[1], y[1], z[1]));
   }

   // Moves only the side face; shrinking stops at the opposite face so it never turns inside out.
   public static void expand(int blocks, Direction side) {
      BlockPos lo = min();
      BlockPos hi = max();
      Direction.Axis axis = side.getAxis();
      int shift = Math.max(blocks, lo.get(axis) - hi.get(axis));
      if (side.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
         setBounds(lo, hi.relative(side, shift));
      } else {
         setBounds(lo.relative(side, shift), hi);
      }
   }

   // Shrinking past the middle would flip the axis, so it collapses to a single block instead.
   private static int[] growRange(int lo, int hi, int blocks) {
      int grownLo = lo - blocks;
      int grownHi = hi + blocks;
      if (grownLo > grownHi) {
         int middle = (lo + hi) / 2;
         return new int[]{middle, middle};
      }

      return new int[]{grownLo, grownHi};
   }

   public static void expandVertical(int bottomY, int topY) {
      setBounds(new BlockPos(min().getX(), bottomY, min().getZ()), new BlockPos(max().getX(), topY, max().getZ()));
   }

   public static void setBounds(BlockPos lo, BlockPos hi) {
      start = lo;
      end = hi;
      save();
   }

   public static void rememberOptions(String options) {
      lastOptions = options == null ? "" : options.trim();
      save();
   }

   // Keeps the selection across restarts in a Properties file: five fields need no JSON.
   public static void loadSession(Path exportRoot) {
      sessionFile = exportRoot.resolve("session.properties");
      if (!Files.exists(sessionFile)) {
         return;
      }

      Properties properties = new Properties();
      try (InputStream in = Files.newInputStream(sessionFile)) {
         properties.load(in);
      } catch (IOException unreadable) {
         // Start with no selection rather than a half-read one.
         return;
      }

      start = readPos(properties, "start");
      end = readPos(properties, "end");
      lastOptions = properties.getProperty("options", "");
      String dimensionName = properties.getProperty("dimension", "");
      Identifier dimensionId = dimensionName.isEmpty() ? null : Identifier.tryParse(dimensionName);
      if (dimensionId != null) {
         dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
      }
   }

   private static void save() {
      if (sessionFile == null) {
         return;
      }

      Properties properties = new Properties();
      writePos(properties, "start", start);
      writePos(properties, "end", end);
      properties.setProperty("options", lastOptions);
      if (dimension != null) {
         properties.setProperty("dimension", dimension.identifier().toString());
      }

      try {
         Files.createDirectories(sessionFile.getParent());

         try (OutputStream out = Files.newOutputStream(sessionFile)) {
            properties.store(out, "meshify selection");
         }
      } catch (IOException ignored) {
         // Only the next session's convenience is lost; the selection in memory is intact.
      }
   }

   private static BlockPos readPos(Properties properties, String key) {
      String value = properties.getProperty(key);
      String[] parts = value == null ? new String[0] : value.split(",");
      if (parts.length != 3) {
         return null;
      }

      try {
         return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
      } catch (NumberFormatException malformed) {
         return null;
      }
   }

   private static void writePos(Properties properties, String key, BlockPos pos) {
      if (pos != null) {
         properties.setProperty(key, pos.getX() + "," + pos.getY() + "," + pos.getZ());
      }
   }

   // Self-check, run with gradlew selectionCheck; never calls loadSession, so it writes no file.
   public static void main(String[] args) {
      start = new BlockPos(0, 0, 0);
      end = new BlockPos(9, 4, 9);
      expand(3, Direction.UP);
      check(min().getY() == 0 && max().getY() == 7, "growing up moves only the top face");
      expand(-2, Direction.WEST);
      check(min().getX() == 2 && max().getX() == 9, "shrinking west pulls the west face in");
      expand(-100, Direction.NORTH);
      check(min().getZ() == 9 && max().getZ() == 9, "shrinking stops at the opposite face");
      expand(-100, Direction.DOWN);
      check(min().getY() == 7 && max().getY() == 7, "shrinking from below stops at the top face");
      check(describeSize().equals("8×1×1"), "dimensions reads X×Y×Z");
      check(volume() == 8L, "volume multiplies the sides");
      start = new BlockPos(-2_000_000_000, 0, 0);
      end = new BlockPos(2_000_000_000, 0, 0);
      check(volume() == 4_000_000_001L, "a side wider than an int still counts right");
      start = new BlockPos(-2_000_000_000, -2_000_000_000, -2_000_000_000);
      end = new BlockPos(2_000_000_000, 2_000_000_000, 2_000_000_000);
      check(volume() == Long.MAX_VALUE, "a volume past a long saturates instead of wrapping");
      System.out.println("Selection self-check passed");
   }

   private static void check(boolean passed, String description) {
      if (!passed) {
         throw new AssertionError("Selection self-check failed: " + description);
      }
   }
}
