package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TexKey;
import net.minecraft.util.Identifier;

public final class ConduitExporter {
   private ConduitExporter() {
   }

   public static void export(MeshBuilder mesh, double bx, double by, double bz) {
      TexKey texkey = TexKey.resource(Identifier.of("minecraft", "textures/entity/conduit/base.png"));
      BoxUv.emit(mesh, texkey, bx, by, bz, 5.0, 5.0, 5.0, 11.0, 11.0, 11.0, 0, 0, 32, 16, 0.0, 8.0, 8.0, 0.0);
   }
}
