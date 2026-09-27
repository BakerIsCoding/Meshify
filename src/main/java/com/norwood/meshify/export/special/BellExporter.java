package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TextureKey;
import net.minecraft.util.Identifier;

public final class BellExporter {
   private BellExporter() {
   }

   public static void export(MeshBuilder mesh, double blockX, double blockY, double blockZ) {
      TextureKey texture = TextureKey.resource(Identifier.ofVanilla("textures/entity/bell/bell_body.png"));
      BoxUv.emit(mesh, texture, blockX, blockY, blockZ, 5.0, 6.0, 5.0, 11.0, 13.0, 11.0, 0, 0, 32, 32, 0.0, 0.0);
      BoxUv.emit(mesh, texture, blockX, blockY, blockZ, 4.0, 4.0, 4.0, 12.0, 6.0, 12.0, 0, 13, 32, 32, 0.0, 0.0);
   }
}
