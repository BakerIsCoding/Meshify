package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BlockContext;
import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.FontRaster;
import com.norwood.meshify.export.ObjTemplate;
import com.norwood.meshify.export.Placement;
import com.norwood.meshify.export.TextureKey;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
//? if >=26.4
//import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
//? if >=26.3
//import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

public final class SignExporter {
   // Four lines of the game's sign font. The text quads are all twice as wide as they are tall.
   private static final int TEXT_HEIGHT = 4 * FontRaster.LINE_HEIGHT;
   private static final int TEXT_WIDTH = 2 * TEXT_HEIGHT;
   // The whole text texture, in the corner order both text quads use.
   private static final double[][] TEXT_UV = {{0.0, TEXT_HEIGHT}, {TEXT_WIDTH, TEXT_HEIGHT}, {TEXT_WIDTH, 0.0}, {0.0, 0.0}};
   private static final TextArea STANDING_TEXT = new TextArea(1.5, 14.5, 10.0, 16.5, 7.233333333333333, 8.766666666666666);
   // The back is against the wall, so only the front gets text.
   private static final TextArea WALL_TEXT = new TextArea(1.5, 14.5, 4.75, 11.25, 14.566666666666666, Double.NaN);
   private static final TextArea HANGING_TEXT = new TextArea(2.0, 14.0, 2.0, 8.0, 6.9, 9.1);

   private SignExporter() {
   }

   public static boolean export(BlockContext context, SignBlock block) {
      if (!isHanging(block) && !(block instanceof StandingSignBlock) && !(block instanceof WallSignBlock)) {
         return false;
      }

      double yaw = yaw(context.state(), block);
      // From 26.2 the sign itself is a block model, which the caller exports; only the text is drawn here.
      //? if <26.2 {
      if (!emitBody(context, block, yaw)) {
         return false;
      }
      //?}

      if (block instanceof StandingSignBlock) {
         emitTexts(context, yaw, STANDING_TEXT);
      } else if (block instanceof WallSignBlock) {
         emitTexts(context, yaw, WALL_TEXT);
      } else {
         emitTexts(context, yaw, HANGING_TEXT);
      }

      return true;
   }

   private static boolean isHanging(SignBlock block) {
      return block instanceof CeilingHangingSignBlock || block instanceof WallHangingSignBlock;
   }

   private static double yaw(BlockState state, SignBlock block) {
      if (block instanceof StandingSignBlock) {
         return Yaw.ofRotation(state);
      } else if (block instanceof WallSignBlock) {
         return Yaw.of(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
      } else if (block instanceof WallHangingSignBlock) {
         return Yaw.of(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
      } else {
         return state.getValue(BlockStateProperties.ROTATION_16) * Yaw.ROTATION_STEP;
      }
   }

   // The sign without its text, built from the sign texture the renderer draws before 26.2; false when that fails.
   private static boolean emitBody(BlockContext context, SignBlock block, double yaw) {
      String wood = TextureKey.sanitize(block.type().name());
      Identifier texturePath = Identifier.withDefaultNamespace("textures/entity/signs/" + (isHanging(block) ? "hanging/" : "") + wood + ".png");
      if (context.client().getResourceManager().getResource(texturePath).isEmpty()) {
         context.warnings().add("sign texture not found, skipped: " + texturePath + " @ " + context.pos().toShortString());
         return false;
      }

      TextureKey texture = TextureKey.resource(texturePath);
      // The standing and wall signs are the sign model's cuboids at the 2/3 scale its renderer draws them.
      if (block instanceof StandingSignBlock) {
         BoxUv sign = new BoxUv(context.placement(yaw), texture, 64, 32);
         // Post, board.
         sign.box(
            new AABB(7.333333333333333, 0.0, 7.333333333333333, 8.666666666666666, 9.333333333333334, 8.666666666666666), 0, 14, new Vec3i(2, 14, 2)
         );
         sign.box(new AABB(0.0, 9.333333333333334, 7.333333333333333, 16.0, 17.333333333333332, 8.666666666666666), 0, 0, new Vec3i(24, 12, 2));
      } else if (block instanceof WallSignBlock) {
         new BoxUv(context.placement(yaw), texture, 64, 32).box(new AABB(0.0, 4.0, 14.666666666666666, 16.0, 12.0, 16.0), 0, 0, new Vec3i(24, 12, 2));
      } else {
         ObjTemplate template;
         try {
            template = ObjTemplate.getOrLoad("hanging_sign.obj");
         } catch (IOException exception) {
            context.warnings().add("hanging sign template failed: " + exception.getMessage());
            return false;
         }

         Placement placement = context.placement(yaw);
         BiFunction<String, float[], TextureKey> wholeTexture = (material, normal) -> texture;
         if (block instanceof WallHangingSignBlock) {
            template.emit(placement, "sign", wholeTexture);
            template.emit(placement, "chains", wholeTexture);
            template.emit(placement, "top_bar", wholeTexture);
         } else {
            BlockState state = context.state();
            boolean attached = state.hasProperty(CeilingHangingSignBlock.ATTACHED) && state.getValue(CeilingHangingSignBlock.ATTACHED);
            template.emit(placement, "sign", wholeTexture);
            template.emit(placement, attached ? "chains_attached" : "chains", wholeTexture);
         }
      }

      return true;
   }

   private static void emitTexts(BlockContext context, double yaw, TextArea area) {
      if (!(context.world().getBlockEntity(context.pos()) instanceof SignBlockEntity sign)) {
         return;
      }

      //? if >=26.3 {
      /*SignText frontText = sign.getText(SignTextSlot.FRONT);
      SignText backText = sign.getText(SignTextSlot.BACK);
      *///?} else {
      SignText frontText = sign.getFrontText();
      SignText backText = sign.getBackText();
      //?}
      double x0 = area.x0();
      double x1 = area.x1();
      double y0 = area.y0();
      double y1 = area.y1();
      TextureKey front = textTexture(context.client(), frontText, context.warnings());
      if (front != null) {
         double zFront = area.zFront();
         double[][] corners = {{x1, y0, zFront}, {x0, y0, zFront}, {x0, y1, zFront}, {x1, y1, zFront}};
         new BoxUv(context.placement(yaw), front, TEXT_WIDTH, TEXT_HEIGHT).quad(corners, TEXT_UV, new float[]{0.0F, 0.0F, -1.0F});
      }

      double zBack = area.zBack();
      TextureKey back = Double.isNaN(zBack) ? null : textTexture(context.client(), backText, context.warnings());
      if (back != null) {
         double[][] corners = {{x0, y0, zBack}, {x1, y0, zBack}, {x1, y1, zBack}, {x0, y1, zBack}};
         new BoxUv(context.placement(yaw), back, TEXT_WIDTH, TEXT_HEIGHT).quad(corners, TEXT_UV, new float[]{0.0F, 0.0F, 1.0F});
      }
   }

   // The side's text rasterised, or null when it is blank or the font would not load.
   private static TextureKey textTexture(Minecraft client, SignText signText, List<String> warnings) {
      //? if >=26.3 {
      /*Component[] messages = signText.getMessages(false).toArray(Component[]::new);
      *///?} else
      Component[] messages = signText.getMessages(false);
      String[] lines = new String[messages.length];
      StringBuilder allLines = new StringBuilder();
      boolean blank = true;

      for (int i = 0; i < messages.length; i++) {
         lines[i] = messages[i] == null ? "" : messages[i].getString();
         allLines.append(lines[i]).append('\n');
         blank &= lines[i].isBlank();
      }

      if (blank || !FontRaster.ensureLoaded(client, warnings)) {
         return null;
      }

      // Characters the font lacks would silently vanish from the sign, so they get a warning.
      String missing = allLines.codePoints()
         .filter(codePoint -> codePoint != '\n' && !FontRaster.covers(codePoint))
         .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
         .toString();
      String warning = "sign characters missing from the default font, not drawn: " + missing;
      if (!missing.isEmpty() && !warnings.contains(warning)) {
         warnings.add(warning);
      }

      //? if >=26.4 {
      /*int rgb = AbstractSignRenderer.BRIGHT_TEXT_COLORS.pick(signText.getColor()) & 0xFFFFFF;
      *///?} else
      int rgb = signText.getColor().getTextColor() & 0xFFFFFF;
      String id = String.format(Locale.ROOT, "sign_text_%08x_%06x", allLines.toString().hashCode(), rgb);
      return TextureKey.generated(id, ignored -> FontRaster.render(lines, rgb, TEXT_WIDTH, TEXT_HEIGHT));
   }

   // Where the text lies on a board, in model pixels: the front at zFront, the back at zBack unless that is NaN.
   private record TextArea(double x0, double x1, double y0, double y1, double zFront, double zBack) {
   }
}
