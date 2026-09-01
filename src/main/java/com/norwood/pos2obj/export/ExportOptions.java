package com.norwood.pos2obj.export;

public record ExportOptions(
   String name, boolean mergeAtlas, int texScale, boolean weldVertices, boolean aggressiveCull, boolean bake, int bakeUvSize, int bakeMaxAtlas
) {
}
