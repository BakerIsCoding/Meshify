package com.norwood.meshify.mixin;

import java.util.Map;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({RenderSetup.class})
public interface RenderSetupAccessor {
   @Accessor("textures")
   Map meshify$getTextures();
}
