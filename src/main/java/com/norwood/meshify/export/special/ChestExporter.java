package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TexKey;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

public final class ChestExporter {
   private ChestExporter() {
   }

   public static void export(MeshBuilder mesh, BlockState state, double bx, double by, double bz) {
      Direction direction = state.get(Properties.HORIZONTAL_FACING);

      double d0 = switch (direction) {
         case NORTH -> 180.0;
         case EAST -> 270.0;
         case WEST -> 90.0;
         default -> 0.0;
      };
      boolean flag = state.getBlock() == Blocks.ENDER_CHEST;
      ChestType chesttype = !flag && state.contains(ChestBlock.CHEST_TYPE) ? state.get(ChestBlock.CHEST_TYPE) : ChestType.SINGLE;
      String s = flag ? "ender" : (state.getBlock() == Blocks.TRAPPED_CHEST ? "trapped" : "normal");
      String s1 = chesttype == ChestType.LEFT ? "_left" : (chesttype == ChestType.RIGHT ? "_right" : "");
      TexKey texkey = TexKey.resource(Identifier.of("minecraft", "textures/entity/chest/" + s + s1 + ".png"));
      switch (chesttype) {
         case SINGLE:
            box(mesh, texkey, bx, by, bz, 1.0, 0.0, 1.0, 15.0, 10.0, 15.0, 0, 19, d0);
            box(mesh, texkey, bx, by, bz, 1.0, 9.0, 1.0, 15.0, 14.0, 15.0, 0, 0, d0);
            box(mesh, texkey, bx, by, bz, 7.0, 7.0, 15.0, 9.0, 11.0, 16.0, 0, 0, d0);
            break;
         case RIGHT:
            box(mesh, texkey, bx, by, bz, 1.0, 0.0, 1.0, 16.0, 10.0, 15.0, 0, 19, d0);
            box(mesh, texkey, bx, by, bz, 1.0, 9.0, 1.0, 16.0, 14.0, 15.0, 0, 0, d0);
            box(mesh, texkey, bx, by, bz, 15.0, 7.0, 15.0, 16.0, 11.0, 16.0, 0, 0, d0);
            break;
         case LEFT:
            box(mesh, texkey, bx, by, bz, 0.0, 0.0, 1.0, 15.0, 10.0, 15.0, 0, 19, d0);
            box(mesh, texkey, bx, by, bz, 0.0, 9.0, 1.0, 15.0, 14.0, 15.0, 0, 0, d0);
            box(mesh, texkey, bx, by, bz, 0.0, 7.0, 15.0, 1.0, 11.0, 16.0, 0, 0, d0);
      }
   }

   private static void box(
      MeshBuilder mesh,
      TexKey tex,
      double bx,
      double by,
      double bz,
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
      BoxUv.emit(mesh, tex, bx, by, bz, x0, y0, z0, x1, y1, z1, u0, v0, 64, 64, yaw, 8.0, 8.0, 0.0, true);
   }
}
