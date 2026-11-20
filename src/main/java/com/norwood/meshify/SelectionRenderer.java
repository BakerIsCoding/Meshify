package com.norwood.meshify;

import com.mojang.blaze3d.platform.InputConstants;
//? if >=26.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?} else
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.meshify.MeshifyActions.Feedback;
import com.norwood.meshify.Selection.Corner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
*///?} else
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if <26.2
import net.minecraft.client.renderer.ShapeRenderer;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else
/*import net.minecraft.client.renderer.rendertype.RenderType;*/
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

// The corner keys and the in-world outline of the selection.
public final class SelectionRenderer {
   private static final int START_COLOR = 0xFF55FF55;
   private static final int END_COLOR = 0xFFFF5555;
   private static final int REGION_COLOR = 0xFF55FFFF;
   private static final float REGION_LINE_WIDTH = 3.0F;
   private static final float CORNER_LINE_WIDTH = 5.0F;
   // Grows the corner outlines just past the region's, so the two never z-fight.
   private static final double CORNER_NUDGE = 0.002;

   private static KeyMapping setStartKey;
   private static KeyMapping setEndKey;

   private SelectionRenderer() {
   }

   public static void register() {
      setStartKey = KeyBindingHelper.registerKeyBinding(
         new KeyMapping("key.meshify.set_start", InputConstants.KEY_V, KeyMapping.Category.MISC)
      );
      setEndKey = KeyBindingHelper.registerKeyBinding(
         new KeyMapping("key.meshify.set_end", InputConstants.KEY_B, KeyMapping.Category.MISC)
      );

      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         while (setStartKey.consumeClick()) {
            setCorner(client, Corner.START);
         }

         while (setEndKey.consumeClick()) {
            setCorner(client, Corner.END);
         }
      });

      //? if >=26.2 {
      /*LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
         if (inSelectionDimension()) {
            Vec3 camera = context.levelState().cameraRenderState.pos;
            PoseStack poseStack = context.poseStack();
            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            for (Outline outline : outlines()) {
               context.submitNodeCollector().submitShapeOutline(poseStack, Shapes.create(outline.box()), RenderTypes.lines(), outline.color(), outline.width(), false);
            }
            poseStack.popPose();
         }
      });
      *///?} elif >=26.1 {
      /*LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> {
         if (inSelectionDimension()) {
            Vec3 camera = context.levelState().cameraRenderState.pos;
            VertexConsumer lineBuffer = context.bufferSource().getBuffer(RenderTypes.lines());
            for (Outline outline : outlines()) {
               ShapeRenderer.renderShape(context.poseStack(), lineBuffer, Shapes.create(outline.box()), -camera.x, -camera.y, -camera.z, outline.color(), outline.width());
            }
         }
      });
      *///?} elif >=1.21.11 {
      // Not BEFORE_DEBUG_RENDER: Fabric never prepares that hook's context, so its matrices are null.
      WorldRenderEvents.AFTER_ENTITIES.register(context -> {
         if (context.matrices() != null && context.consumers() != null && inSelectionDimension()) {
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            VertexConsumer lineBuffer = context.consumers().getBuffer(RenderTypes.lines());
            for (Outline outline : outlines()) {
               ShapeRenderer.renderShape(
                  context.matrices(), lineBuffer, Shapes.create(outline.box()), -camera.x, -camera.y, -camera.z, outline.color(), outline.width()
               );
            }
         }
      });
      //?} else {
      /*// Lines have one fixed width before 1.21.11.
      WorldRenderEvents.AFTER_ENTITIES.register(context -> {
         if (context.matrices() != null && context.consumers() != null && inSelectionDimension()) {
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            VertexConsumer lineBuffer = context.consumers().getBuffer(RenderType.lines());
            for (Outline outline : outlines()) {
               ShapeRenderer.renderShape(context.matrices(), lineBuffer, Shapes.create(outline.box()), -camera.x, -camera.y, -camera.z, outline.color());
            }
         }
      });
      *///?}
   }

   private static boolean inSelectionDimension() {
      Minecraft client = Minecraft.getInstance();
      return client.level != null && Selection.matchesDimension(client.level.dimension());
   }

   private static List<Outline> outlines() {
      List<Outline> outlines = new ArrayList<>();
      if (Selection.isComplete()) {
         outlines.add(new Outline(AABB.encapsulatingFullBlocks(Selection.min(), Selection.max()), REGION_COLOR, REGION_LINE_WIDTH));
      }

      if (Selection.start != null) {
         outlines.add(new Outline(new AABB(Selection.start).inflate(CORNER_NUDGE), START_COLOR, CORNER_LINE_WIDTH));
      }

      if (Selection.end != null) {
         outlines.add(new Outline(new AABB(Selection.end).inflate(CORNER_NUDGE), END_COLOR, CORNER_LINE_WIDTH));
      }

      return outlines;
   }

   // The keys report on the action bar, which stays out of the chat while the player sets corners by eye.
   private static void setCorner(Minecraft client, Corner corner) {
      if (client.player != null && client.level != null) {
         Consumer<Component> actionBar = text -> client.player.displayClientMessage(text, true);
         MeshifyActions.setCorner(new Feedback(actionBar, actionBar), corner, MeshifyActions.targetedBlock(client));
      }
   }

   private record Outline(AABB box, int color, float width) {
   }
}
