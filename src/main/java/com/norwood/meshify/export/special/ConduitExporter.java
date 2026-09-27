package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TextureKey;
import net.minecraft.util.Identifier;

public final class ConduitExporter {
   private ConduitExporter() {
   }

   public static void export(MeshBuilder mesh, double blockX, double blockY, double blockZ) {
      TextureKey texture = TextureKey.resource(Identifier.ofVanilla("textures/entity/conduit/base.png"));
      BoxUv.emit(mesh, texture, blockX, blockY, blockZ, 5.0, 5.0, 5.0, 11.0, 11.0, 11.0, 0, 0, 32, 16, 0.0, 0.0);
   }
}
