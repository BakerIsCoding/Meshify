package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BlockContext;
import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.TextureKey;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
//? if >=26.4
//import net.minecraft.util.CommonColors;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers.Layer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

public final class BannerExporter {
   private BannerExporter() {
   }

   // The cuboids are the banner model's, at the 2/3 scale its renderer draws it, so each unwrap is sized apart from its box.
   public static void export(BlockContext context, AbstractBannerBlock block) {
      BlockState state = context.state();
      TextureKey texture = buildTexture(context.world(), context.pos(), block.getColor());
      if (state.hasProperty(BannerBlock.ROTATION)) {
         BoxUv banner = new BoxUv(context.placement(Yaw.ofRotation(state)), texture, 64, 64);
         // Pole, crossbar, flag.
         banner.box(
            new AABB(7.333333333333333, 0.0, 7.333333333333333, 8.666666666666666, 28.0, 8.666666666666666), 44, 0, new Vec3i(2, 42, 2)
         );
         banner.box(
            new AABB(1.333333333333334, 28.0, 7.333333333333333, 14.666666666666666, 29.333333333333332, 8.666666666666666), 0, 42, new Vec3i(20, 2, 2)
         );
         banner.box(
            new AABB(1.333333333333334, 2.6666666666666665, 6.666666666666667, 14.666666666666666, 29.333333333333332, 7.333333333333333),
            0,
            0,
            new Vec3i(20, 40, 1)
         );
      } else {
         BoxUv banner = new BoxUv(context.placement(Yaw.of(state.getValue(BlockStateProperties.HORIZONTAL_FACING))), texture, 64, 64);
         // Crossbar, flag.
         banner.box(
            new AABB(1.333333333333334, 12.333333333333332, 14.333333333333334, 14.666666666666666, 13.666666666666666, 15.666666666666666),
            0,
            42,
            new Vec3i(20, 2, 2)
         );
         banner.box(
            new AABB(1.333333333333334, -12.999999999999998, 13.666666666666668, 14.666666666666666, 13.666666666666666, 14.333333333333334),
            0,
            0,
            new Vec3i(20, 40, 1)
         );
      }
   }

   // 26.4 moved the dye colours out of DyeColor into a lookup table.
   private static int tint(DyeColor color) {
      //? if >=26.4 {
      /*return CommonColors.TEXTURE_TINT_COLORS.pick(color) & 0xFFFFFF;
      *///?} else
      return color.getTextureDiffuseColor() & 0xFFFFFF;
   }

   // The banner base dyed its colour, with each pattern layer painted on top in its own colour.
   private static TextureKey buildTexture(BlockGetter world, BlockPos pos, DyeColor baseColor) {
      List<TextureKey.CompositeLayer> layers = new ArrayList<>();
      //? if >=26.1 {
      /*layers.add(new TextureKey.CompositeLayer(Identifier.withDefaultNamespace("textures/entity/banner/banner_base.png"), -1));
      *///?} else
      layers.add(new TextureKey.CompositeLayer(Identifier.withDefaultNamespace("textures/entity/banner_base.png"), -1));
      layers.add(new TextureKey.CompositeLayer(Identifier.withDefaultNamespace("textures/entity/banner/base.png"), tint(baseColor)));
      StringBuilder patterns = new StringBuilder();
      if (world.getBlockEntity(pos) instanceof BannerBlockEntity banner) {
         for (Layer layer : banner.getPatterns().layers()) {
            Identifier asset = layer.pattern().value().assetId();
            layers.add(
               new TextureKey.CompositeLayer(
                  Identifier.fromNamespaceAndPath(asset.getNamespace(), "textures/entity/banner/" + asset.getPath() + ".png"), tint(layer.color())
               )
            );
            patterns.append(asset).append('#').append(layer.color().getName()).append(';');
         }
      }

      String id = "banner_" + baseColor.getName();
      if (!patterns.isEmpty()) {
         id = id + "_" + Integer.toHexString(patterns.toString().hashCode());
      }

      return TextureKey.composite(TextureKey.sanitize(id), layers);
   }
}
