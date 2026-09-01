package com.norwood.pos2obj.export.special;

import com.norwood.pos2obj.export.BoxUv;
import com.norwood.pos2obj.export.FontRaster;
import com.norwood.pos2obj.export.MeshBuilder;
import com.norwood.pos2obj.export.ObjTemplate;
import com.norwood.pos2obj.export.TexKey;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.WallHangingSignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;

public final class SignExporter {
   private static final int TEXT_W = 128;
   private static final int TEXT_H = 64;

   private SignExporter() {
   }

   public static boolean export(
      MeshBuilder mesh,
      MinecraftClient mc,
      BlockRenderView world,
      BlockPos pos,
      BlockState state,
      AbstractSignBlock block,
      double bx,
      double by,
      double bz,
      List<String> warnings
   ) {
      boolean flag = block instanceof SignBlock;
      boolean flag1 = block instanceof WallSignBlock;
      boolean flag2 = block instanceof HangingSignBlock;
      boolean flag3 = block instanceof WallHangingSignBlock;
      if (!flag && !flag1 && !flag2 && !flag3) {
         return false;
      } else {
         String s = TexKey.sanitize(block.getWoodType().name());
         String s1 = !flag2 && !flag3 ? "textures/entity/signs/" + s + ".png" : "textures/entity/signs/hanging/" + s + ".png";
         Identifier identifier = Identifier.of("minecraft", s1);
         if (mc.getResourceManager().getResource(identifier).isEmpty()) {
            warnings.add("sign texture not found, skipped: " + identifier + " @ " + pos.toShortString());
            return false;
         } else {
            TexKey texkey = TexKey.resource(identifier);
            if (flag) {
               double d0 = 180.0 + state.get(SignBlock.ROTATION).intValue() * 22.5;
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
                  9.333333333333334,
                  8.666666666666666,
                  0,
                  14,
                  2,
                  14,
                  2,
                  64,
                  32,
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
                  0.0,
                  9.333333333333334,
                  7.333333333333333,
                  16.0,
                  17.333333333333332,
                  8.666666666666666,
                  0,
                  0,
                  24,
                  12,
                  2,
                  64,
                  32,
                  d0,
                  8.0,
                  8.0,
                  0.0
               );
               emitTexts(mesh, mc, world, pos, bx, by, bz, d0, warnings, 1.5, 14.5, 10.0, 16.5, 7.233333333333333, 8.766666666666666);
            } else if (flag1) {
               Direction direction = state.get(Properties.HORIZONTAL_FACING);
               double d1 = horizontalYaw(direction);
               BoxUv.emit(mesh, texkey, bx, by, bz, 0.0, 4.0, 14.666666666666666, 16.0, 12.0, 16.0, 0, 0, 24, 12, 2, 64, 32, d1, 8.0, 8.0, 0.0);
               emitTexts(mesh, mc, world, pos, bx, by, bz, d1, warnings, 1.5, 14.5, 4.75, 11.25, 14.566666666666666, Double.NaN);
            } else {
               ObjTemplate objtemplate;
               try {
                  objtemplate = ObjTemplate.get("hanging_sign.obj");
               } catch (IOException ioexception) {
                  warnings.add("hanging sign template failed: " + ioexception.getMessage());
                  return false;
               }

               double d2;
               if (!flag2) {
                  Direction direction1 = state.get(Properties.HORIZONTAL_FACING);

                  d2 = switch (direction1) {
                     case NORTH -> 180.0;
                     case EAST -> 270.0;
                     case WEST -> 90.0;
                     default -> 0.0;
                  };
                  objtemplate.emit(mesh, "sign", (m, n) -> texkey, bx, by, bz, d2);
                  objtemplate.emit(mesh, "chains", (m, n) -> texkey, bx, by, bz, d2);
                  objtemplate.emit(mesh, "top_bar", (m, n) -> texkey, bx, by, bz, d2);
               } else {
                  d2 = state.get(HangingSignBlock.ROTATION).intValue() * 22.5;
                  boolean flag4 = state.contains(HangingSignBlock.ATTACHED) && state.get(HangingSignBlock.ATTACHED);
                  objtemplate.emit(mesh, "sign", (m, n) -> texkey, bx, by, bz, d2);
                  objtemplate.emit(mesh, flag4 ? "chains_attached" : "chains", (m, n) -> texkey, bx, by, bz, d2);
               }

               emitTexts(mesh, mc, world, pos, bx, by, bz, d2, warnings, 2.0, 14.0, 2.0, 8.0, 6.9, 9.1);
            }

            return true;
         }
      }
   }

   private static double horizontalYaw(Direction f) {
      return switch (f) {
         case EAST -> 90.0;
         case WEST -> 270.0;
         case SOUTH -> 180.0;
         default -> 0.0;
      };
   }

   private static void emitTexts(
      MeshBuilder mesh,
      MinecraftClient mc,
      BlockRenderView world,
      BlockPos pos,
      double bx,
      double by,
      double bz,
      double yaw,
      List<String> warnings,
      double x0,
      double x1,
      double y0,
      double y1,
      double zFront,
      double zBack
   ) {
      if (world.getBlockEntity(pos) instanceof SignBlockEntity signblockentity) {
         TexKey texkey1 = textTexture(mc, signblockentity.getFrontText(), warnings);
         if (texkey1 != null) {
            BoxUv.rotatedQuad(
               mesh,
               texkey1,
               bx,
               by,
               bz,
               new double[][]{{x1, y0, zFront}, {x0, y0, zFront}, {x0, y1, zFront}, {x1, y1, zFront}},
               new double[][]{{0.0, 64.0}, {128.0, 64.0}, {128.0, 0.0}, {0.0, 0.0}},
               128,
               64,
               yaw,
               8.0,
               8.0,
               new float[]{0.0F, 0.0F, -1.0F}
            );
         }

         if (!Double.isNaN(zBack)) {
            TexKey texkey = textTexture(mc, signblockentity.getBackText(), warnings);
            if (texkey != null) {
               BoxUv.rotatedQuad(
                  mesh,
                  texkey,
                  bx,
                  by,
                  bz,
                  new double[][]{{x0, y0, zBack}, {x1, y0, zBack}, {x1, y1, zBack}, {x0, y1, zBack}},
                  new double[][]{{0.0, 64.0}, {128.0, 64.0}, {128.0, 0.0}, {0.0, 0.0}},
                  128,
                  64,
                  yaw,
                  8.0,
                  8.0,
                  new float[]{0.0F, 0.0F, 1.0F}
               );
            }
         }
      }
   }

   private static TexKey textTexture(MinecraftClient mc, SignText signText, List<String> warnings) {
      Text[] atext = signText.getMessages(false);
      String[] astring = new String[atext.length];
      boolean flag = false;
      StringBuilder stringbuilder = new StringBuilder();

      for (int i = 0; i < atext.length; i++) {
         astring[i] = atext[i] == null ? "" : atext[i].getString();
         stringbuilder.append(astring[i]).append('\n');
         if (!astring[i].isBlank()) {
            flag = true;
         }
      }

      if (!flag) {
         return null;
      } else if (!FontRaster.ensureLoaded(mc, warnings)) {
         return null;
      } else {
         int j = signText.getColor().getSignColor() & 16777215;
         String s = String.format(Locale.ROOT, "sign_text_%08x_%06x", stringbuilder.toString().hashCode(), j);
         return TexKey.generated(s, client -> FontRaster.render(astring, j, 128, 64));
      }
   }
}
