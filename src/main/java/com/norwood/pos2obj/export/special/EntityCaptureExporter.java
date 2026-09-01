package com.norwood.pos2obj.export.special;

import com.norwood.pos2obj.export.MeshBuilder;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class EntityCaptureExporter {
   private EntityCaptureExporter() {
   }

   public static int export(MeshBuilder mesh, MinecraftClient mc, ClientWorld world, BlockPos min, BlockPos max, List<String> warnings) {
      Box box = new Box(min.getX(), min.getY(), min.getZ(), max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
      List<Entity> list = world.getOtherEntities(null, box, ex -> !(ex instanceof PlayerEntity));
      if (list.isEmpty()) {
         return 0;
      } else {
         EntityRenderManager entityrendermanager = mc.getEntityRenderDispatcher();
         int i = 0;

         for (Entity entity : list) {
            try {
               EntityRenderState entityrenderstate = entityrendermanager.getAndUpdateRenderState(entity, 0.0F);
               MatrixStack matrixstack = new MatrixStack();
               CaptureQueue capturequeue = new CaptureQueue(mesh, mc, warnings);
               if (entity instanceof ItemFrameEntity itemframeentity) {
                  MapIdComponent mapidcomponent = itemframeentity.getMapId(itemframeentity.getHeldItemStack());
                  if (mapidcomponent != null) {
                     capturequeue.contextMapId = mapidcomponent.id();
                  }
               }

               entityrendermanager.render(
                  entityrenderstate,
                  BerCaptureExporter.dummyCamera(entity.getBlockPos()),
                  entity.getX() - min.getX(),
                  entity.getY() - min.getY(),
                  entity.getZ() - min.getZ(),
                  matrixstack,
                  capturequeue
               );
               if (capturequeue.captured > 0) {
                  i++;
               }
            } catch (Exception exception) {
               warnings.add(
                  "entity capture failed: " + Registries.ENTITY_TYPE.getId(entity.getType()) + " @ " + entity.getBlockPos().toShortString() + ": " + exception
               );
            }
         }

         return i;
      }
   }
}
