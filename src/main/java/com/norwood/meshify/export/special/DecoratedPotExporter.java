package com.norwood.meshify.export.special;

import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.ObjTemplate;
import com.norwood.meshify.export.TextureKey;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.block.DecoratedPotPattern;
import net.minecraft.block.DecoratedPotPatterns;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.block.entity.Sherds;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;

public final class DecoratedPotExporter {
   private static final String PLAIN_SIDE = "decorated_pot_side";

   private DecoratedPotExporter() {
   }

   public static boolean export(MeshBuilder mesh, BlockRenderView world, BlockPos pos, BlockState state, double blockX, double blockY, double blockZ, List<String> warnings) {
      ObjTemplate template;
      try {
         template = ObjTemplate.getOrLoad("decorated_pot.obj");
      } catch (IOException exception) {
         warnings.add("decorated pot template failed: " + exception.getMessage());
         return false;
      }

      Direction facing = state.contains(Properties.HORIZONTAL_FACING) ? state.get(Properties.HORIZONTAL_FACING) : Direction.NORTH;
      Sherds sherds = world.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot ? pot.getSherds() : Sherds.DEFAULT;
      TextureKey base = TextureKey.resource(Identifier.ofVanilla("textures/entity/decorated_pot/decorated_pot_base.png"));
      TextureKey front = sherdTexture(sherds.front());
      TextureKey back = sherdTexture(sherds.back());
      TextureKey left = sherdTexture(sherds.left());
      TextureKey right = sherdTexture(sherds.right());
      // Which side a face is on, read from its normal in the template's own, unturned frame.
      template.emit(mesh, null, (material, normal) -> {
         if (!material.contains(PLAIN_SIDE)) {
            return base;
         } else if (Math.abs(normal[0]) >= Math.abs(normal[2])) {
            return normal[0] > 0.0F ? left : right;
         } else {
            return normal[2] > 0.0F ? back : front;
         }
      }, blockX, blockY, blockZ, Yaw.of(facing));
      return true;
   }

   private static TextureKey sherdTexture(Optional<Item> sherd) {
      String name = PLAIN_SIDE;
      if (sherd.isPresent()) {
         RegistryKey<DecoratedPotPattern> patternKey = DecoratedPotPatterns.fromSherd(sherd.get());
         if (patternKey != null) {
            DecoratedPotPattern pattern = Registries.DECORATED_POT_PATTERN.get(patternKey);
            if (pattern != null) {
               name = pattern.assetId().getPath();
            }
         }
      }

      return TextureKey.resource(Identifier.ofVanilla("textures/entity/decorated_pot/" + name + ".png"));
   }
}
