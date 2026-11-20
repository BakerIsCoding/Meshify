package com.norwood.meshify.export;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Vector3fc;
//? if >=1.21.11 {
import net.minecraft.client.model.geom.builders.UVPair;
//?} else {
/*import net.minecraft.client.renderer.block.model.FaceBakery;
import org.joml.Vector3f;
*///?}

// Reads a baked quad, which each version stores differently.
public final class BakedQuads {
   private BakedQuads() {
   }

   public static TextureAtlasSprite sprite(BakedQuad quad) {
      //? if >=26.1 {
      /*return quad.materialInfo().sprite();
      *///?} else
      return quad.sprite();
   }

   // The quad's tint layer, or -1 when it is not tinted.
   public static int tintIndex(BakedQuad quad) {
      //? if >=26.1 {
      /*return quad.materialInfo().tintIndex();
      *///?} else
      return quad.tintIndex();
   }

   public static Vector3fc position(BakedQuad quad, int corner) {
      //? if >=1.21.11 {
      return quad.position(corner);
      //?} else {
      /*int start = corner * FaceBakery.VERTEX_INT_SIZE;
      int[] data = quad.vertices();
      return new Vector3f(Float.intBitsToFloat(data[start]), Float.intBitsToFloat(data[start + 1]), Float.intBitsToFloat(data[start + 2]));
      *///?}
   }

   // The corner's UV, restated within sprite.
   public static float[] spriteUv(BakedQuad quad, TextureAtlasSprite sprite, int corner) {
      //? if >=1.21.11 {
      long packed = quad.packedUV(corner);
      return TextureKey.spriteUv(sprite, UVPair.unpackU(packed), UVPair.unpackV(packed));
      //?} else {
      /*int start = corner * FaceBakery.VERTEX_INT_SIZE + FaceBakery.UV_INDEX;
      int[] data = quad.vertices();
      return TextureKey.spriteUv(sprite, Float.intBitsToFloat(data[start]), Float.intBitsToFloat(data[start + 1]));
      *///?}
   }
}
