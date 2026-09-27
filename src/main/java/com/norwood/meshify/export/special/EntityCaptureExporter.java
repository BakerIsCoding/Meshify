package com.norwood.meshify.export.special;

import com.norwood.meshify.export.MeshBuilder;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderManager;
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

   /** Captures every entity in the region except players; returns how many produced geometry. */
   public static int export(MeshBuilder mesh, MinecraftClient client, ClientWorld world, BlockPos min, BlockPos max, List<String> warnings) {
      List<Entity> entities = world.getOtherEntities(null, Box.enclosing(min, max), entity -> !(entity instanceof PlayerEntity));
      EntityRenderManager renderManager = client.getEntityRenderDispatcher();
      int captured = 0;

      for (Entity entity : entities) {
         try {
            CaptureQueue queue = new CaptureQueue(mesh, client, warnings);
            if (entity instanceof ItemFrameEntity frame) {
               MapIdComponent mapId = frame.getMapId(frame.getHeldItemStack());
               if (mapId != null) {
                  queue.itemFrameMapId = mapId.id();
               }
            }

            renderManager.render(
               renderManager.getAndUpdateRenderState(entity, 0.0F),
               BlockEntityCaptureExporter.dummyCamera(entity.getBlockPos()),
               entity.getX() - min.getX(),
               entity.getY() - min.getY(),
               entity.getZ() - min.getZ(),
               new MatrixStack(),
               queue
            );
            if (queue.capturedQuads > 0) {
               captured++;
            }
         } catch (Exception exception) {
            warnings.add("entity capture failed: " + Registries.ENTITY_TYPE.getId(entity.getType()) + " @ " + entity.getBlockPos().toShortString() + ": " + exception);
         }
      }

      return captured;
   }
}
