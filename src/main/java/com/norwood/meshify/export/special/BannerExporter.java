package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TexKey;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.AbstractBannerBlock;
import net.minecraft.block.BannerBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.component.type.BannerPatternsComponent.Layer;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;

public final class BannerExporter {
   private static final double T = 0.6666666666666666;

   private BannerExporter() {
   }

   public static void export(
      MeshBuilder mesh, BlockRenderView world, BlockPos pos, BlockState state, AbstractBannerBlock block, double bx, double by, double bz
   ) {
      TexKey texkey = buildTexture(world, pos, block.getColor());
      boolean flag = state.contains(BannerBlock.ROTATION);
      if (flag) {
         double d0 = 180.0 + state.get(BannerBlock.ROTATION).intValue() * 22.5;
         BoxUv.emit(
            mesh,
            texkey,
            bx,
            by,
            bz,
            7.333333333333333,
            0.0,
            7.333333333333333,
            8.666666666666666,
            28.0,
            8.666666666666666,
            44,
            0,
            2,
            42,
            2,
            64,
            64,
            d0,
            8.0,
            8.0,
            0.0
         );
         BoxUv.emit(
            mesh,
            texkey,
            bx,
            by,
            bz,
            1.333333333333334,
            28.0,
            7.333333333333333,
            14.666666666666666,
            29.333333333333332,
            8.666666666666666,
            0,
            42,
            20,
            2,
            2,
            64,
            64,
            d0,
            8.0,
            8.0,
            0.0
         );
         BoxUv.emit(
            mesh,
            texkey,
            bx,
            by,
            bz,
            1.333333333333334,
            2.6666666666666665,
            6.666666666666667,
            14.666666666666666,
            29.333333333333332,
            7.333333333333333,
            0,
            0,
            20,
            40,
            1,
            64,
            64,
            d0,
            8.0,
            8.0,
            0.0
         );
      } else {
         Direction direction = state.get(Properties.HORIZONTAL_FACING);

         double d1 = switch (direction) {
            case EAST -> 90.0;
            case SOUTH -> 180.0;
            case WEST -> 270.0;
            default -> 0.0;
         };
         BoxUv.emit(
            mesh,
            texkey,
            bx,
            by,
            bz,
            1.333333333333334,
            12.333333333333332,
            14.333333333333334,
            14.666666666666666,
            13.666666666666666,
            15.666666666666666,
            0,
            42,
            20,
            2,
            2,
            64,
            64,
            d1,
            8.0,
            8.0,
            0.0
         );
         BoxUv.emit(
            mesh,
            texkey,
            bx,
            by,
            bz,
            1.333333333333334,
            -12.999999999999998,
            13.666666666666668,
            14.666666666666666,
            13.666666666666666,
            14.333333333333334,
            0,
            0,
            20,
            40,
            1,
            64,
            64,
            d1,
            8.0,
            8.0,
            0.0
         );
      }
   }

   private static TexKey buildTexture(BlockRenderView world, BlockPos pos, DyeColor baseColor) {
      List<TexKey.CompositeLayer> list = new ArrayList<>();
      list.add(new TexKey.CompositeLayer(Identifier.of("minecraft", "textures/entity/banner_base.png"), -1));
      list.add(new TexKey.CompositeLayer(Identifier.of("minecraft", "textures/entity/banner/base.png"), baseColor.getEntityColor() & 16777215));
      StringBuilder stringbuilder = new StringBuilder();
      if (world.getBlockEntity(pos) instanceof BannerBlockEntity bannerblockentity) {
         for (Layer layer : bannerblockentity.getPatterns().layers()) {
            Identifier identifier = layer.pattern().value().assetId();
            list.add(
               new TexKey.CompositeLayer(
                  Identifier.of(identifier.getNamespace(), "textures/entity/banner/" + identifier.getPath() + ".png"),
                  layer.color().getEntityColor() & 16777215
               )
            );
            stringbuilder.append(identifier).append('#').append(layer.color().getId()).append(';');
         }
      }

      String s = "banner_" + baseColor.getId();
      if (stringbuilder.length() > 0) {
         s = s + "_" + Integer.toHexString(stringbuilder.toString().hashCode());
      }

      return TexKey.composite(TexKey.sanitize(s), list);
   }
}
