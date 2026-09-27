package com.norwood.meshify.export.special;

import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/** Model yaw, in degrees, for the block states the special exporters draw. */
final class Yaw {
   /** Standing signs, banners and heads turn in sixteen steps. */
   static final double ROTATION_STEP = 22.5;

   private Yaw() {
   }

   /** North 0, east 90, south 180, west 270; models drawn facing the other way pass {@code facing.getOpposite()}. */
   static double of(Direction facing) {
      return (facing.getPositiveHorizontalDegrees() + 180.0) % 360.0;
   }

   /** A standing sign, banner or head, turned half a round like its renderer does. */
   static double ofRotation(BlockState state) {
      return 180.0 + state.get(Properties.ROTATION) * ROTATION_STEP;
   }
}
