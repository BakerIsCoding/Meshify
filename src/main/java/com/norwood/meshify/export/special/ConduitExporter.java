package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BlockContext;
import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.TextureKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

public final class ConduitExporter {
   private ConduitExporter() {
   }

   public static void export(BlockContext context) {
      TextureKey texture = TextureKey.resource(Identifier.withDefaultNamespace("textures/entity/conduit/base.png"));
      new BoxUv(context.placement(0.0), texture, 32, 16).box(new AABB(5.0, 5.0, 5.0, 11.0, 11.0, 11.0), 0, 0);
   }
}
