package com.norwood.meshify.export.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.norwood.meshify.export.MeshBuilder;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.AABB;

public final class EntityCaptureExporter {
   private EntityCaptureExporter() {
   }

   // Captures every entity in the region except players; returns how many produced geometry.
   public static int export(MeshBuilder mesh, Minecraft client, ClientLevel world, BlockPos min, BlockPos max, List<String> warnings) {
      List<Entity> entities = world.getEntities((Entity) null, AABB.encapsulatingFullBlocks(min, max), entity -> !(entity instanceof Player));
      EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
      int captured = 0;

      for (Entity entity : entities) {
         try {
            CaptureQueue queue = new CaptureQueue(mesh, client, warnings);
            if (entity instanceof ItemFrame frame) {
               MapId mapId = frame.getFramedMapId(frame.getItem());
               if (mapId != null) {
                  queue.itemFrameMapId = mapId.id();
               }
            }

            dispatcher.submit(
               dispatcher.extractEntity(entity, 0.0F),
               BlockEntityCaptureExporter.dummyCamera(entity.blockPosition()),
               entity.getX() - min.getX(),
               entity.getY() - min.getY(),
               entity.getZ() - min.getZ(),
               new PoseStack(),
               queue
            );
            if (queue.capturedQuads > 0) {
               captured++;
            }
         } catch (Exception exception) {
            warnings.add("entity capture failed: " + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + " @ " + entity.blockPosition().toShortString() + ": " + exception);
         }
      }

      return captured;
   }
}
