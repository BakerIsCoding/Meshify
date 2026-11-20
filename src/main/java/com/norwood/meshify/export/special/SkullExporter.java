package com.norwood.meshify.export.special;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.ObjTemplate;
import com.norwood.meshify.export.TextureKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SkullBlock.Type;
import net.minecraft.world.level.block.SkullBlock.Types;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class SkullExporter {
   private SkullExporter() {
   }

   public static boolean export(
      MeshBuilder mesh, BlockGetter world, BlockPos pos, BlockState state, AbstractSkullBlock block, double blockX, double blockY, double blockZ, List<String> warnings
   ) {
      Type type = block.getType();
      Head head = headFor(type);
      // Checked before the state is read: a modded head may have neither ROTATION nor HORIZONTAL_FACING.
      if (head == null && type != Types.DRAGON) {
         warnings.add("unsupported head type skipped: " + type + " @ " + pos.toShortString());
         return false;
      }

      boolean wall = !state.hasProperty(SkullBlock.ROTATION);
      double yaw = wall ? Yaw.of(state.getValue(BlockStateProperties.HORIZONTAL_FACING)) : Yaw.ofRotation(state);
      if (type == Types.DRAGON) {
         return exportDragon(mesh, pos, blockX, blockY, blockZ, yaw, wall, warnings);
      }

      Identifier fallback = Identifier.withDefaultNamespace("textures/entity/" + head.texture() + ".png");
      String skinUrl = type == Types.PLAYER ? resolveSkinUrl(world, pos) : null;
      TextureKey texture = skinUrl != null ? TextureKey.playerSkin(skinUrl) : TextureKey.resource(fallback);
      double x0 = 8.0 - head.halfWidth();
      double x1 = 8.0 + head.halfWidth();
      // A wall head sits halfway up, against the wall; a floor head stands on the block's centre.
      double y0 = wall ? 4.0 : 0.0;
      double y1 = wall ? 12.0 : 8.0;
      double z0 = wall ? 8.0 : 4.0;
      double z1 = wall ? 16.0 : 12.0;
      BoxUv.emit(mesh, texture, blockX, blockY, blockZ, x0, y0, z0, x1, y1, z1, 0, 0, 64, head.textureHeight(), yaw, 0.0);
      if (type == Types.PLAYER) {
         // The hat layer, a quarter pixel proud of the head.
         BoxUv.emit(mesh, texture, blockX, blockY, blockZ, x0, y0, z0, x1, y1, z1, 32, 0, 64, head.textureHeight(), yaw, 0.25);
      }

      return true;
   }

   // The head's entity texture, under textures/entity/; null for heads this cuboid can't draw.
   private static Head headFor(Type type) {
      if (!(type instanceof Types vanilla)) {
         return null;
      }

      return switch (vanilla) {
         case SKELETON -> new Head("skeleton/skeleton", 32, 4.0);
         case WITHER_SKELETON -> new Head("skeleton/wither_skeleton", 32, 4.0);
         case CREEPER -> new Head("creeper/creeper", 32, 4.0);
         case ZOMBIE -> new Head("zombie/zombie", 64, 4.0);
         case PIGLIN -> new Head("piglin/piglin", 64, 5.0);
         case PLAYER -> new Head("player/wide/steve", 64, 4.0);
         case DRAGON -> null;
      };
   }

   private static boolean exportDragon(MeshBuilder mesh, BlockPos pos, double blockX, double blockY, double blockZ, double yaw, boolean wall, List<String> warnings) {
      try {
         ObjTemplate template = ObjTemplate.getOrLoad(wall ? "dragon_wall_head.obj" : "dragon_head.obj");
         TextureKey texture = TextureKey.resource(Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png"));
         template.emit(mesh, null, (material, normal) -> texture, blockX, blockY, blockZ, yaw);
         return true;
      } catch (IOException exception) {
         warnings.add("dragon head template failed @ " + pos.toShortString() + ": " + exception.getMessage());
         return false;
      }
   }

   // The owner's skin URL from the head's profile, or null when it has none or it can't be read.
   private static String resolveSkinUrl(BlockGetter world, BlockPos pos) {
      try {
         if (!(world.getBlockEntity(pos) instanceof SkullBlockEntity skull)) {
            return null;
         }

         ResolvableProfile owner = skull.getOwnerProfile();
         GameProfile profile = owner == null ? null : owner.partialProfile();
         if (profile == null) {
            return null;
         }

         for (Property property : profile.properties().get("textures")) {
            String json = new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
            JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
            if (skin != null) {
               return skin.get("url").getAsString();
            }
         }
      } catch (Exception ignored) {
         // A malformed profile just means the default skin.
      }

      return null;
   }

   // An entity texture with the head at its top-left; the piglin's head is wider than the rest.
   private record Head(String texture, int textureHeight, double halfWidth) {
   }
}
