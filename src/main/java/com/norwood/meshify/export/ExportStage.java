package com.norwood.meshify.export;

import java.util.Locale;

public enum ExportStage {
   BAKING,
   OPTIMIZING,
   TEXTURES,
   GLB,
   OBJ;

   public String translationKey() {
      return "meshify.stage." + this.name().toLowerCase(Locale.ROOT);
   }
}
