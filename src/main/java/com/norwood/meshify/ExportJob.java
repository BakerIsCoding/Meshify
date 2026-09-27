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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/** The running export: scans a slice per client tick, then writes the files on a background thread. */
final class ExportJob {
   /** About half of a 50 ms tick; raise it for faster exports at a lower frame rate. */
   private static final long SCAN_BUDGET_NANOS = 25_000_000L;
   /** Below this much of the scan, the time left swings too much to be worth showing. */
   private static final double MIN_PROGRESS_FOR_ETA = 0.02;
   private static final SystemToast.Type TOAST = new SystemToast.Type();

   private static volatile ExportJob current;

   private final MinecraftClient client;
   private final RegionExporter.Scanner scanner;
   private final ExportOptions options;
   private final long startNanos = System.nanoTime();
   private volatile boolean cancelled;
   private volatile ExportStage stage;
   /** Client-thread only: set once the scan has handed over to the writer thread. */
   private boolean writing;

   private ExportJob(MinecraftClient client, RegionExporter.Scanner scanner, ExportOptions options) {
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

   static void start(BlockPos a, BlockPos b, ExportOptions options) {
      current = new ExportJob(MinecraftClient.getInstance(), new RegionExporter.Scanner(a, b, options), options);
   }

   static boolean cancel() {
      ExportJob job = current;
      if (job == null) {
         return false;
      }

      job.cancelled = true;
      return true;
   }

   static Text folderLink(Path dir) {
      Path absolute = dir.toAbsolutePath().normalize();
      return Text.literal(absolute.toString())
         .styled(
            style -> style.withColor(Formatting.GREEN)
               .withUnderline(true)
               .withClickEvent(new ClickEvent.OpenFile(absolute))
               .withHoverEvent(new HoverEvent.ShowText(Text.translatable("meshify.msg.saved_hover")))
         );
   }

   private void tick() {
      if (this.writing) {
         this.showProgress();
      } else if (this.client.world != this.scanner.world()) {
         this.endScan(Text.translatable("meshify.error.world_changed").formatted(Formatting.RED));
      } else if (this.cancelled) {
         this.endScan(Text.translatable("meshify.msg.cancelled"));
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
         Thread.ofVirtual().name("meshify-export").start(() -> this.write(scan));
      } catch (Exception exception) {
         this.endScan(failureMessage(exception));
         this.showToast("meshify.toast.failed");
      }
   }

   private void endScan(Text message) {
      this.scanner.discard();
      current = null;
      this.client.inGameHud.setOverlayMessage(Text.empty(), false);
      this.post(message);
   }

   private void write(RegionExporter.Scan scan) {
      try {
         this.report(RegionExporter.finish(this.client, scan, this::enterStage));
      } catch (CancellationException cancellation) {
         deleteQuietly(RegionExporter.outputDir(this.client, this.options.name()));
         this.post(Text.translatable("meshify.msg.cancelled"));
      } catch (Exception exception) {
         this.post(failureMessage(exception));
         this.client.execute(() -> this.showToast("meshify.toast.failed"));
      } finally {
         current = null;
         this.client.execute(() -> this.client.inGameHud.setOverlayMessage(Text.empty(), false));
      }
   }

   /** Runs on the writer thread before each step, so cancelling throws here. */
   private void enterStage(ExportStage next) {
      if (this.cancelled) {
         throw new CancellationException();
      }

      this.stage = next;
   }

   static Text currentProgress() {
      ExportJob job = current;
      return job == null ? null : job.progressText();
   }

   private void showProgress() {
      Text text = this.progressText();
      if (text != null) {
         this.client.inGameHud.setOverlayMessage(text, false);
      }
   }

   /** Null only in the moment between the scan ending and the writer thread reaching its first step. */
   private Text progressText() {
      if (!this.writing) {
         double progress = this.scanner.progress();
         int percent = (int)(progress * 100.0);
         return progress < MIN_PROGRESS_FOR_ETA
            ? Text.translatable("meshify.progress.scan", percent)
            : Text.translatable("meshify.progress.scan_eta", percent, Math.round(this.elapsedSeconds() * (1.0 - progress) / progress));
      }

      ExportStage currentStage = this.stage;
      return currentStage == null
         ? null
         : Text.translatable("meshify.progress.write", Text.translatable(currentStage.translationKey()), Math.round(this.elapsedSeconds()));
   }

   private double elapsedSeconds() {
      return (System.nanoTime() - this.startNanos) / 1.0E9;
   }

   private void report(RegionExporter.Result result) {
      this.post(
         Text.translatable(
            "meshify.msg.done",
            String.format(Locale.ROOT, "%.1f", this.elapsedSeconds()),
            result.blocksExported(),
            result.entitiesExported(),
            result.facesEmitted()
         )
      );
      BakeCollector.Stats bakeStats = result.bakeStats();
      if (bakeStats != null) {
         double reduction = bakeStats.facesConsumed() > 0 ? 100.0 * (1.0 - (double)bakeStats.quadsEmitted() / bakeStats.facesConsumed()) : 0.0;
         this.post(
            Text.translatable(
                  "meshify.msg.bake_summary",
                  bakeStats.facesConsumed(),
                  bakeStats.quadsEmitted(),
                  String.format(Locale.ROOT, "%.1f", reduction),
                  bakeStats.sheets(),
                  String.format(Locale.ROOT, "%.1f", bakeStats.bakedPixels() / 1000000.0)
               )
               .formatted(Formatting.AQUA)
         );
      }

      MeshBuilder.OptStats optStats = result.optStats();
      if (optStats != null) {
         this.post(
            Text.translatable(
                  "meshify.msg.weld_summary",
                  result.facesBeforeOptimize(),
                  result.facesEmitted(),
                  optStats.degenerateRemoved(),
                  optStats.duplicateRemoved(),
                  optStats.weldGrid()
               )
               .formatted(Formatting.AQUA)
         );
      }

      if (!result.skippedTypes().isEmpty()) {
         this.post(Text.translatable("meshify.msg.skipped", String.join(", ", result.skippedTypes())).formatted(Formatting.YELLOW));
      }

      for (String warning : result.warnings()) {
         this.post(Text.translatable("meshify.msg.warning", warning).formatted(Formatting.YELLOW));
      }

      // Last, so the link is the line right above the chat input when the export finishes.
      this.post(Text.translatable("meshify.msg.saved", folderLink(result.outDir())));
      this.client.execute(() -> {
         this.showToast("meshify.toast.done");
         this.client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F));
      });
   }

   private void showToast(String titleKey) {
      SystemToast.show(this.client.getToastManager(), TOAST, Text.translatable(titleKey), Text.literal(this.options.name()));
   }

   private void post(Text text) {
      this.client.execute(() -> {
         if (this.client.player != null) {
            this.client.player.sendMessage(text, false);
         }
      });
   }

   private static Text failureMessage(Exception exception) {
      return Text.translatable("meshify.error.failed", String.valueOf(exception)).formatted(Formatting.RED);
   }

   /** Deleting the whole folder is safe: {@code MeshifyActions#uniqueName} never reuses one. */
   private static void deleteQuietly(Path dir) {
      if (!Files.exists(dir)) {
         return;
      }

      try (Stream<Path> paths = Files.walk(dir)) {
         paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
      } catch (IOException ignored) {
      }
   }
}
