package com.norwood.meshify.export.special;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

// Model yaw, in degrees, for the block states the special exporters draw.
final class Yaw {
   // Standing signs, banners and heads turn in sixteen steps.
   static final double ROTATION_STEP = 22.5;

   private Yaw() {
   }

   // North 0, east 90, south 180, west 270; models drawn facing the other way pass facing.getOpposite().
   static double of(Direction facing) {
      return (facing.toYRot() + 180.0) % 360.0;
   }

   // A standing sign, banner or head, turned half a round like its renderer does.
   static double ofRotation(BlockState state) {
      return 180.0 + state.getValue(BlockStateProperties.ROTATION_16) * ROTATION_STEP;
   }
}
