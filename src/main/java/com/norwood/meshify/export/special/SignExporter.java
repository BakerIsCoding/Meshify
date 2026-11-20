package com.norwood.meshify.export.special;

import com.norwood.meshify.export.BoxUv;
import com.norwood.meshify.export.FontRaster;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.ObjTemplate;
import com.norwood.meshify.export.TextureKey;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
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

public final class SignExporter {
   /** Four lines of the game's sign font. The text quads are all twice as wide as they are tall. */
   private static final int TEXT_HEIGHT = 4 * FontRaster.LINE_HEIGHT;
   private static final int TEXT_WIDTH = 2 * TEXT_HEIGHT;
   /** The whole text texture, in the corner order both text quads use. */
   private static final double[][] TEXT_UV = {{0.0, TEXT_HEIGHT}, {TEXT_WIDTH, TEXT_HEIGHT}, {TEXT_WIDTH, 0.0}, {0.0, 0.0}};

   private SignExporter() {
   }

   public static boolean export(
      MeshBuilder mesh,
      Minecraft client,
      BlockGetter world,
      BlockPos pos,
      BlockState state,
      SignBlock block,
      double blockX,
      double blockY,
      double blockZ,
      List<String> warnings
   ) {
      boolean hanging = block instanceof CeilingHangingSignBlock || block instanceof WallHangingSignBlock;
      if (!hanging && !(block instanceof StandingSignBlock) && !(block instanceof WallSignBlock)) {
         return false;
      }

      double yaw = yaw(state, block);
      // From 26.2 the sign itself is a block model, which the caller exports; only the text is drawn here.
      //? if <26.2 {
      if (!emitBody(mesh, client, pos, state, block, hanging, yaw, blockX, blockY, blockZ, warnings)) {
         return false;
      }
      //?}

      // The text rectangles, in the model pixels of each board.
      if (block instanceof StandingSignBlock) {
         emitTexts(mesh, client, world, pos, blockX, blockY, blockZ, yaw, warnings, 1.5, 14.5, 10.0, 16.5, 7.233333333333333, 8.766666666666666);
      } else if (block instanceof WallSignBlock) {
         // The back is against the wall, so only the front gets text.
         emitTexts(mesh, client, world, pos, blockX, blockY, blockZ, yaw, warnings, 1.5, 14.5, 4.75, 11.25, 14.566666666666666, Double.NaN);
      } else {
         emitTexts(mesh, client, world, pos, blockX, blockY, blockZ, yaw, warnings, 2.0, 14.0, 2.0, 8.0, 6.9, 9.1);
      }

      return true;
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

   /** The sign without its text, built from the sign texture the renderer draws before 26.2; false when that fails. */
   private static boolean emitBody(
      MeshBuilder mesh,
      Minecraft client,
      BlockPos pos,
      BlockState state,
      SignBlock block,
      boolean hanging,
      double yaw,
      double blockX,
      double blockY,
      double blockZ,
      List<String> warnings
   ) {
      String wood = TextureKey.sanitize(block.type().name());
      Identifier texturePath = Identifier.withDefaultNamespace("textures/entity/signs/" + (hanging ? "hanging/" : "") + wood + ".png");
      if (client.getResourceManager().getResource(texturePath).isEmpty()) {
         warnings.add("sign texture not found, skipped: " + texturePath + " @ " + pos.toShortString());
         return false;
      }

      TextureKey texture = TextureKey.resource(texturePath);
      // The standing and wall signs are the sign model's cuboids at the 2/3 scale its renderer draws them.
      if (block instanceof StandingSignBlock) {
         // Post, board.
         BoxUv.emit(
            mesh, texture, blockX, blockY, blockZ,
            7.333333333333333, 0.0, 7.333333333333333, 8.666666666666666, 9.333333333333334, 8.666666666666666,
            0, 14, 2, 14, 2, 64, 32, yaw, 0.0, Direction.UP, false
         );
         BoxUv.emit(
            mesh, texture, blockX, blockY, blockZ,
            0.0, 9.333333333333334, 7.333333333333333, 16.0, 17.333333333333332, 8.666666666666666,
            0, 0, 24, 12, 2, 64, 32, yaw, 0.0, Direction.UP, false
         );
      } else if (block instanceof WallSignBlock) {
         BoxUv.emit(mesh, texture, blockX, blockY, blockZ, 0.0, 4.0, 14.666666666666666, 16.0, 12.0, 16.0, 0, 0, 24, 12, 2, 64, 32, yaw, 0.0, Direction.UP, false);
      } else {
         ObjTemplate template;
         try {
            template = ObjTemplate.getOrLoad("hanging_sign.obj");
         } catch (IOException exception) {
            warnings.add("hanging sign template failed: " + exception.getMessage());
            return false;
         }

         BiFunction<String, float[], TextureKey> wholeTexture = (material, normal) -> texture;
         if (block instanceof WallHangingSignBlock) {
            template.emit(mesh, "sign", wholeTexture, blockX, blockY, blockZ, yaw);
            template.emit(mesh, "chains", wholeTexture, blockX, blockY, blockZ, yaw);
            template.emit(mesh, "top_bar", wholeTexture, blockX, blockY, blockZ, yaw);
         } else {
            boolean attached = state.hasProperty(CeilingHangingSignBlock.ATTACHED) && state.getValue(CeilingHangingSignBlock.ATTACHED);
            template.emit(mesh, "sign", wholeTexture, blockX, blockY, blockZ, yaw);
            template.emit(mesh, attached ? "chains_attached" : "chains", wholeTexture, blockX, blockY, blockZ, yaw);
         }
      }

      return true;
   }

   /** Lays the text over the board in model pixels: the front at {@code zFront}, the back at {@code zBack} unless that is NaN. */
   private static void emitTexts(
      MeshBuilder mesh,
      Minecraft client,
      BlockGetter world,
      BlockPos pos,
      double blockX,
      double blockY,
      double blockZ,
      double yaw,
      List<String> warnings,
      double x0,
      double x1,
      double y0,
      double y1,
      double zFront,
      double zBack
   ) {
      if (!(world.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
         return;
      }

      //? if >=26.3 {
      /*SignText frontText = sign.getText(SignTextSlot.FRONT);
      SignText backText = sign.getText(SignTextSlot.BACK);
      *///?} else {
      SignText frontText = sign.getFrontText();
      SignText backText = sign.getBackText();
      //?}
      TextureKey front = textTexture(client, frontText, warnings);
      if (front != null) {
         double[][] corners = {{x1, y0, zFront}, {x0, y0, zFront}, {x0, y1, zFront}, {x1, y1, zFront}};
         BoxUv.rotatedQuad(mesh, front, blockX, blockY, blockZ, corners, TEXT_UV, TEXT_WIDTH, TEXT_HEIGHT, yaw, new float[]{0.0F, 0.0F, -1.0F});
      }

      TextureKey back = Double.isNaN(zBack) ? null : textTexture(client, backText, warnings);
      if (back != null) {
         double[][] corners = {{x0, y0, zBack}, {x1, y0, zBack}, {x1, y1, zBack}, {x0, y1, zBack}};
         BoxUv.rotatedQuad(mesh, back, blockX, blockY, blockZ, corners, TEXT_UV, TEXT_WIDTH, TEXT_HEIGHT, yaw, new float[]{0.0F, 0.0F, 1.0F});
      }
   }

   /** The side's text rasterised, or null when it is blank or the font would not load. */
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

      int rgb = signText.getColor().getTextColor() & 0xFFFFFF;
      String id = String.format(Locale.ROOT, "sign_text_%08x_%06x", allLines.toString().hashCode(), rgb);
      return TextureKey.generated(id, ignored -> FontRaster.render(lines, rgb, TEXT_WIDTH, TEXT_HEIGHT));
   }
}
