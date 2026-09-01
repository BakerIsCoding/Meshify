package com.norwood.meshify.export.special;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.ObjTemplate;
import com.norwood.meshify.export.TexKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.SkullBlock.SkullType;
import net.minecraft.block.SkullBlock.Type;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;

public final class SkullExporter {
   private SkullExporter() {
   }

   public static boolean export(
      MeshBuilder mesh, BlockRenderView world, BlockPos pos, BlockState state, AbstractSkullBlock block, double bx, double by, double bz, List<String> warnings
   ) {
      SkullType skulltype = block.getSkullType();
      if (skulltype == Type.DRAGON) {
         return exportDragon(mesh, state, pos, bx, by, bz, warnings);
      } else {
         int i = 64;
         boolean flag = false;
         boolean flag1 = false;
         String s;
         if (skulltype == Type.SKELETON) {
            s = "textures/entity/skeleton/skeleton.png";
            i = 32;
         } else if (skulltype == Type.WITHER_SKELETON) {
            s = "textures/entity/skeleton/wither_skeleton.png";
            i = 32;
         } else if (skulltype == Type.CREEPER) {
            s = "textures/entity/creeper/creeper.png";
            i = 32;
         } else if (skulltype == Type.ZOMBIE) {
            s = "textures/entity/zombie/zombie.png";
         } else if (skulltype == Type.PIGLIN) {
            s = "textures/entity/piglin/piglin.png";
            flag1 = true;
         } else {
            if (skulltype != Type.PLAYER) {
               warnings.add("unsupported head type skipped: " + skulltype + " @ " + pos.toShortString());
               return false;
            }

            s = "textures/entity/player/wide/steve.png";
            flag = true;
         }

         TexKey texkey;
         if (skulltype == Type.PLAYER) {
            String s1 = resolveSkinUrl(world, pos);
            texkey = s1 != null ? TexKey.playerSkin(s1) : TexKey.resource(Identifier.of("minecraft", s));
         } else {
            texkey = TexKey.resource(Identifier.of("minecraft", s));
         }

         boolean flag2 = !state.contains(SkullBlock.ROTATION);
         double d0 = flag1 ? 5.0 : 4.0;
         double d1 = 8.0 - d0;
         double d2 = 8.0 + d0;
         double d3;
         double d4;
         double d5;
         double d6;
         double d7;
         if (flag2) {
            Direction direction = state.get(Properties.HORIZONTAL_FACING);

            d3 = switch (direction) {
               case EAST -> 90.0;
               case SOUTH -> 180.0;
               case WEST -> 270.0;
               default -> 0.0;
            };
            d4 = 4.0;
            d5 = 12.0;
            d6 = 8.0;
            d7 = 16.0;
         } else {
            d3 = 180.0 + state.get(SkullBlock.ROTATION).intValue() * 22.5;
            d4 = 0.0;
            d5 = 8.0;
            d6 = 4.0;
            d7 = 12.0;
         }

         BoxUv.emit(mesh, texkey, bx, by, bz, d1, d4, d6, d2, d5, d7, 0, 0, 64, i, d3, 8.0, 8.0, 0.0);
         if (flag) {
            BoxUv.emit(mesh, texkey, bx, by, bz, d1, d4, d6, d2, d5, d7, 32, 0, 64, i, d3, 8.0, 8.0, 0.25);
         }

         return true;
      }
   }

   private static boolean exportDragon(MeshBuilder mesh, BlockState state, BlockPos pos, double bx, double by, double bz, List<String> warnings) {
      boolean flag = !state.contains(SkullBlock.ROTATION);
      double d0;
      if (flag) {
         d0 = switch ((Direction)state.get(Properties.HORIZONTAL_FACING)) {
            case EAST -> 90.0;
            case SOUTH -> 180.0;
            case WEST -> 270.0;
            default -> 0.0;
         };
      } else {
         d0 = 180.0 + state.get(SkullBlock.ROTATION).intValue() * 22.5;
      }

      try {
         ObjTemplate objtemplate = ObjTemplate.get(flag ? "dragon_wall_head.obj" : "dragon_head.obj");
         TexKey texkey = TexKey.resource(Identifier.of("minecraft", "textures/entity/enderdragon/dragon.png"));
         objtemplate.emit(mesh, null, (mat, normal) -> texkey, bx, by, bz, d0);
         return true;
      } catch (IOException ioexception) {
         warnings.add("dragon head template failed @ " + pos.toShortString() + ": " + ioexception.getMessage());
         return false;
      }
   }

   private static String resolveSkinUrl(BlockRenderView world, BlockPos pos) {
      try {
         if (!(world.getBlockEntity(pos) instanceof SkullBlockEntity skullblockentity)) {
            return null;
         }

         ProfileComponent profilecomponent = skullblockentity.getOwner();
         if (profilecomponent == null) {
            return null;
         }

         GameProfile gameprofile = profilecomponent.getGameProfile();
         if (gameprofile == null) {
            return null;
         }

         for (Property property : gameprofile.properties().get("textures")) {
            String s = new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8);
            JsonObject jsonobject = JsonParser.parseString(s).getAsJsonObject();
            JsonObject jsonobject1 = jsonobject.getAsJsonObject("textures");
            if (jsonobject1 != null) {
               JsonObject jsonobject2 = jsonobject1.getAsJsonObject("SKIN");
               if (jsonobject2 != null) {
                  return jsonobject2.get("url").getAsString();
               }
            }
         }
      } catch (Exception exception) {
      }

      return null;
   }
}
