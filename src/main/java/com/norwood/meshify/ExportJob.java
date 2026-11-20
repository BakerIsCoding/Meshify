package com.norwood.meshify;

import com.norwood.meshify.export.BakeCollector;
import com.norwood.meshify.export.ExportOptions;
import com.norwood.meshify.export.ExportStage;
import com.norwood.meshify.export.MeshBuilder;
import com.norwood.meshify.export.RegionExporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;

// The running export: scans a slice per client tick, then writes the files on a background thread.
final class ExportJob {
   // About half of a 50 ms tick; raise it for faster exports at a lower frame rate.
   private static final long SCAN_BUDGET_NANOS = 25_000_000L;
   // Below this much of the scan, the time left swings too much to be worth showing.
   private static final double MIN_PROGRESS_FOR_ETA = 0.02;
   private static final SystemToast.SystemToastId TOAST_TYPE = new SystemToast.SystemToastId();

   private static volatile ExportJob current;

   private final Minecraft client;
   private final RegionExporter.Scanner scanner;
   private final ExportOptions options;
   private final long startNanos = System.nanoTime();
   private volatile boolean cancelled;
   private volatile ExportStage stage;
   // Client-thread only: set once the scan has handed over to the writer thread.
   private boolean writing;

   private ExportJob(Minecraft client, RegionExporter.Scanner scanner, ExportOptions options) {
      this.client = client;
      this.scanner = scanner;
      this.options = options;
   }

   static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         ExportJob job = current;
         if (job != null) {
            job.tick();
         }
      });
   }

   static boolean isRunning() {
      return current != null;
   }

   static void start(BlockPos firstCorner, BlockPos secondCorner, ExportOptions options) {
      current = new ExportJob(Minecraft.getInstance(), new RegionExporter.Scanner(firstCorner, secondCorner, options), options);
   }

   static boolean cancel() {
      ExportJob job = current;
      if (job == null) {
         return false;
      }

      job.cancelled = true;
      return true;
   }

   static Component folderLink(Path dir) {
      Path absolute = dir.toAbsolutePath().normalize();
      return Component.literal(absolute.toString())
         .withStyle(
            style -> style.withColor(ChatFormatting.GREEN)
               .withUnderlined(true)
               .withClickEvent(new ClickEvent.OpenFile(absolute))
               .withHoverEvent(new HoverEvent.ShowText(Component.translatable("meshify.msg.saved_hover")))
         );
   }

   private void tick() {
      if (this.writing) {
         this.showProgress();
      } else if (this.client.level != this.scanner.world()) {
         this.endScan(Component.translatable("meshify.error.world_changed").withStyle(ChatFormatting.RED));
      } else if (this.cancelled) {
         this.endScan(Component.translatable("meshify.msg.cancelled"));
      } else {
         this.scanSlice();
      }
   }

   private void scanSlice() {
      try {
         if (!this.scanner.step(System.nanoTime() + SCAN_BUDGET_NANOS)) {
            this.showProgress();
            return;
         }

         RegionExporter.Scan scan = this.scanner.complete();
         this.writing = true;
         Thread.ofVirtual().name("meshify-export").start(() -> this.writeFiles(scan));
      } catch (Exception exception) {
         this.endScan(failureMessage(exception));
         this.showToast("meshify.toast.failed");
      }
   }

   private void endScan(Component message) {
      this.scanner.discard();
      current = null;
      this.clearOverlay();
      this.sendChatMessage(message);
   }

   private void writeFiles(RegionExporter.Scan scan) {
      try {
         this.report(RegionExporter.finish(this.client, scan, this::enterStage));
      } catch (CancellationException cancellation) {
         deleteQuietly(RegionExporter.outputDir(this.client, this.options.name()));
         this.sendChatMessage(Component.translatable("meshify.msg.cancelled"));
      } catch (Exception exception) {
         this.sendChatMessage(failureMessage(exception));
         this.client.execute(() -> this.showToast("meshify.toast.failed"));
      } finally {
         current = null;
         this.client.execute(this::clearOverlay);
      }
   }

   private void clearOverlay() {
      this.client.gui.setOverlayMessage(Component.empty(), false);
   }

   // Runs on the writer thread before each step, so cancelling throws here.
   private void enterStage(ExportStage next) {
      if (this.cancelled) {
         throw new CancellationException();
      }

      this.stage = next;
   }

   static Component currentProgress() {
      ExportJob job = current;
      return job == null ? null : job.progressText();
   }

   private void showProgress() {
      Component text = this.progressText();
      if (text != null) {
         this.client.gui.setOverlayMessage(text, false);
      }
   }

   // Null only in the moment between the scan ending and the writer thread reaching its first step.
   private Component progressText() {
      if (!this.writing) {
         double progress = this.scanner.progress();
         int percent = (int)(progress * 100.0);
         return progress < MIN_PROGRESS_FOR_ETA
            ? Component.translatable("meshify.progress.scan", percent)
            : Component.translatable("meshify.progress.scan_eta", percent, Math.round(this.elapsedSeconds() * (1.0 - progress) / progress));
      }

      ExportStage currentStage = this.stage;
      return currentStage == null
         ? null
         : Component.translatable("meshify.progress.write", Component.translatable(currentStage.translationKey()), Math.round(this.elapsedSeconds()));
   }

   private double elapsedSeconds() {
      return (System.nanoTime() - this.startNanos) / 1.0E9;
   }

   private void report(RegionExporter.Result result) {
      this.sendChatMessage(
         Component.translatable(
            "meshify.msg.done",
            oneDecimal(this.elapsedSeconds()),
            result.blocksExported(),
            result.entitiesExported(),
            result.facesEmitted()
         )
      );
      BakeCollector.Stats bakeStats = result.bakeStats();
      if (bakeStats != null) {
         this.sendChatMessage(
            Component.translatable(
                  "meshify.msg.bake_summary",
                  bakeStats.facesConsumed(),
                  bakeStats.quadsEmitted(),
                  oneDecimal(bakeStats.reductionPercent()),
                  bakeStats.sheets(),
                  oneDecimal(bakeStats.megapixels())
               )
               .withStyle(ChatFormatting.AQUA)
         );
      }

      MeshBuilder.OptimizationStats optimizationStats = result.optimizationStats();
      if (optimizationStats != null) {
         this.sendChatMessage(
            Component.translatable(
                  "meshify.msg.weld_summary",
                  result.facesBeforeOptimize(),
                  result.facesEmitted(),
                  optimizationStats.degenerateRemoved(),
                  optimizationStats.duplicateRemoved(),
                  optimizationStats.weldGrid()
               )
               .withStyle(ChatFormatting.AQUA)
         );
      }

      if (!result.skippedTypes().isEmpty()) {
         this.sendChatMessage(Component.translatable("meshify.msg.skipped", String.join(", ", result.skippedTypes())).withStyle(ChatFormatting.YELLOW));
      }

      for (String warning : result.warnings()) {
         this.sendChatMessage(Component.translatable("meshify.msg.warning", warning).withStyle(ChatFormatting.YELLOW));
      }

      // Last, so the link is the line right above the chat input when the export finishes.
      this.sendChatMessage(Component.translatable("meshify.msg.saved", folderLink(result.outputDir())));
      this.client.execute(() -> {
         this.showToast("meshify.toast.done");
         this.client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
      });
   }

   private void showToast(String titleKey) {
      SystemToast.addOrUpdate(this.client.getToastManager(), TOAST_TYPE, Component.translatable(titleKey), Component.literal(this.options.name()));
   }

   private void sendChatMessage(Component text) {
      this.client.execute(() -> {
         if (this.client.player != null) {
            this.client.player.displayClientMessage(text, false);
         }
      });
   }

   private static String oneDecimal(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }

   private static Component failureMessage(Exception exception) {
      return Component.translatable("meshify.error.failed", String.valueOf(exception)).withStyle(ChatFormatting.RED);
   }

   // Deleting the whole folder is safe: MeshifyActions.uniqueName never reuses one.
   private static void deleteQuietly(Path dir) {
      if (!Files.exists(dir)) {
         return;
      }

      try (Stream<Path> paths = Files.walk(dir)) {
         paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
      } catch (IOException ignored) {
         // Best effort: a half-deleted folder of a cancelled export harms nothing.
      }
   }
}
