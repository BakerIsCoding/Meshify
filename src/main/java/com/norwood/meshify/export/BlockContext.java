package com.norwood.meshify.export;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// One block being exported: where it is in the world, and the mesh, corner and warning list it exports into.
public record BlockContext(Minecraft client, ClientLevel world, BlockPos pos, BlockState state, MeshBuilder mesh, Vec3 origin, List<String> warnings) {
   public Placement placement(double yawDegrees) {
      return new Placement(this.mesh, this.origin, yawDegrees);
   }
}
