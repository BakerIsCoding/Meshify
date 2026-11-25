package com.norwood.meshify;

import com.mojang.blaze3d.platform.InputConstants;
import com.norwood.meshify.MeshifyActions.Feedback;
import com.norwood.meshify.Selection.Corner;
import com.norwood.meshify.export.ExportOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

final class MeshifyScreen extends Screen {
   private static final int COLUMN_WIDTH = 200;
   private static final int COLUMN_GAP = 12;
   private static final int BUTTON_GAP = 4;
   private static final int HALF_BUTTON_WIDTH = (COLUMN_WIDTH - BUTTON_GAP) / 2;
   // Where the second of two half-width buttons starts, from its column's left edge.
   private static final int SECOND_HALF_OFFSET = HALF_BUTTON_WIDTH + BUTTON_GAP;
   private static final int BUTTON_HEIGHT = 20;
   private static final int AMOUNT_FIELD_WIDTH = 40;
   private static final int AMOUNT_MAX_DIGITS = 4;
   private static final int NAME_MAX_LENGTH = 64;
   private static final int ROW_HEIGHT = 24;
   private static final int LINE_HEIGHT = 10;
   private static final int HEADINGS_TOP = 14;
   private static final int COLUMNS_TOP = 28;
   // Where the selection buttons start, a gap under the four lines describing the selection.
   private static final int SELECTION_BUTTONS_TOP = COLUMNS_TOP + 4 * LINE_HEIGHT + BUTTON_GAP;
   private static final int STATUS_TOP = 194;
   private static final int STATUS_LINES = 2;
   // Title, both columns, the status lines and the bottom buttons; fits a 240-pixel-tall GUI.
   private static final int CONTENT_HEIGHT = 236;
   private static final int PANEL_PADDING = 6;
   // How far the title stays from the top edge when the window is too short to centre the menu.
   private static final int MIN_PANEL_TOP = 4;
   private static final int PANEL_COLOR = 0x90000000;
   private static final int TEXT_COLOR = 0xFFFFFFFF;
   private static final String MESSAGE_PREFIX = "[Meshify] ";

   private static KeyMapping openMenuKey;

   // The choices live on the screen, not in the widgets: resizing the window rebuilds every widget.
   private String name = "";
   private String amount = "1";
   private String side = MeshifyActions.ALL_SIDES;
   private boolean mergeAtlas;
   private int textureScale;
   private boolean bake;
   private int bakeUvSize;
   private int bakeMaxAtlas;
   private boolean weldVertices;
   private boolean aggressiveCull;
   private Component status = Component.empty();
   private final Feedback statusFeedback = new Feedback(this::showInfo, this::showError);

   private int panelTop;
   private int leftColumnX;
   private int rightColumnX;
   private CycleButton<Integer> bakeUvButton;
   private CycleButton<Integer> bakeMaxButton;
   private Button exportButton;

   MeshifyScreen() {
      super(Component.translatable("meshify.gui.title"));
      // Starts from the last export's options, the same ones a bare /meshify export would reuse.
      ExportOptions previousOptions = MeshifyActions.parseOptions("", Selection.lastOptions, Feedback.IGNORE);
      this.mergeAtlas = previousOptions.mergeAtlas();
      this.textureScale = previousOptions.textureScale();
      this.bake = previousOptions.bake();
      this.bakeUvSize = previousOptions.bakeUvSize();
      this.bakeMaxAtlas = previousOptions.bakeMaxAtlas();
      this.weldVertices = previousOptions.weldVertices();
      this.aggressiveCull = previousOptions.aggressiveCull();
   }

   static void register() {
      openMenuKey = KeyBindingHelper.registerKeyBinding(
         new KeyMapping("key.meshify.open_menu", InputConstants.KEY_N, KeyMapping.Category.MISC)
      );
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         while (openMenuKey.consumeClick()) {
            if (client.player != null && client.screen == null) {
               client.setScreen(new MeshifyScreen());
            }
         }
      });
   }

   @Override
   protected void init() {
      this.panelTop = Math.max(MIN_PANEL_TOP, (this.height - CONTENT_HEIGHT) / 2);
      this.leftColumnX = this.width / 2 - COLUMN_WIDTH - COLUMN_GAP / 2;
      this.rightColumnX = this.width / 2 + COLUMN_GAP / 2;
      this.initSelectionColumn(this.panelTop + SELECTION_BUTTONS_TOP);
      this.initExportColumn(this.panelTop + COLUMNS_TOP);

      int bottomY = this.panelTop + CONTENT_HEIGHT - BUTTON_HEIGHT;
      this.addRenderableWidget(
         button("meshify.gui.open_folder", () -> MeshifyActions.openFolder(this.statusFeedback))
            .bounds(this.width / 2 - HALF_BUTTON_WIDTH - BUTTON_GAP / 2, bottomY, HALF_BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).bounds(this.width / 2 + BUTTON_GAP / 2, bottomY, HALF_BUTTON_WIDTH, BUTTON_HEIGHT).build()
      );
   }

   private void initSelectionColumn(int y) {
      Tooltip cornerTooltip = Tooltip.create(Component.translatable("meshify.gui.set_corner.tooltip"));
      this.addRenderableWidget(button("meshify.gui.set_start", () -> this.setCorner(Corner.START)).bounds(this.leftColumnX, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT).tooltip(cornerTooltip).build());
      this.addRenderableWidget(
         button("meshify.gui.set_end", () -> this.setCorner(Corner.END)).bounds(this.leftColumnX + SECOND_HALF_OFFSET, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT).tooltip(cornerTooltip).build()
      );

      y += ROW_HEIGHT;
      EditBox amountField = this.filteredEditBox(
         this.leftColumnX, y, AMOUNT_FIELD_WIDTH, Component.translatable("meshify.gui.amount"), text -> text.chars().allMatch(Character::isDigit)
      );
      amountField.setMaxLength(AMOUNT_MAX_DIGITS);
      amountField.setValue(this.amount);
      amountField.setResponder(text -> this.amount = text);
      amountField.setTooltip(Tooltip.create(Component.translatable("meshify.gui.amount")));
      this.addRenderableWidget(amountField);
      this.addRenderableWidget(
         cycleButton(value -> Component.translatable("meshify.gui.side." + value), this.side)
            .withValues(MeshifyActions.SIDES)
            .create(
               this.leftColumnX + AMOUNT_FIELD_WIDTH + BUTTON_GAP,
               y,
               COLUMN_WIDTH - AMOUNT_FIELD_WIDTH - BUTTON_GAP,
               BUTTON_HEIGHT,
               Component.translatable("meshify.gui.side"),
               (button, value) -> this.side = value
            )
      );

      y += ROW_HEIGHT;
      this.addRenderableWidget(button("meshify.gui.expand", () -> this.resize(1)).bounds(this.leftColumnX, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT).build());
      this.addRenderableWidget(button("meshify.gui.shrink", () -> this.resize(-1)).bounds(this.leftColumnX + SECOND_HALF_OFFSET, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT).build());

      y += ROW_HEIGHT;
      this.addRenderableWidget(
         button("meshify.gui.full_height", () -> MeshifyActions.expandVertical(this.statusFeedback))
            .bounds(this.leftColumnX, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("meshify.gui.full_height.tooltip")))
            .build()
      );
      this.addRenderableWidget(
         button("meshify.gui.trim", () -> MeshifyActions.trim(this.statusFeedback))
            .bounds(this.leftColumnX + SECOND_HALF_OFFSET, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("meshify.gui.trim.tooltip")))
            .build()
      );

      y += ROW_HEIGHT;
      this.addRenderableWidget(button("meshify.gui.clear", () -> MeshifyActions.clear(this.statusFeedback)).bounds(this.leftColumnX, y, COLUMN_WIDTH, BUTTON_HEIGHT).build());
   }

   private void initExportColumn(int y) {
      EditBox nameField = this.filteredEditBox(
         this.rightColumnX, y, COLUMN_WIDTH, Component.translatable("meshify.gui.name"), text -> MeshifyActions.sanitizeName(text).equals(text)
      );
      nameField.setMaxLength(NAME_MAX_LENGTH);
      nameField.setValue(this.name);
      nameField.setHint(Component.translatable("meshify.gui.name.placeholder").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
      nameField.setResponder(text -> this.name = text);
      this.addRenderableWidget(nameField);

      y += ROW_HEIGHT;
      this.addRenderableWidget(
         cycleButton(merged -> Component.translatable(merged ? "meshify.opt.atlas" : "meshify.opt.separate"), this.mergeAtlas)
            .withValues(true, false)
            .withTooltip(merged -> Tooltip.create(Component.translatable(merged ? "meshify.opt.desc.atlas" : "meshify.opt.desc.separate")))
            .create(this.rightColumnX, y, COLUMN_WIDTH, BUTTON_HEIGHT, Component.translatable("meshify.gui.textures"), (button, value) -> this.mergeAtlas = value)
      );

      y += ROW_HEIGHT;
      this.addRenderableWidget(
         cycleButton(scale -> Component.literal("x" + scale), this.textureScale)
            .withValues(MeshifyActions.TEXTURE_SCALES)
            .withTooltip(scale -> Tooltip.create(Component.translatable("meshify.opt.desc.scale", scale)))
            .create(this.rightColumnX, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("meshify.gui.scale"), (button, value) -> this.textureScale = value)
      );
      this.addRenderableWidget(this.toggle(this.rightColumnX + SECOND_HALF_OFFSET, y, "weld", this.weldVertices, value -> this.weldVertices = value));

      y += ROW_HEIGHT;
      this.addRenderableWidget(this.toggle(this.rightColumnX, y, "bake", this.bake, value -> {
         this.bake = value;
         this.updateBakeButtons();
      }));
      this.addRenderableWidget(this.toggle(this.rightColumnX + SECOND_HALF_OFFSET, y, "cull", this.aggressiveCull, value -> this.aggressiveCull = value));

      y += ROW_HEIGHT;
      this.bakeUvButton = this.addRenderableWidget(
         cycleButton(size -> Component.literal(size + " px"), this.bakeUvSize)
            .withValues(MeshifyActions.BAKE_UV_SIZES)
            .withTooltip(size -> Tooltip.create(Component.translatable("meshify.opt.desc.uv", size)))
            .create(this.rightColumnX, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("meshify.gui.bake_uv"), (button, value) -> this.bakeUvSize = value)
      );
      this.bakeMaxButton = this.addRenderableWidget(
         cycleButton(size -> Component.literal(size + " px"), this.bakeMaxAtlas)
            .withValues(MeshifyActions.BAKE_MAX_ATLAS_SIZES)
            .withTooltip(size -> Tooltip.create(Component.translatable("meshify.opt.desc.max", size)))
            .create(this.rightColumnX + SECOND_HALF_OFFSET, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("meshify.gui.bake_max"), (button, value) -> this.bakeMaxAtlas = value)
      );
      this.updateBakeButtons();

      y += ROW_HEIGHT;
      this.exportButton = this.addRenderableWidget(button("meshify.gui.export_button", this::exportOrCancel).bounds(this.rightColumnX, y, COLUMN_WIDTH, BUTTON_HEIGHT).build());
      this.tick();
   }

   private static Button.Builder button(String translationKey, Runnable action) {
      return Button.builder(Component.translatable(translationKey), button -> action.run());
   }

   /**
    * An edit box that refuses typing or pasting {@code filter} rejects. EditBox lost setFilter in 26.1; checking in insertText
    * works everywhere, and deleting can never break these filters.
    */
   private EditBox filteredEditBox(int x, int y, int width, Component label, Predicate<String> filter) {
      return new EditBox(this.font, x, y, width, BUTTON_HEIGHT, label) {
         @Override
         public void insertText(String text) {
            String before = this.getValue();
            int cursor = this.getCursorPosition();
            super.insertText(text);
            if (!filter.test(this.getValue())) {
               this.setValue(before);
               this.setCursorPosition(cursor);
            }
         }
      };
   }

   // 1.21.11 takes the initial value in builder(); earlier versions set it afterwards.
   private static <T> CycleButton.Builder<T> cycleButton(Function<T, Component> label, T initial) {
      //? if >=1.21.11 {
      return CycleButton.builder(label, initial);
      //?} else
      /*return CycleButton.builder(label).withInitialValue(initial);*/
   }

   // A half-width on/off button for one export option, labelled and explained by its translations.
   private CycleButton<Boolean> toggle(int x, int y, String option, boolean value, Consumer<Boolean> onChange) {
      return CycleButton.onOffBuilder(value)
         .withTooltip(on -> Tooltip.create(Component.translatable("meshify.opt.desc." + option)))
         .create(x, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("meshify.gui." + option), (button, changed) -> onChange.accept(changed));
   }

   private void updateBakeButtons() {
      this.bakeUvButton.active = this.bake;
      this.bakeMaxButton.active = this.bake;
   }

   private void setCorner(Corner corner) {
      MeshifyActions.setCorner(this.statusFeedback, corner, MeshifyActions.targetedBlock(this.minecraft));
   }

   private void resize(int sign) {
      int blocks = this.amount.isEmpty() ? 0 : Integer.parseInt(this.amount);
      if (blocks < 1) {
         this.showError(Component.translatable("meshify.gui.bad_amount"));
         return;
      }

      MeshifyActions.resize(this.statusFeedback, sign * blocks, this.side);
   }

   private void exportOrCancel() {
      if (ExportJob.isRunning()) {
         MeshifyActions.cancel(this.statusFeedback);
         return;
      }

      // Messages go to the chat, which outlives the menu; a refusal stays in the menu instead.
      Feedback chatOrStatus = new Feedback(text -> this.minecraft.player.displayClientMessage(text, false), this::showError);
      ExportOptions options = new ExportOptions("", this.mergeAtlas, this.textureScale, this.weldVertices, this.aggressiveCull, this.bake, this.bakeUvSize, this.bakeMaxAtlas);
      if (MeshifyActions.export(chatOrStatus, this.name, MeshifyActions.formatOptions(options))) {
         this.onClose();
      }
   }

   private void showInfo(Component text) {
      this.status = withoutPrefix(text);
   }

   private void showError(Component text) {
      this.status = withoutPrefix(text).withStyle(ChatFormatting.RED);
   }

   // The menu already says who sent it, so the chat prefix only costs width here.
   private static MutableComponent withoutPrefix(Component text) {
      String message = text.getString();
      return Component.literal(message.startsWith(MESSAGE_PREFIX) ? message.substring(MESSAGE_PREFIX.length()) : message);
   }

   @Override
   public void tick() {
      this.exportButton.setMessage(Component.translatable(ExportJob.isRunning() ? "meshify.gui.cancel_button" : "meshify.gui.export_button"));
   }

   // Only a dim panel behind the menu: no blur, so the selection outline stays sharp.
   @Override
   //? if >=26.4 {
   /*public void extractBackground(GuiGraphicsExtractor context) {
   *///?} elif >=26.1 {
   /*public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
   *///?} else
   public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
      context.fill(
         this.leftColumnX - PANEL_PADDING,
         this.panelTop - PANEL_PADDING,
         this.rightColumnX + COLUMN_WIDTH + PANEL_PADDING,
         this.panelTop + CONTENT_HEIGHT + PANEL_PADDING,
         PANEL_COLOR
      );
   }

   @Override
   //? if >=26.4 {
   /*public void extractRenderState(GuiGraphicsExtractor context) {
      super.extractRenderState(context);
   *///?} elif >=26.1 {
   /*public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      super.extractRenderState(context, mouseX, mouseY, delta);
   *///?} else {
   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      super.render(context, mouseX, mouseY, delta);
   //?}
      context.drawCenteredString(this.font, this.title, this.width / 2, this.panelTop, TEXT_COLOR);
      context.drawString(this.font, Component.translatable("meshify.gui.selection").withStyle(ChatFormatting.YELLOW), this.leftColumnX, this.panelTop + HEADINGS_TOP, TEXT_COLOR);
      context.drawString(this.font, Component.translatable("meshify.gui.export").withStyle(ChatFormatting.YELLOW), this.rightColumnX, this.panelTop + HEADINGS_TOP, TEXT_COLOR);

      int y = this.panelTop + COLUMNS_TOP;
      for (Component line : this.selectionLines()) {
         context.drawString(this.font, line, this.leftColumnX, y, TEXT_COLOR);
         y += LINE_HEIGHT;
      }

      // A running export's progress outranks the result of the last button press.
      Component progress = ExportJob.currentProgress();
      List<FormattedCharSequence> statusLines = this.font.split(progress != null ? progress : this.status, 2 * COLUMN_WIDTH + COLUMN_GAP);
      y = this.panelTop + STATUS_TOP;
      for (FormattedCharSequence line : statusLines.subList(0, Math.min(STATUS_LINES, statusLines.size()))) {
         context.drawCenteredString(this.font, line, this.width / 2, y, TEXT_COLOR);
         y += LINE_HEIGHT;
      }
   }

   private List<Component> selectionLines() {
      List<Component> lines = new ArrayList<>();
      lines.add(Component.translatable("meshify.gui.start", MeshifyActions.describeCorner(Selection.start)));
      lines.add(Component.translatable("meshify.gui.end", MeshifyActions.describeCorner(Selection.end)));
      if (Selection.isComplete()) {
         lines.add(Component.translatable("meshify.gui.size", Selection.describeSize(), Selection.volume()));
      }

      lines.add(Component.translatable("meshify.gui.target", MeshifyActions.targetedBlock(this.minecraft).toShortString()).withStyle(ChatFormatting.GRAY));
      return lines;
   }
}
