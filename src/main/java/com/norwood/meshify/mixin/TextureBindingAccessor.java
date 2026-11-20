package com.norwood.meshify.mixin;

//? if <1.21.11 {
/*import java.util.Optional;
import net.minecraft.client.renderer.RenderStateShard;
*///?}
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.11 {
import org.spongepowered.asm.mixin.gen.Accessor;
//?} else
/*import org.spongepowered.asm.mixin.gen.Invoker;*/

// Before 1.21.11 the texture lives in a texture state shard, which only hands it out through cutoutTexture().
//? if >=1.21.11 {
@Mixin(targets = "net/minecraft/client/renderer/rendertype/RenderSetup$TextureBinding")
public interface TextureBindingAccessor {
   @Accessor("location")
   Identifier meshify$getLocation();
}
//?} else {
/*@Mixin(RenderStateShard.EmptyTextureStateShard.class)
public interface TextureBindingAccessor {
   @Invoker("cutoutTexture")
   Optional<Identifier> meshify$getLocation();
}
*///?}
