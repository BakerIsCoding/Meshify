package com.norwood.meshify.export.special;

import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.ObjTemplate;
import com.norwood.meshify.export.TextureKey;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotPattern;
import net.minecraft.world.level.block.entity.PotDecorations;
//? if >=26.3 {
/*import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStackTemplate;
*///?} else {
import java.util.ArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.DecoratedPotPatterns;
//?}
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class DecoratedPotExporter {
   private static final String PLAIN_SIDE = "decorated_pot_side";

   private DecoratedPotExporter() {
   }

   public static boolean export(MeshBuilder mesh, BlockGetter world, BlockPos pos, BlockState state, double blockX, double blockY, double blockZ, List<String> warnings) {
      ObjTemplate template;
      try {
         template = ObjTemplate.getOrLoad("decorated_pot.obj");
      } catch (IOException exception) {
         warnings.add("decorated pot template failed: " + exception.getMessage());
         return false;
      }

      Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
      PotDecorations sherds = world.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot ? pot.getDecorations() : PotDecorations.EMPTY;
      TextureKey base = potTexture("decorated_pot_base");
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

   // From 26.3 a sherd carries its pattern as an item component, and patterns are no longer a built-in registry.
   //? if >=26.3 {
   /*private static TextureKey sherdTexture(Optional<ItemStackTemplate> sherd) {
      Holder<DecoratedPotPattern> pattern = sherd.map(template -> template.get(DataComponents.PROVIDES_POTTERY_PATTERN)).orElse(null);
      return potTexture(pattern != null ? pattern.value().assetId().getPath() : PLAIN_SIDE);
   }
   *///?} else {
   private static TextureKey sherdTexture(Optional<Item> sherd) {
      ResourceKey<DecoratedPotPattern> patternKey = sherd.map(DecoratedPotExporter::patternOf).orElse(null);
      DecoratedPotPattern pattern = patternKey != null ? BuiltInRegistries.DECORATED_POT_PATTERN.getValue(patternKey) : null;
      return potTexture(pattern != null ? pattern.assetId().getPath() : PLAIN_SIDE);
   }
   //?}

   // 26.2 no longer looks a pattern up by item; it only lists every item and pattern pair.
   //? if 26.2 {
   /*private static ResourceKey<DecoratedPotPattern> patternOf(Item item) {
      ResourceKey<Item> itemKey = BuiltInRegistries.ITEM.getResourceKey(item).orElse(null);
      List<ResourceKey<DecoratedPotPattern>> found = new ArrayList<>(1);
      DecoratedPotPatterns.itemToPatternMappings((mappedItem, pattern) -> {
         if (mappedItem.equals(itemKey)) {
            found.add(pattern);
         }
      });
      return found.isEmpty() ? null : found.getFirst();
   }
   *///?} elif <26.2 {
   private static ResourceKey<DecoratedPotPattern> patternOf(Item item) {
      return DecoratedPotPatterns.getPatternFromItem(item);
   }
   //?}

   private static TextureKey potTexture(String name) {
      return TextureKey.resource(Identifier.withDefaultNamespace("textures/entity/decorated_pot/" + name + ".png"));
   }
}
