package com.norwood.meshify.mixin;

//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else
/*import net.minecraft.client.renderer.RenderStateShard;*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Before 1.21.11 a render type inherits its name from RenderStateShard.
//? if >=1.21.11 {
@Mixin(RenderType.class)
//?} else
/*@Mixin(RenderStateShard.class)*/
public interface RenderTypeNameAccessor {
   @Accessor("name")
   String meshify$getName();
}
