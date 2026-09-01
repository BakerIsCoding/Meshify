package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TexKey;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

public final class ShulkerExporter {
   private ShulkerExporter() {
   }

   public static void export(MeshBuilder mesh, BlockState state, ShulkerBoxBlock block, double bx, double by, double bz) {
      Direction direction = state.get(ShulkerBoxBlock.FACING);
      DyeColor dyecolor = block.getColor();
      String s = dyecolor == null ? "shulker" : "shulker_" + dyecolor.getId();
      TexKey texkey = TexKey.resource(Identifier.of("minecraft", "textures/entity/shulker/" + s + ".png"));
      BoxUv.emit(mesh, texkey, bx, by, bz, 0.0, 0.0, 0.0, 16.0, 8.0, 16.0, 0, 28, 16, 8, 16, 64, 64, 0.0, 8.0, 8.0, 0.0, direction);
      BoxUv.emit(mesh, texkey, bx, by, bz, 0.0, 4.0, 0.0, 16.0, 16.0, 16.0, 0, 0, 16, 12, 16, 64, 64, 0.0, 8.0, 8.0, 0.0, direction);
   }
}
