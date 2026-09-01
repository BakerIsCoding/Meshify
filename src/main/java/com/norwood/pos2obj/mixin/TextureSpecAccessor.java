package com.norwood.pos2obj.mixin;

import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
   targets = {"net/minecraft/client/render/RenderSetup$TextureSpec"}
)
public interface TextureSpecAccessor {
   @Accessor("location")
   Identifier pos2obj$getLocation();
}
