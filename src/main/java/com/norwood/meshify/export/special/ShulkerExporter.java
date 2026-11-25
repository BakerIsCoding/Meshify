package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BlockContext;
import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.TextureKey;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;

public final class ShulkerExporter {
   private ShulkerExporter() {
   }

   public static void export(BlockContext context, ShulkerBoxBlock block) {
      Direction facing = context.state().getValue(ShulkerBoxBlock.FACING);
      DyeColor color = block.getColor();
      String textureName = color == null ? "shulker" : "shulker_" + color.getName();
      TextureKey texture = TextureKey.resource(Identifier.withDefaultNamespace("textures/entity/shulker/" + textureName + ".png"));
      BoxUv shulker = new BoxUv(context.placement(0.0), texture, 64, 64).withUp(facing);
      // Base, lid.
      shulker.box(new AABB(0.0, 0.0, 0.0, 16.0, 8.0, 16.0), 0, 28);
      shulker.box(new AABB(0.0, 4.0, 0.0, 16.0, 16.0, 16.0), 0, 0);
   }
}
