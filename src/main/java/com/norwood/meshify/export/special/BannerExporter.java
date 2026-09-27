package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.TextureKey;
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
   private BannerExporter() {
   }

   /** The cuboids are the banner model's, at the 2/3 scale its renderer draws it, so each unwrap is sized apart from its box. */
   public static void export(
      MeshBuilder mesh, BlockRenderView world, BlockPos pos, BlockState state, AbstractBannerBlock block, double blockX, double blockY, double blockZ
   ) {
      TextureKey texture = buildTexture(world, pos, block.getColor());
      if (state.contains(BannerBlock.ROTATION)) {
         double yaw = Yaw.ofRotation(state);
         // Pole, crossbar, flag.
         BoxUv.emit(
            mesh, texture, blockX, blockY, blockZ,
            7.333333333333333, 0.0, 7.333333333333333, 8.666666666666666, 28.0, 8.666666666666666,
            44, 0, 2, 42, 2, 64, 64, yaw, 0.0, Direction.UP, false
         );
         BoxUv.emit(
            mesh, texture, blockX, blockY, blockZ,
            1.333333333333334, 28.0, 7.333333333333333, 14.666666666666666, 29.333333333333332, 8.666666666666666,
            0, 42, 20, 2, 2, 64, 64, yaw, 0.0, Direction.UP, false
         );
         BoxUv.emit(
            mesh, texture, blockX, blockY, blockZ,
            1.333333333333334, 2.6666666666666665, 6.666666666666667, 14.666666666666666, 29.333333333333332, 7.333333333333333,
            0, 0, 20, 40, 1, 64, 64, yaw, 0.0, Direction.UP, false
         );
      } else {
         double yaw = Yaw.of(state.get(Properties.HORIZONTAL_FACING));
         // Crossbar, flag.
         BoxUv.emit(
            mesh, texture, blockX, blockY, blockZ,
            1.333333333333334, 12.333333333333332, 14.333333333333334, 14.666666666666666, 13.666666666666666, 15.666666666666666,
            0, 42, 20, 2, 2, 64, 64, yaw, 0.0, Direction.UP, false
         );
         BoxUv.emit(
            mesh, texture, blockX, blockY, blockZ,
            1.333333333333334, -12.999999999999998, 13.666666666666668, 14.666666666666666, 13.666666666666666, 14.333333333333334,
            0, 0, 20, 40, 1, 64, 64, yaw, 0.0, Direction.UP, false
         );
      }
   }

   /** The banner base dyed its colour, with each pattern layer painted on top in its own colour. */
   private static TextureKey buildTexture(BlockRenderView world, BlockPos pos, DyeColor baseColor) {
      List<TextureKey.CompositeLayer> layers = new ArrayList<>();
      layers.add(new TextureKey.CompositeLayer(Identifier.ofVanilla("textures/entity/banner_base.png"), -1));
      layers.add(new TextureKey.CompositeLayer(Identifier.ofVanilla("textures/entity/banner/base.png"), baseColor.getEntityColor() & 0xFFFFFF));
      StringBuilder patterns = new StringBuilder();
      if (world.getBlockEntity(pos) instanceof BannerBlockEntity banner) {
         for (Layer layer : banner.getPatterns().layers()) {
            Identifier asset = layer.pattern().value().assetId();
            layers.add(
               new TextureKey.CompositeLayer(
                  Identifier.of(asset.getNamespace(), "textures/entity/banner/" + asset.getPath() + ".png"), layer.color().getEntityColor() & 0xFFFFFF
               )
            );
            patterns.append(asset).append('#').append(layer.color().getId()).append(';');
         }
      }

      String id = "banner_" + baseColor.getId();
      if (!patterns.isEmpty()) {
         id = id + "_" + Integer.toHexString(patterns.toString().hashCode());
      }

      return TextureKey.composite(TextureKey.sanitize(id), layers);
   }
}
