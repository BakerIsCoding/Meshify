package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TextureKey;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

public final class ShulkerExporter {
   private ShulkerExporter() {
   }

   public static void export(MeshBuilder mesh, BlockState state, ShulkerBoxBlock block, double blockX, double blockY, double blockZ) {
      Direction facing = state.get(ShulkerBoxBlock.FACING);
      DyeColor color = block.getColor();
      String textureName = color == null ? "shulker" : "shulker_" + color.getId();
      TextureKey texture = TextureKey.resource(Identifier.ofVanilla("textures/entity/shulker/" + textureName + ".png"));
      BoxUv.emit(mesh, texture, blockX, blockY, blockZ, 0.0, 0.0, 0.0, 16.0, 8.0, 16.0, 0, 28, 16, 8, 16, 64, 64, 0.0, 0.0, facing, false);
      BoxUv.emit(mesh, texture, blockX, blockY, blockZ, 0.0, 4.0, 0.0, 16.0, 16.0, 16.0, 0, 0, 16, 12, 16, 64, 64, 0.0, 0.0, facing, false);
   }
}
