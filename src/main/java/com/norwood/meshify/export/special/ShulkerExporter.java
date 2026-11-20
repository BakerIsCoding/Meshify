package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TextureKey;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class ShulkerExporter {
   private ShulkerExporter() {
   }

   public static void export(MeshBuilder mesh, BlockState state, ShulkerBoxBlock block, double blockX, double blockY, double blockZ) {
      Direction facing = state.getValue(ShulkerBoxBlock.FACING);
      DyeColor color = block.getColor();
      String textureName = color == null ? "shulker" : "shulker_" + color.getName();
      TextureKey texture = TextureKey.resource(Identifier.withDefaultNamespace("textures/entity/shulker/" + textureName + ".png"));
      BoxUv.emit(mesh, texture, blockX, blockY, blockZ, 0.0, 0.0, 0.0, 16.0, 8.0, 16.0, 0, 28, 16, 8, 16, 64, 64, 0.0, 0.0, facing, false);
      BoxUv.emit(mesh, texture, blockX, blockY, blockZ, 0.0, 4.0, 0.0, 16.0, 16.0, 16.0, 0, 0, 16, 12, 16, 64, 64, 0.0, 0.0, facing, false);
   }
}
