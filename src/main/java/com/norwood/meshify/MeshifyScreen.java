package com.norwood.meshify;

import com.norwood.meshify.MeshifyActions.Feedback;
import com.norwood.meshify.Selection.Corner;
import com.norwood.meshify.export.ExportOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

final class MeshifyScreen extends Screen {
   private static final int COLUMN_WIDTH = 200;
   private static final int COLUMN_GAP = 12;
   private static final int BUTTON_GAP = 4;
   private static final int HALF_BUTTON_WIDTH = (COLUMN_WIDTH - BUTTON_GAP) / 2;
   /** Where the second of two half-width buttons starts, from its column's left edge. */
   private static final int SECOND_HALF_OFFSET = HALF_BUTTON_WIDTH + BUTTON_GAP;
   private static final int BUTTON_HEIGHT = 20;
   private static final int AMOUNT_FIELD_WIDTH = 40;
   private static final int ROW_HEIGHT = 24;
   private static final int LINE_HEIGHT = 10;
   private static final int HEADINGS_TOP = 14;
   private static final int COLUMNS_TOP = 28;
   /** Where the selection buttons start, under the four lines describing the selection. */
   private static final int SELECTION_BUTTONS_TOP = COLUMNS_TOP + 4 * LINE_HEIGHT + 4;
   private static final int STATUS_TOP = 194;
   private static final int STATUS_LINES = 2;
   /** Title, both columns, the status lines and the bottom buttons; fits a 240-pixel-tall GUI. */
   private static final int CONTENT_HEIGHT = 236;
   private static final int PANEL_PADDING = 6;
   private static final int PANEL_COLOR = 0x90000000;
   private static final int TEXT_COLOR = 0xFFFFFFFF;
   private static final String MESSAGE_PREFIX = "[Meshify] ";

   private static KeyBinding openMenuKey;

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
   private Text status = Text.empty();
   private final Feedback statusFeedback = new Feedback(this::showInfo, this::showError);

   private int panelTop;
   private int leftColumnX;
   private int rightColumnX;
   private CyclingButtonWidget<Integer> bakeUvButton;
   private CyclingButtonWidget<Integer> bakeMaxButton;
   private ButtonWidget exportButton;

   MeshifyScreen() {
      super(Text.translatable("meshify.gui.title"));
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
         new KeyBinding("key.meshify.open_menu", InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_N, KeyBinding.Category.MISC)
      );
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         while (openMenuKey.wasPressed()) {
            if (client.player != null && client.currentScreen == null) {
               client.setScreen(new MeshifyScreen());
            }
         }
      });
   }

   @Override
   protected void init() {
      this.panelTop = Math.max(4, (this.height - CONTENT_HEIGHT) / 2);
      this.leftColumnX = this.width / 2 - COLUMN_WIDTH - COLUMN_GAP / 2;
      this.rightColumnX = this.width / 2 + COLUMN_GAP / 2;
      this.initSelectionColumn(this.panelTop + SELECTION_BUTTONS_TOP);
      this.initExportColumn(this.panelTop + COLUMNS_TOP);

      int bottomY = this.panelTop + CONTENT_HEIGHT - BUTTON_HEIGHT;
      this.addDrawableChild(
         button("meshify.gui.open_folder", () -> MeshifyActions.openFolder(this.statusFeedback))
            .dimensions(this.width / 2 - HALF_BUTTON_WIDTH - BUTTON_GAP / 2, bottomY, HALF_BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()
      );
      this.addDrawableChild(
         ButtonWidget.builder(ScreenTexts.DONE, button -> this.close()).dimensions(this.width / 2 + BUTTON_GAP / 2, bottomY, HALF_BUTTON_WIDTH, BUTTON_HEIGHT).build()
      );
   }

   private void initSelectionColumn(int y) {
      Tooltip cornerTooltip = Tooltip.of(Text.translatable("meshify.gui.set_corner.tooltip"));
      this.addDrawableChild(button("meshify.gui.set_start", () -> this.setCorner(Corner.START)).dimensions(this.leftColumnX, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT).tooltip(cornerTooltip).build());
      this.addDrawableChild(
         button("meshify.gui.set_end", () -> this.setCorner(Corner.END)).dimensions(this.leftColumnX + SECOND_HALF_OFFSET, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT).tooltip(cornerTooltip).build()
      );

      y += ROW_HEIGHT;
      TextFieldWidget amountField = new TextFieldWidget(this.textRenderer, this.leftColumnX, y, AMOUNT_FIELD_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.amount"));
      amountField.setMaxLength(4);
      amountField.setTextPredicate(text -> text.chars().allMatch(Character::isDigit));
      amountField.setText(this.amount);
      amountField.setChangedListener(text -> this.amount = text);
      amountField.setTooltip(Tooltip.of(Text.translatable("meshify.gui.amount")));
      this.addDrawableChild(amountField);
      this.addDrawableChild(
         CyclingButtonWidget.<String>builder(value -> Text.translatable("meshify.gui.side." + value), this.side)
            .values(MeshifyActions.SIDES)
            .build(
               this.leftColumnX + AMOUNT_FIELD_WIDTH + BUTTON_GAP,
               y,
               COLUMN_WIDTH - AMOUNT_FIELD_WIDTH - BUTTON_GAP,
               BUTTON_HEIGHT,
               Text.translatable("meshify.gui.side"),
               (button, value) -> this.side = value
            )
      );

      y += ROW_HEIGHT;
      this.addDrawableChild(button("meshify.gui.expand", () -> this.resize(1)).dimensions(this.leftColumnX, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT).build());
      this.addDrawableChild(button("meshify.gui.shrink", () -> this.resize(-1)).dimensions(this.leftColumnX + SECOND_HALF_OFFSET, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT).build());

      y += ROW_HEIGHT;
      this.addDrawableChild(
         button("meshify.gui.full_height", () -> MeshifyActions.expandVertical(this.statusFeedback))
            .dimensions(this.leftColumnX, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT)
            .tooltip(Tooltip.of(Text.translatable("meshify.gui.full_height.tooltip")))
            .build()
      );
      this.addDrawableChild(
         button("meshify.gui.trim", () -> MeshifyActions.trim(this.statusFeedback))
            .dimensions(this.leftColumnX + SECOND_HALF_OFFSET, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT)
            .tooltip(Tooltip.of(Text.translatable("meshify.gui.trim.tooltip")))
            .build()
      );

      y += ROW_HEIGHT;
      this.addDrawableChild(button("meshify.gui.clear", () -> MeshifyActions.clear(this.statusFeedback)).dimensions(this.leftColumnX, y, COLUMN_WIDTH, BUTTON_HEIGHT).build());
   }

   private void initExportColumn(int y) {
      TextFieldWidget nameField = new TextFieldWidget(this.textRenderer, this.rightColumnX, y, COLUMN_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.name"));
      nameField.setMaxLength(64);
      nameField.setTextPredicate(text -> MeshifyActions.sanitizeName(text).equals(text));
      nameField.setText(this.name);
      nameField.setPlaceholder(Text.translatable("meshify.gui.name.placeholder").formatted(Formatting.GRAY, Formatting.ITALIC));
      nameField.setChangedListener(text -> this.name = text);
      this.addDrawableChild(nameField);

      y += ROW_HEIGHT;
      this.addDrawableChild(
         CyclingButtonWidget.<Boolean>builder(merged -> Text.translatable(merged ? "meshify.opt.atlas" : "meshify.opt.separate"), this.mergeAtlas)
            .values(true, false)
            .tooltip(merged -> Tooltip.of(Text.translatable(merged ? "meshify.opt.desc.atlas" : "meshify.opt.desc.separate")))
            .build(this.rightColumnX, y, COLUMN_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.textures"), (button, value) -> this.mergeAtlas = value)
      );

      y += ROW_HEIGHT;
      this.addDrawableChild(
         CyclingButtonWidget.<Integer>builder(scale -> Text.literal("x" + scale), this.textureScale)
            .values(MeshifyActions.TEXTURE_SCALES)
            .tooltip(scale -> Tooltip.of(Text.translatable("meshify.opt.desc.scale", scale)))
            .build(this.rightColumnX, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.scale"), (button, value) -> this.textureScale = value)
      );
      this.addDrawableChild(this.toggle(this.rightColumnX + SECOND_HALF_OFFSET, y, "weld", this.weldVertices, value -> this.weldVertices = value));

      y += ROW_HEIGHT;
      this.addDrawableChild(this.toggle(this.rightColumnX, y, "bake", this.bake, value -> {
         this.bake = value;
         this.updateBakeButtons();
      }));
      this.addDrawableChild(this.toggle(this.rightColumnX + SECOND_HALF_OFFSET, y, "cull", this.aggressiveCull, value -> this.aggressiveCull = value));

      y += ROW_HEIGHT;
      this.bakeUvButton = this.addDrawableChild(
         CyclingButtonWidget.<Integer>builder(size -> Text.literal(size + " px"), this.bakeUvSize)
            .values(MeshifyActions.BAKE_UV_SIZES)
            .tooltip(size -> Tooltip.of(Text.translatable("meshify.opt.desc.uv", size)))
            .build(this.rightColumnX, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.bake_uv"), (button, value) -> this.bakeUvSize = value)
      );
      this.bakeMaxButton = this.addDrawableChild(
         CyclingButtonWidget.<Integer>builder(size -> Text.literal(size + " px"), this.bakeMaxAtlas)
            .values(MeshifyActions.BAKE_MAX_ATLAS_SIZES)
            .tooltip(size -> Tooltip.of(Text.translatable("meshify.opt.desc.max", size)))
            .build(this.rightColumnX + SECOND_HALF_OFFSET, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.bake_max"), (button, value) -> this.bakeMaxAtlas = value)
      );
      this.updateBakeButtons();

      y += ROW_HEIGHT;
      this.exportButton = this.addDrawableChild(button("meshify.gui.export_button", this::exportOrCancel).dimensions(this.rightColumnX, y, COLUMN_WIDTH, BUTTON_HEIGHT).build());
      this.tick();
   }

   private static ButtonWidget.Builder button(String translationKey, Runnable action) {
      return ButtonWidget.builder(Text.translatable(translationKey), button -> action.run());
   }

   /** A half-width on/off button for the export option {@code option}, labelled and explained by its translations. */
   private CyclingButtonWidget<Boolean> toggle(int x, int y, String option, boolean value, Consumer<Boolean> onChange) {
      return CyclingButtonWidget.onOffBuilder(value)
         .tooltip(on -> Tooltip.of(Text.translatable("meshify.opt.desc." + option)))
         .build(x, y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui." + option), (button, changed) -> onChange.accept(changed));
   }

   private void updateBakeButtons() {
      this.bakeUvButton.active = this.bake;
      this.bakeMaxButton.active = this.bake;
   }

   private void setCorner(Corner corner) {
      MeshifyActions.setCorner(this.statusFeedback, corner, MeshifyActions.targetedBlock(this.client));
   }

   private void resize(int sign) {
      int blocks = this.amount.isEmpty() ? 0 : Integer.parseInt(this.amount);
      if (blocks < 1) {
         this.showError(Text.translatable("meshify.gui.bad_amount"));
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
      Feedback chatOrStatus = new Feedback(text -> this.client.player.sendMessage(text, false), this::showError);
      ExportOptions options = new ExportOptions("", this.mergeAtlas, this.textureScale, this.weldVertices, this.aggressiveCull, this.bake, this.bakeUvSize, this.bakeMaxAtlas);
      if (MeshifyActions.export(chatOrStatus, this.name, MeshifyActions.formatOptions(options))) {
         this.close();
      }
   }

   private void showInfo(Text text) {
      this.status = withoutPrefix(text);
   }

   private void showError(Text text) {
      this.status = withoutPrefix(text).formatted(Formatting.RED);
   }

   /** The menu already says who sent it, so the chat prefix only costs width here. */
   private static MutableText withoutPrefix(Text text) {
      String message = text.getString();
      return Text.literal(message.startsWith(MESSAGE_PREFIX) ? message.substring(MESSAGE_PREFIX.length()) : message);
   }

   @Override
   public void tick() {
      this.exportButton.setMessage(Text.translatable(ExportJob.isRunning() ? "meshify.gui.cancel_button" : "meshify.gui.export_button"));
   }

   /** Only a dim panel behind the menu: no blur, so the selection outline stays sharp. */
   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
      context.fill(
         this.leftColumnX - PANEL_PADDING,
         this.panelTop - PANEL_PADDING,
         this.rightColumnX + COLUMN_WIDTH + PANEL_PADDING,
         this.panelTop + CONTENT_HEIGHT + PANEL_PADDING,
         PANEL_COLOR
      );
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      super.render(context, mouseX, mouseY, delta);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.panelTop, TEXT_COLOR);
      context.drawTextWithShadow(this.textRenderer, Text.translatable("meshify.gui.selection").formatted(Formatting.YELLOW), this.leftColumnX, this.panelTop + HEADINGS_TOP, TEXT_COLOR);
      context.drawTextWithShadow(this.textRenderer, Text.translatable("meshify.gui.export").formatted(Formatting.YELLOW), this.rightColumnX, this.panelTop + HEADINGS_TOP, TEXT_COLOR);

      int y = this.panelTop + COLUMNS_TOP;
      for (Text line : this.selectionLines()) {
         context.drawTextWithShadow(this.textRenderer, line, this.leftColumnX, y, TEXT_COLOR);
         y += LINE_HEIGHT;
      }

      // A running export's progress outranks the result of the last button press.
      Text progress = ExportJob.currentProgress();
      List<OrderedText> statusLines = this.textRenderer.wrapLines(progress != null ? progress : this.status, 2 * COLUMN_WIDTH + COLUMN_GAP);
      y = this.panelTop + STATUS_TOP;
      for (OrderedText line : statusLines.subList(0, Math.min(STATUS_LINES, statusLines.size()))) {
         context.drawCenteredTextWithShadow(this.textRenderer, line, this.width / 2, y, TEXT_COLOR);
         y += LINE_HEIGHT;
      }
   }

   private List<Text> selectionLines() {
      List<Text> lines = new ArrayList<>();
      lines.add(Text.translatable("meshify.gui.start", MeshifyActions.describeCorner(Selection.start)));
      lines.add(Text.translatable("meshify.gui.end", MeshifyActions.describeCorner(Selection.end)));
      if (Selection.isComplete()) {
         lines.add(Text.translatable("meshify.gui.size", Selection.describeSize(), Selection.volume()));
      }

      lines.add(Text.translatable("meshify.gui.target", MeshifyActions.targetedBlock(this.client).toShortString()).formatted(Formatting.GRAY));
      return lines;
   }
}
