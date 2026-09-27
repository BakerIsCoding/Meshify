package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TextureKey;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;

public final class ChestExporter {
   private ChestExporter() {
   }

   public static void export(MeshBuilder mesh, BlockState state, double blockX, double blockY, double blockZ) {
      double yaw = Yaw.of(state.get(Properties.HORIZONTAL_FACING).getOpposite());
      boolean ender = state.getBlock() == Blocks.ENDER_CHEST;
      ChestType type = !ender && state.contains(ChestBlock.CHEST_TYPE) ? state.get(ChestBlock.CHEST_TYPE) : ChestType.SINGLE;
      String kind = ender ? "ender" : (state.getBlock() == Blocks.TRAPPED_CHEST ? "trapped" : "normal");
      String half = switch (type) {
         case LEFT -> "_left";
         case RIGHT -> "_right";
         case SINGLE -> "";
      };
      TextureKey texture = TextureKey.resource(Identifier.ofVanilla("textures/entity/chest/" + kind + half + ".png"));
      switch (type) {
         case SINGLE -> {
            uprightBox(mesh, texture, blockX, blockY, blockZ, 1.0, 0.0, 1.0, 15.0, 10.0, 15.0, 0, 19, yaw);
            uprightBox(mesh, texture, blockX, blockY, blockZ, 1.0, 9.0, 1.0, 15.0, 14.0, 15.0, 0, 0, yaw);
            uprightBox(mesh, texture, blockX, blockY, blockZ, 7.0, 7.0, 15.0, 9.0, 11.0, 16.0, 0, 0, yaw);
         }
         case RIGHT -> {
            uprightBox(mesh, texture, blockX, blockY, blockZ, 1.0, 0.0, 1.0, 16.0, 10.0, 15.0, 0, 19, yaw);
            uprightBox(mesh, texture, blockX, blockY, blockZ, 1.0, 9.0, 1.0, 16.0, 14.0, 15.0, 0, 0, yaw);
            uprightBox(mesh, texture, blockX, blockY, blockZ, 15.0, 7.0, 15.0, 16.0, 11.0, 16.0, 0, 0, yaw);
         }
         case LEFT -> {
            uprightBox(mesh, texture, blockX, blockY, blockZ, 0.0, 0.0, 1.0, 15.0, 10.0, 15.0, 0, 19, yaw);
            uprightBox(mesh, texture, blockX, blockY, blockZ, 0.0, 9.0, 1.0, 15.0, 14.0, 15.0, 0, 0, yaw);
            uprightBox(mesh, texture, blockX, blockY, blockZ, 0.0, 7.0, 15.0, 1.0, 11.0, 16.0, 0, 0, yaw);
         }
      }
   }

   private static void uprightBox(
      MeshBuilder mesh,
      TextureKey texture,
      double blockX,
      double blockY,
      double blockZ,
      double x0,
      double y0,
      double z0,
      double x1,
      double y1,
      double z1,
      int u0,
      int v0,
      double yaw
   ) {
      // The chest renderer draws its model upright, unlike the skull/shulker ones BoxUv defaults to.
      BoxUv.emit(mesh, texture, blockX, blockY, blockZ, x0, y0, z0, x1, y1, z1, u0, v0, 64, 64, yaw, 0.0, true);
   }
}
