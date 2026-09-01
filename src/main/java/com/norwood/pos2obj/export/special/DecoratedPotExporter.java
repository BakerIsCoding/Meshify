package com.norwood.pos2obj.export.special;

import com.norwood.pos2obj.export.MeshBuilder;
import com.norwood.pos2obj.export.ObjTemplate;
import com.norwood.pos2obj.export.TexKey;
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
   private DecoratedPotExporter() {
   }

   public static boolean export(MeshBuilder mesh, BlockRenderView world, BlockPos pos, BlockState state, double bx, double by, double bz, List<String> warnings) {
      ObjTemplate objtemplate;
      try {
         objtemplate = ObjTemplate.get("decorated_pot.obj");
      } catch (IOException ioexception) {
         warnings.add("decorated pot template failed: " + ioexception.getMessage());
         return false;
      }

      Direction direction = state.contains(Properties.HORIZONTAL_FACING) ? state.get(Properties.HORIZONTAL_FACING) : Direction.NORTH;

      double d0 = switch (direction) {
         case EAST -> 90.0;
         case SOUTH -> 180.0;
         case WEST -> 270.0;
         default -> 0.0;
      };
      Sherds sherds = world.getBlockEntity(pos) instanceof DecoratedPotBlockEntity decoratedpotblockentity
         ? decoratedpotblockentity.getSherds()
         : Sherds.DEFAULT;
      TexKey texkey3 = TexKey.resource(Identifier.of("minecraft", "textures/entity/decorated_pot/decorated_pot_base.png"));
      TexKey texkey4 = sherdTexture(sherds.front());
      TexKey texkey = sherdTexture(sherds.back());
      TexKey texkey1 = sherdTexture(sherds.left());
      TexKey texkey2 = sherdTexture(sherds.right());
      objtemplate.emit(mesh, null, (material, normal) -> {
         if (!material.contains("decorated_pot_side")) {
            return texkey3;
         } else if (Math.abs(normal[0]) >= Math.abs(normal[2])) {
            return normal[0] > 0.0F ? texkey1 : texkey2;
         } else {
            return normal[2] > 0.0F ? texkey : texkey4;
         }
      }, bx, by, bz, d0);
      return true;
   }

   private static TexKey sherdTexture(Optional<Item> sherd) {
      String s = "decorated_pot_side";
      if (sherd.isPresent()) {
         RegistryKey<DecoratedPotPattern> registrykey = DecoratedPotPatterns.fromSherd(sherd.get());
         if (registrykey != null) {
            DecoratedPotPattern decoratedpotpattern = Registries.DECORATED_POT_PATTERN.get(registrykey);
            if (decoratedpotpattern != null) {
               s = decoratedpotpattern.assetId().getPath();
            }
         }
      }

      return TexKey.resource(Identifier.of("minecraft", "textures/entity/decorated_pot/" + s + ".png"));
   }
}
