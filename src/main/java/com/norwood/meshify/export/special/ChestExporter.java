package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BlockContext;
import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.TextureKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

public final class ChestExporter {
   private ChestExporter() {
   }

   public static void export(BlockContext context) {
      BlockState state = context.state();
      double yaw = Yaw.of(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
      boolean ender = state.getBlock() == Blocks.ENDER_CHEST;
      ChestType type = !ender && state.hasProperty(ChestBlock.TYPE) ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
      String kind = ender ? "ender" : (state.getBlock() == Blocks.TRAPPED_CHEST ? "trapped" : "normal");
      String half = switch (type) {
         case LEFT -> "_left";
         case RIGHT -> "_right";
         case SINGLE -> "";
      };
      TextureKey texture = TextureKey.resource(Identifier.withDefaultNamespace("textures/entity/chest/" + kind + half + ".png"));
      BoxUv chest = new BoxUv(context.placement(yaw), texture, 64, 64).upright();
      // Bottom, lid and latch.
      switch (type) {
         case SINGLE -> {
            chest.box(new AABB(1.0, 0.0, 1.0, 15.0, 10.0, 15.0), 0, 19);
            chest.box(new AABB(1.0, 9.0, 1.0, 15.0, 14.0, 15.0), 0, 0);
            chest.box(new AABB(7.0, 7.0, 15.0, 9.0, 11.0, 16.0), 0, 0);
         }
         case RIGHT -> {
            chest.box(new AABB(1.0, 0.0, 1.0, 16.0, 10.0, 15.0), 0, 19);
            chest.box(new AABB(1.0, 9.0, 1.0, 16.0, 14.0, 15.0), 0, 0);
            chest.box(new AABB(15.0, 7.0, 15.0, 16.0, 11.0, 16.0), 0, 0);
         }
         case LEFT -> {
            chest.box(new AABB(0.0, 0.0, 1.0, 15.0, 10.0, 15.0), 0, 19);
            chest.box(new AABB(0.0, 9.0, 1.0, 15.0, 14.0, 15.0), 0, 0);
            chest.box(new AABB(0.0, 7.0, 15.0, 1.0, 11.0, 16.0), 0, 0);
         }
      }
   }
}
