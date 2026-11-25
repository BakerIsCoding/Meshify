package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BlockContext;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TextureKey;
//? if >=26.1 {
/*import net.minecraft.client.renderer.block.FluidModel;
*///?} else {
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
//?}
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public final class FluidExporter {
   private FluidExporter() {
   }

   // A box up to the fluid's surface, without the faces the same fluid or a full block hides, except on the region's edge.
   public static void export(BlockContext context, BoundingBox bounds) {
      ClientLevel world = context.world();
      BlockPos pos = context.pos();
      FluidState fluid = context.state().getFluidState();
      // From 26.1 the still sprite and the tint come from the game's fluid model; Fabric registers modded fluids there too.
      //? if >=26.1 {
      /*FluidModel model = context.client().getModelManager().getFluidStateModelSet().get(fluid);
      TextureAtlasSprite still = model == null ? null : model.stillMaterial().sprite();
      if (still == null) {
         return;
      }

      int tint = model.tintSource() != null ? model.tintSource().colorInWorld(world.getBlockState(pos), world, pos) : -1;
      TextureKey texture = TextureKey.sprite(still, tint);
      *///?} else {
      FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid.getType());
      TextureAtlasSprite[] sprites = handler == null ? null : handler.getFluidSprites(world, pos, fluid);
      if (sprites == null || sprites.length == 0 || sprites[0] == null) {
         return;
      }

      TextureKey texture = TextureKey.sprite(sprites[0], handler.getFluidColor(world, pos, fluid));
      //?}
      // Under more of the same fluid the column is full height, with no surface of its own.
      boolean submerged = sameFluid(world, pos.above(), fluid);
      float height = submerged ? 1.0F : fluid.getHeight(world, pos);
      float sideTop = 1.0F - height;
      float[][] sideUv = new float[][]{{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, sideTop}, {0.0F, sideTop}};
      if (!submerged) {
         addQuad(
            context,
            texture,
            new MeshBuilder.Quad(
               new float[][]{{0.0F, height, 1.0F}, {1.0F, height, 1.0F}, {1.0F, height, 0.0F}, {0.0F, height, 0.0F}},
               new float[][]{{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}, {0.0F, 0.0F}},
               new float[]{0.0F, 1.0F, 0.0F}
            )
         );
      }

      if (shouldDraw(context, Direction.DOWN, bounds)) {
         addQuad(
            context,
            texture,
            new MeshBuilder.Quad(
               new float[][]{{1.0F, 0.0F, 1.0F}, {0.0F, 0.0F, 1.0F}, {0.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F}},
               new float[][]{{1.0F, 1.0F}, {0.0F, 1.0F}, {0.0F, 0.0F}, {1.0F, 0.0F}},
               new float[]{0.0F, -1.0F, 0.0F}
            )
         );
      }

      if (shouldDraw(context, Direction.NORTH, bounds)) {
         addQuad(
            context,
            texture,
            new MeshBuilder.Quad(
               new float[][]{{1.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, height, 0.0F}, {1.0F, height, 0.0F}}, sideUv, new float[]{0.0F, 0.0F, -1.0F}
            )
         );
      }

      if (shouldDraw(context, Direction.SOUTH, bounds)) {
         addQuad(
            context,
            texture,
            new MeshBuilder.Quad(
               new float[][]{{0.0F, 0.0F, 1.0F}, {1.0F, 0.0F, 1.0F}, {1.0F, height, 1.0F}, {0.0F, height, 1.0F}}, sideUv, new float[]{0.0F, 0.0F, 1.0F}
            )
         );
      }

      if (shouldDraw(context, Direction.WEST, bounds)) {
         addQuad(
            context,
            texture,
            new MeshBuilder.Quad(
               new float[][]{{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 1.0F}, {0.0F, height, 1.0F}, {0.0F, height, 0.0F}}, sideUv, new float[]{-1.0F, 0.0F, 0.0F}
            )
         );
      }

      if (shouldDraw(context, Direction.EAST, bounds)) {
         addQuad(
            context,
            texture,
            new MeshBuilder.Quad(
               new float[][]{{1.0F, 0.0F, 1.0F}, {1.0F, 0.0F, 0.0F}, {1.0F, height, 0.0F}, {1.0F, height, 1.0F}}, sideUv, new float[]{1.0F, 0.0F, 0.0F}
            )
         );
      }
   }

   private static boolean sameFluid(ClientLevel world, BlockPos pos, FluidState fluid) {
      //? if >=26.4 {
      /*return world.getFluidState(pos).is(fluid.getType());
      *///?} else
      return world.getFluidState(pos).getType().isSame(fluid.getType());
   }

   private static boolean shouldDraw(BlockContext context, Direction side, BoundingBox bounds) {
      BlockPos neighbor = context.pos().relative(side);
      if (!bounds.isInside(neighbor)) {
         return true;
      }

      return !sameFluid(context.world(), neighbor, context.state().getFluidState()) && !context.world().getBlockState(neighbor).isSolidRender();
   }

   // quadInBlock has its corners in block units, from the block's lowest corner.
   private static void addQuad(BlockContext context, TextureKey texture, MeshBuilder.Quad quadInBlock) {
      Vec3 origin = context.origin();
      float[][] positions = new float[4][3];

      for (int k = 0; k < 4; k++) {
         positions[k][0] = (float)(origin.x + quadInBlock.positions()[k][0]);
         positions[k][1] = (float)(origin.y + quadInBlock.positions()[k][1]);
         positions[k][2] = (float)(origin.z + quadInBlock.positions()[k][2]);
      }

      context.mesh().addQuad(texture, positions, quadInBlock.uv(), quadInBlock.normal());
   }
}
