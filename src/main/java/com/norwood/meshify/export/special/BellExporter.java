package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TexKey;
import net.minecraft.util.Identifier;

public final class BellExporter {
   private BellExporter() {
   }

   public static void export(MeshBuilder mesh, double bx, double by, double bz) {
      TexKey texkey = TexKey.resource(Identifier.of("minecraft", "textures/entity/bell/bell_body.png"));
      BoxUv.emit(mesh, texkey, bx, by, bz, 5.0, 6.0, 5.0, 11.0, 13.0, 11.0, 0, 0, 32, 32, 0.0, 8.0, 8.0, 0.0);
      BoxUv.emit(mesh, texkey, bx, by, bz, 4.0, 4.0, 4.0, 12.0, 6.0, 12.0, 0, 13, 32, 32, 0.0, 8.0, 8.0, 0.0);
   }
}
