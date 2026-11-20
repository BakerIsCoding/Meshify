package com.norwood.meshify.mixin;

//? if >=1.21.11 {
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderSetup;
//?} else {
/*import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.rendertype.RenderType;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Before 1.21.11 there is no RenderSetup; a CompositeState holds the one texture shard instead.
//? if >=1.21.11 {
@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
   // Values are the package-private RenderSetup.TextureBinding; read them through TextureBindingAccessor.
   @Accessor("textures")
   Map<String, ?> meshify$getTextures();
}
//?} else {
/*@Mixin(RenderType.CompositeState.class)
public interface RenderSetupAccessor {
   @Accessor("textureState")
   RenderStateShard.EmptyTextureStateShard meshify$getTextureState();
}
*///?}
