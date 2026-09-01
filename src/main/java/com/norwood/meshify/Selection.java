package com.norwood.meshify;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class Selection {
   public static BlockPos start;
   public static BlockPos end;
   /** The world both corners were placed in; a region spanning two dimensions is meaningless. */
   public static RegistryKey<World> dimension;
   /** Last option string used for an export, reused when the next one omits it. */
   public static String lastOptions = "";

   private static Path file;

   private Selection() {
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

   public static void set(boolean isStart, BlockPos pos, RegistryKey<World> world) {
      // Corners from two different dimensions describe nothing, so the second one wins and the
      // stale corner is dropped rather than silently exporting a garbage region.
      if (dimension != null && !dimension.equals(world)) {
         start = null;
         end = null;
      }

      dimension = world;
      if (isStart) {
         start = pos;
      } else {
         end = pos;
      }

      save();
   }

   public static boolean matches(RegistryKey<World> world) {
      return dimension == null || dimension.equals(world);
   }

   public static BlockPos min() {
      return new BlockPos(Math.min(start.getX(), end.getX()), Math.min(start.getY(), end.getY()), Math.min(start.getZ(), end.getZ()));
   }

   public static BlockPos max() {
      return new BlockPos(Math.max(start.getX(), end.getX()), Math.max(start.getY(), end.getY()), Math.max(start.getZ(), end.getZ()));
   }

   public static long volume() {
      if (!isComplete()) {
         return 0L;
      } else {
         long i = Math.abs(end.getX() - start.getX()) + 1L;
         long j = Math.abs(end.getY() - start.getY()) + 1L;
         long k = Math.abs(end.getZ() - start.getZ()) + 1L;
         return i * j * k;
      }
   }

   /** Grows the region by {@code n} blocks on all six sides; a negative {@code n} shrinks it. */
   public static void expand(int n) {
      BlockPos blockpos = min();
      BlockPos blockpos1 = max();
      int[] aint = axis(blockpos.getX(), blockpos1.getX(), n);
      int[] aint1 = axis(blockpos.getY(), blockpos1.getY(), n);
      int[] aint2 = axis(blockpos.getZ(), blockpos1.getZ(), n);
      start = new BlockPos(aint[0], aint1[0], aint2[0]);
      end = new BlockPos(aint[1], aint1[1], aint2[1]);
      save();
   }

   /** Shrinking past the middle would flip the axis, so it collapses to a single block instead. */
   private static int[] axis(int lo, int hi, int n) {
      int i = lo - n;
      int j = hi + n;
      if (i > j) {
         int k = (lo + hi) / 2;
         return new int[]{k, k};
      } else {
         return new int[]{i, j};
      }
   }

   public static void expandVertical(int bottomY, int topY) {
      start = new BlockPos(min().getX(), bottomY, min().getZ());
      end = new BlockPos(max().getX(), topY, max().getZ());
      save();
   }

   public static void rememberOptions(String options) {
      lastOptions = options == null ? "" : options.trim();
      save();
   }

   // ---------------------------------------------------------------- persistence

   /**
    * Keeps the selection across restarts. A {@link Properties} file rather than JSON: it is five
    * fields and the stdlib already reads and writes this format.
    */
   public static void init(Path runDir) {
      file = runDir.resolve("meshify").resolve("session.properties");
      if (Files.exists(file)) {
         Properties properties = new Properties();

         try (InputStream inputstream = Files.newInputStream(file)) {
            properties.load(inputstream);
         } catch (IOException ioexception) {
            return;
         }

         start = readPos(properties, "start");
         end = readPos(properties, "end");
         lastOptions = properties.getProperty("options", "");
         String s = properties.getProperty("dimension", "");
         if (!s.isEmpty()) {
            Identifier identifier = Identifier.tryParse(s);
            if (identifier != null) {
               dimension = RegistryKey.of(RegistryKeys.WORLD, identifier);
            }
         }
      }
   }

   private static void save() {
      if (file != null) {
         Properties properties = new Properties();
         writePos(properties, "start", start);
         writePos(properties, "end", end);
         properties.setProperty("options", lastOptions);
         if (dimension != null) {
            properties.setProperty("dimension", dimension.getValue().toString());
         }

         try {
            Files.createDirectories(file.getParent());

            try (OutputStream outputstream = Files.newOutputStream(file)) {
               properties.store(outputstream, "meshify selection");
            }
         } catch (IOException ioexception) {
         }
      }
   }

   private static BlockPos readPos(Properties props, String prefix) {
      String s = props.getProperty(prefix);
      if (s == null) {
         return null;
      } else {
         String[] astring = s.split(",");
         if (astring.length != 3) {
            return null;
         } else {
            try {
               return new BlockPos(Integer.parseInt(astring[0]), Integer.parseInt(astring[1]), Integer.parseInt(astring[2]));
            } catch (NumberFormatException numberformatexception) {
               return null;
            }
         }
      }
   }

   private static void writePos(Properties props, String prefix, BlockPos pos) {
      if (pos != null) {
         props.setProperty(prefix, pos.getX() + "," + pos.getY() + "," + pos.getZ());
      }
   }
}
