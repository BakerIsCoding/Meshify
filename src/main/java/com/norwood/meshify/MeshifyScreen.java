package com.norwood.meshify;

import com.norwood.meshify.MeshifyActions.Feedback;
import com.norwood.meshify.Selection.Corner;
import com.norwood.meshify.export.ExportOptions;
import java.util.ArrayList;
import java.util.List;
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
   private static final int HALF_WIDTH = (COLUMN_WIDTH - 4) / 2;
   private static final int BUTTON_HEIGHT = 20;
   private static final int ROW = 24;
   private static final int LINE = 10;
   private static final int COLUMNS_TOP = 28;
   /** Where the selection buttons start, under the four lines describing the selection. */
   private static final int SELECTION_BUTTONS_TOP = COLUMNS_TOP + 4 * LINE + 4;
   private static final int STATUS_TOP = 194;
   private static final int STATUS_LINES = 2;
   /** Title, both columns, the status lines and the bottom buttons; fits a 240-pixel-tall GUI. */
   private static final int CONTENT_HEIGHT = 236;
   private static final int PANEL_PADDING = 6;
   private static final int PANEL_COLOR = 0x90000000;
   private static final int TEXT_COLOR = 0xFFFFFFFF;
   private static final String MESSAGE_PREFIX = "[Meshify] ";
   private static final List<Integer> TEXTURE_SCALES = List.of(1, 2, 4, 8);
   private static final List<Integer> BAKE_UV_SIZES = List.of(8, 16, 32, 64, 128);
   private static final List<Integer> BAKE_MAX_SIZES = List.of(1024, 2048, 4096, 8192);

   private static KeyBinding openKey;

   // The choices live on the screen, not in the widgets: resizing the window rebuilds every widget.
   private String name = "";
   private String amount = "1";
   private String side = MeshifyActions.ALL_SIDES;
   private boolean atlas;
   private int texScale;
   private boolean bake;
   private int bakeUv;
   private int bakeMax;
   private boolean weld;
   private boolean cull;
   private Text status = Text.empty();
   private final Feedback statusFeedback = new Feedback(this::showInfo, this::showError);

   private int top;
   private int left;
   private int right;
   private CyclingButtonWidget<Integer> bakeUvButton;
   private CyclingButtonWidget<Integer> bakeMaxButton;
   private ButtonWidget exportButton;

   MeshifyScreen() {
      super(Text.translatable("meshify.gui.title"));
      // Starts from the last export's options, the same ones a bare /meshify export would reuse.
      ExportOptions last = MeshifyActions.parseOptions("", Selection.lastOptions, Feedback.IGNORE);
      this.atlas = last.mergeAtlas();
      this.texScale = last.texScale();
      this.bake = last.bake();
      this.bakeUv = last.bakeUvSize();
      this.bakeMax = last.bakeMaxAtlas();
      this.weld = last.weldVertices();
      this.cull = last.aggressiveCull();
   }

   static void register() {
      openKey = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.meshify.open_menu", InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_N, KeyBinding.Category.MISC)
      );
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         while (openKey.wasPressed()) {
            if (client.player != null && client.currentScreen == null) {
               client.setScreen(new MeshifyScreen());
            }
         }
      });
   }

   @Override
   protected void init() {
      this.top = Math.max(4, (this.height - CONTENT_HEIGHT) / 2);
      this.left = this.width / 2 - COLUMN_WIDTH - COLUMN_GAP / 2;
      this.right = this.width / 2 + COLUMN_GAP / 2;
      this.initSelectionColumn(this.top + SELECTION_BUTTONS_TOP);
      this.initExportColumn(this.top + COLUMNS_TOP);

      int bottomY = this.top + CONTENT_HEIGHT - BUTTON_HEIGHT;
      this.addDrawableChild(
         button("meshify.gui.open_folder", () -> MeshifyActions.openFolder(this.statusFeedback))
            .dimensions(this.width / 2 - HALF_WIDTH - 2, bottomY, HALF_WIDTH, BUTTON_HEIGHT)
            .build()
      );
      this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close()).dimensions(this.width / 2 + 2, bottomY, HALF_WIDTH, BUTTON_HEIGHT).build());
   }

   private void initSelectionColumn(int y) {
      Tooltip cornerTooltip = Tooltip.of(Text.translatable("meshify.gui.set_corner.tooltip"));
      this.addDrawableChild(button("meshify.gui.set_start", () -> this.setCorner(Corner.START)).dimensions(this.left, y, HALF_WIDTH, BUTTON_HEIGHT).tooltip(cornerTooltip).build());
      this.addDrawableChild(
         button("meshify.gui.set_end", () -> this.setCorner(Corner.END)).dimensions(this.left + HALF_WIDTH + 4, y, HALF_WIDTH, BUTTON_HEIGHT).tooltip(cornerTooltip).build()
      );

      y += ROW;
      TextFieldWidget amountField = new TextFieldWidget(this.textRenderer, this.left, y, 40, BUTTON_HEIGHT, Text.translatable("meshify.gui.amount"));
      amountField.setMaxLength(4);
      amountField.setTextPredicate(text -> text.chars().allMatch(Character::isDigit));
      amountField.setText(this.amount);
      amountField.setChangedListener(text -> this.amount = text);
      amountField.setTooltip(Tooltip.of(Text.translatable("meshify.gui.amount")));
      this.addDrawableChild(amountField);
      this.addDrawableChild(
         CyclingButtonWidget.<String>builder(value -> Text.translatable("meshify.gui.side." + value), this.side)
            .values(MeshifyActions.SIDES)
            .build(this.left + 44, y, COLUMN_WIDTH - 44, BUTTON_HEIGHT, Text.translatable("meshify.gui.side"), (button, value) -> this.side = value)
      );

      y += ROW;
      this.addDrawableChild(button("meshify.gui.expand", () -> this.resize(1)).dimensions(this.left, y, HALF_WIDTH, BUTTON_HEIGHT).build());
      this.addDrawableChild(button("meshify.gui.shrink", () -> this.resize(-1)).dimensions(this.left + HALF_WIDTH + 4, y, HALF_WIDTH, BUTTON_HEIGHT).build());

      y += ROW;
      this.addDrawableChild(
         button("meshify.gui.full_height", () -> MeshifyActions.expandVertical(this.statusFeedback))
            .dimensions(this.left, y, HALF_WIDTH, BUTTON_HEIGHT)
            .tooltip(Tooltip.of(Text.translatable("meshify.gui.full_height.tooltip")))
            .build()
      );
      this.addDrawableChild(
         button("meshify.gui.trim", () -> MeshifyActions.trim(this.statusFeedback))
            .dimensions(this.left + HALF_WIDTH + 4, y, HALF_WIDTH, BUTTON_HEIGHT)
            .tooltip(Tooltip.of(Text.translatable("meshify.gui.trim.tooltip")))
            .build()
      );

      y += ROW;
      this.addDrawableChild(button("meshify.gui.clear", () -> MeshifyActions.clear(this.statusFeedback)).dimensions(this.left, y, COLUMN_WIDTH, BUTTON_HEIGHT).build());
   }

   private void initExportColumn(int y) {
      TextFieldWidget nameField = new TextFieldWidget(this.textRenderer, this.right, y, COLUMN_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.name"));
      nameField.setMaxLength(64);
      // The export folder is named after it, so only characters every file system accepts.
      nameField.setTextPredicate(text -> text.matches("[A-Za-z0-9_\\-]*"));
      nameField.setText(this.name);
      nameField.setPlaceholder(Text.translatable("meshify.gui.name.placeholder").formatted(Formatting.GRAY, Formatting.ITALIC));
      nameField.setChangedListener(text -> this.name = text);
      this.addDrawableChild(nameField);

      y += ROW;
      this.addDrawableChild(
         CyclingButtonWidget.<Boolean>builder(single -> Text.translatable(single ? "meshify.opt.atlas" : "meshify.opt.separate"), this.atlas)
            .values(true, false)
            .tooltip(single -> Tooltip.of(Text.translatable(single ? "meshify.opt.desc.atlas" : "meshify.opt.desc.separate")))
            .build(this.right, y, COLUMN_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.textures"), (button, value) -> this.atlas = value)
      );

      y += ROW;
      this.addDrawableChild(
         CyclingButtonWidget.<Integer>builder(scale -> Text.literal("x" + scale), this.texScale)
            .values(TEXTURE_SCALES)
            .tooltip(scale -> Tooltip.of(Text.translatable("meshify.opt.desc.scale", scale)))
            .build(this.right, y, HALF_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.scale"), (button, value) -> this.texScale = value)
      );
      this.addDrawableChild(
         CyclingButtonWidget.onOffBuilder(this.weld)
            .tooltip(on -> Tooltip.of(Text.translatable("meshify.opt.desc.weld")))
            .build(this.right + HALF_WIDTH + 4, y, HALF_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.weld"), (button, value) -> this.weld = value)
      );

      y += ROW;
      this.addDrawableChild(
         CyclingButtonWidget.onOffBuilder(this.bake)
            .tooltip(on -> Tooltip.of(Text.translatable("meshify.opt.desc.bake")))
            .build(this.right, y, HALF_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.bake"), (button, value) -> {
               this.bake = value;
               this.updateBakeButtons();
            })
      );
      this.addDrawableChild(
         CyclingButtonWidget.onOffBuilder(this.cull)
            .tooltip(on -> Tooltip.of(Text.translatable("meshify.opt.desc.cull")))
            .build(this.right + HALF_WIDTH + 4, y, HALF_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.cull"), (button, value) -> this.cull = value)
      );

      y += ROW;
      this.bakeUvButton = this.addDrawableChild(
         CyclingButtonWidget.<Integer>builder(size -> Text.literal(size + " px"), this.bakeUv)
            .values(BAKE_UV_SIZES)
            .tooltip(size -> Tooltip.of(Text.translatable("meshify.opt.desc.uv", size)))
            .build(this.right, y, HALF_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.bake_uv"), (button, value) -> this.bakeUv = value)
      );
      this.bakeMaxButton = this.addDrawableChild(
         CyclingButtonWidget.<Integer>builder(size -> Text.literal(size + " px"), this.bakeMax)
            .values(BAKE_MAX_SIZES)
            .tooltip(size -> Tooltip.of(Text.translatable("meshify.opt.desc.max", size)))
            .build(this.right + HALF_WIDTH + 4, y, HALF_WIDTH, BUTTON_HEIGHT, Text.translatable("meshify.gui.bake_max"), (button, value) -> this.bakeMax = value)
      );
      this.updateBakeButtons();

      y += ROW;
      this.exportButton = this.addDrawableChild(button("meshify.gui.export_button", this::exportOrCancel).dimensions(this.right, y, COLUMN_WIDTH, BUTTON_HEIGHT).build());
      this.tick();
   }

   private static ButtonWidget.Builder button(String translationKey, Runnable action) {
      return ButtonWidget.builder(Text.translatable(translationKey), button -> action.run());
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
      ExportOptions options = new ExportOptions("", this.atlas, this.texScale, this.weld, this.cull, this.bake, this.bakeUv, this.bakeMax);
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
         this.left - PANEL_PADDING,
         this.top - PANEL_PADDING,
         this.right + COLUMN_WIDTH + PANEL_PADDING,
         this.top + CONTENT_HEIGHT + PANEL_PADDING,
         PANEL_COLOR
      );
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      super.render(context, mouseX, mouseY, delta);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.top, TEXT_COLOR);
      context.drawTextWithShadow(this.textRenderer, Text.translatable("meshify.gui.selection").formatted(Formatting.YELLOW), this.left, this.top + 14, TEXT_COLOR);
      context.drawTextWithShadow(this.textRenderer, Text.translatable("meshify.gui.export").formatted(Formatting.YELLOW), this.right, this.top + 14, TEXT_COLOR);

      int y = this.top + COLUMNS_TOP;
      for (Text line : this.selectionLines()) {
         context.drawTextWithShadow(this.textRenderer, line, this.left, y, TEXT_COLOR);
         y += LINE;
      }

      // A running export's progress outranks the result of the last button press.
      Text progress = ExportJob.currentProgress();
      List<OrderedText> statusLines = this.textRenderer.wrapLines(progress != null ? progress : this.status, 2 * COLUMN_WIDTH + COLUMN_GAP);
      y = this.top + STATUS_TOP;
      for (OrderedText line : statusLines.subList(0, Math.min(STATUS_LINES, statusLines.size()))) {
         context.drawCenteredTextWithShadow(this.textRenderer, line, this.width / 2, y, TEXT_COLOR);
         y += LINE;
      }
   }

   private List<Text> selectionLines() {
      Text unset = Text.translatable("meshify.label.unset");
      List<Text> lines = new ArrayList<>();
      lines.add(Text.translatable("meshify.gui.start", Selection.start == null ? unset : Selection.start.toShortString()));
      lines.add(Text.translatable("meshify.gui.end", Selection.end == null ? unset : Selection.end.toShortString()));
      if (Selection.isComplete()) {
         lines.add(Text.translatable("meshify.gui.size", Selection.dimensions(), Selection.volume()));
      }

      lines.add(Text.translatable("meshify.gui.target", MeshifyActions.targetedBlock(this.client).toShortString()).formatted(Formatting.GRAY));
      return lines;
   }
}
