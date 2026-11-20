package com.norwood.meshify.mixin;

//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else
/*import net.minecraft.client.renderer.rendertype.RenderType;*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Before 1.21.11 only composite render types carry state, as a CompositeState instead of a RenderSetup.
//? if >=1.21.11 {
@Mixin(RenderType.class)
public interface RenderTypeStateAccessor {
   @Accessor("state")
   RenderSetup meshify$getState();
}
//?} else {
/*@Mixin(RenderType.CompositeRenderType.class)
public interface RenderTypeStateAccessor {
   @Accessor("state")
   RenderType.CompositeState meshify$getState();
}
*///?}
