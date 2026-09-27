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
   /** Four lines of the game's sign font. The text quads are all twice as wide as they are tall. */
   private static final int TEXT_HEIGHT = 4 * FontRaster.LINE_HEIGHT;
   private static final int TEXT_WIDTH = 2 * TEXT_HEIGHT;
   /** The whole text texture, in the corner order both text quads use. */
   private static final double[][] TEXT_UV = {{0.0, TEXT_HEIGHT}, {TEXT_WIDTH, TEXT_HEIGHT}, {TEXT_WIDTH, 0.0}, {0.0, 0.0}};

   private SignExporter() {
   }

   public static boolean export(
      MeshBuilder mesh,
      MinecraftClient client,
      BlockRenderView world,
      BlockPos pos,
      BlockState state,
      AbstractSignBlock block,
      double blockX,
      double blockY,
      double blockZ,
      List<String> warnings
   ) {
      boolean hanging = block instanceof HangingSignBlock || block instanceof WallHangingSignBlock;
      if (!hanging && !(block instanceof SignBlock) && !(block instanceof WallSignBlock)) {
         return false;
      }

      String wood = TextureKey.sanitize(block.getWoodType().name());
      Identifier texturePath = Identifier.ofVanilla("textures/entity/signs/" + (hanging ? "hanging/" : "") + wood + ".png");
      if (client.getResourceManager().getResource(texturePath).isEmpty()) {
         warnings.add("sign texture not found, skipped: " + texturePath + " @ " + pos.toShortString());
         return false;
      }

      TextureKey texture = TextureKey.resource(texturePath);
      // The standing and wall signs are the sign model's cuboids at the 2/3 scale its renderer draws them.
      if (block instanceof SignBlock) {
         double yaw = Yaw.ofRotation(state);
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
         emitTexts(mesh, client, world, pos, blockX, blockY, blockZ, yaw, warnings, 1.5, 14.5, 10.0, 16.5, 7.233333333333333, 8.766666666666666);
      } else if (block instanceof WallSignBlock) {
         double yaw = Yaw.of(state.get(Properties.HORIZONTAL_FACING));
         BoxUv.emit(mesh, texture, blockX, blockY, blockZ, 0.0, 4.0, 14.666666666666666, 16.0, 12.0, 16.0, 0, 0, 24, 12, 2, 64, 32, yaw, 0.0, Direction.UP, false);
         // The back is against the wall, so only the front gets text.
         emitTexts(mesh, client, world, pos, blockX, blockY, blockZ, yaw, warnings, 1.5, 14.5, 4.75, 11.25, 14.566666666666666, Double.NaN);
      } else {
         ObjTemplate template;
         try {
            template = ObjTemplate.getOrLoad("hanging_sign.obj");
         } catch (IOException exception) {
            warnings.add("hanging sign template failed: " + exception.getMessage());
            return false;
         }

         BiFunction<String, float[], TextureKey> wholeTexture = (material, normal) -> texture;
         double yaw;
         if (block instanceof WallHangingSignBlock) {
            yaw = Yaw.of(state.get(Properties.HORIZONTAL_FACING).getOpposite());
            template.emit(mesh, "sign", wholeTexture, blockX, blockY, blockZ, yaw);
            template.emit(mesh, "chains", wholeTexture, blockX, blockY, blockZ, yaw);
            template.emit(mesh, "top_bar", wholeTexture, blockX, blockY, blockZ, yaw);
         } else {
            yaw = state.get(Properties.ROTATION) * Yaw.ROTATION_STEP;
            boolean attached = state.contains(HangingSignBlock.ATTACHED) && state.get(HangingSignBlock.ATTACHED);
            template.emit(mesh, "sign", wholeTexture, blockX, blockY, blockZ, yaw);
            template.emit(mesh, attached ? "chains_attached" : "chains", wholeTexture, blockX, blockY, blockZ, yaw);
         }

         emitTexts(mesh, client, world, pos, blockX, blockY, blockZ, yaw, warnings, 2.0, 14.0, 2.0, 8.0, 6.9, 9.1);
      }

      return true;
   }

   /** Lays the text over the board in model pixels: the front at {@code zFront}, the back at {@code zBack} unless that is NaN. */
   private static void emitTexts(
      MeshBuilder mesh,
      MinecraftClient client,
      BlockRenderView world,
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

      TextureKey front = textTexture(client, sign.getFrontText(), warnings);
      if (front != null) {
         double[][] corners = {{x1, y0, zFront}, {x0, y0, zFront}, {x0, y1, zFront}, {x1, y1, zFront}};
         BoxUv.rotatedQuad(mesh, front, blockX, blockY, blockZ, corners, TEXT_UV, TEXT_WIDTH, TEXT_HEIGHT, yaw, new float[]{0.0F, 0.0F, -1.0F});
      }

      TextureKey back = Double.isNaN(zBack) ? null : textTexture(client, sign.getBackText(), warnings);
      if (back != null) {
         double[][] corners = {{x0, y0, zBack}, {x1, y0, zBack}, {x1, y1, zBack}, {x0, y1, zBack}};
         BoxUv.rotatedQuad(mesh, back, blockX, blockY, blockZ, corners, TEXT_UV, TEXT_WIDTH, TEXT_HEIGHT, yaw, new float[]{0.0F, 0.0F, 1.0F});
      }
   }

   /** The side's text rasterised, or null when it is blank or the font would not load. */
   private static TextureKey textTexture(MinecraftClient client, SignText signText, List<String> warnings) {
      Text[] messages = signText.getMessages(false);
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

      int rgb = signText.getColor().getSignColor() & 0xFFFFFF;
      String id = String.format(Locale.ROOT, "sign_text_%08x_%06x", allLines.toString().hashCode(), rgb);
      return TextureKey.generated(id, ignored -> FontRaster.render(lines, rgb, TEXT_WIDTH, TEXT_HEIGHT));
   }
}
