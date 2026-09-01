package com.norwood.pos2obj.export.special;

import com.norwood.pos2obj.export.BoxUv;
import com.norwood.pos2obj.export.MeshBuilder;
import com.norwood.pos2obj.export.TexKey;
import net.minecraft.util.Identifier;

public final class ConduitExporter {
   private ConduitExporter() {
   }

   public static void export(MeshBuilder mesh, double bx, double by, double bz) {
      TexKey texkey = TexKey.resource(Identifier.of("minecraft", "textures/entity/conduit/base.png"));
      BoxUv.emit(mesh, texkey, bx, by, bz, 5.0, 5.0, 5.0, 11.0, 11.0, 11.0, 0, 0, 32, 16, 0.0, 8.0, 8.0, 0.0);
   }
}
