package com.norwood.meshify.export;

import net.minecraft.world.phys.Vec3;

// Where a model goes: into mesh with its block's lowest corner at origin, turned yawDegrees about the block's vertical centre line.
public record Placement(MeshBuilder mesh, Vec3 origin, double yawDegrees) {
}
