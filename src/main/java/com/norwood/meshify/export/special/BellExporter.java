package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BlockContext;
import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.TextureKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

public final class BellExporter {
   private BellExporter() {
   }

   public static void export(BlockContext context) {
      TextureKey texture = TextureKey.resource(Identifier.withDefaultNamespace("textures/entity/bell/bell_body.png"));
      BoxUv bell = new BoxUv(context.placement(0.0), texture, 32, 32);
      bell.box(new AABB(5.0, 6.0, 5.0, 11.0, 13.0, 11.0), 0, 0);
      bell.box(new AABB(4.0, 4.0, 4.0, 12.0, 6.0, 12.0), 0, 13);
   }
}
