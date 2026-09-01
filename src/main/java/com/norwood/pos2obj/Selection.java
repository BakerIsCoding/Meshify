package com.norwood.pos2obj;

import net.minecraft.util.math.BlockPos;

public final class Selection {
   public static BlockPos start;
   public static BlockPos end;

   private Selection() {
   }

   public static boolean isComplete() {
      return start != null && end != null;
   }

   public static void clear() {
      start = null;
      end = null;
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
}
