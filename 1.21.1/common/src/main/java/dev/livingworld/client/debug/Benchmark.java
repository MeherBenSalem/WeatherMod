package dev.livingworld.client.debug;

import dev.livingworld.LivingWorld;
import dev.livingworld.client.LivingWorldClient;
import dev.livingworld.client.compat.Compat;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.footprint.FootprintTracker;
import dev.livingworld.client.particle.ParticleEngine;
import dev.livingworld.client.render.PrecipitationRenderer;
import dev.livingworld.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * {@code /livingworld benchmark}: an honest A/B measurement at the place and in the conditions you are
 * standing in. It records frame times with the mod's effects enabled, then with the master switch off,
 * and reports the difference together with the per-system timers and the hardware. Results go to chat
 * and to {@code livingworld-benchmark.txt}.
 */
public final class Benchmark {
    private static final int WARMUP_FRAMES = 30;

    private static boolean active;
    private static int phase;
    private static long phaseEnd;
    private static long phaseNanos;
    private static long lastFrame;
    private static int skip;
    private static float[] samples = new float[1 << 16];
    private static int count;
    private static boolean previousEnabled;
    private static String conditions = "";
    private static double onAvg, onLow, onP99;
    private static String onSystems = "";
    private static int onParticles, onDrops, onDecals, onQuads;
    /** Set when a run finishes (used by the automated test). */
    public static String lastReport;

    private Benchmark() {
    }

    public static boolean running() {
        return active;
    }

    public static void start(int seconds) {
        if (active) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientEnvironment e = ClientEnvironment.INSTANCE;
        active = true;
        phase = 0;
        phaseNanos = Math.max(3, seconds / 2) * 1_000_000_000L;
        previousEnabled = ClientConfig.get().enabled;
        ClientConfig.get().enabled = true;
        conditions = String.format(Locale.ROOT, "%s, %s, air %.0f°C, wind %.2f, precip %.2f, sand %.2f", e.dominant, e.weatherType.id(),
                e.ambientC, e.wind.speed, e.precip, e.sand);
        beginPhase();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("[Living World] Benchmark started (" + Math.max(3, seconds / 2) * 2
                    + " s). Keep the camera still.").withStyle(ChatFormatting.AQUA), false);
        }
    }

    private static void beginPhase() {
        count = 0;
        skip = WARMUP_FRAMES;
        lastFrame = 0L;
        phaseEnd = System.nanoTime() + phaseNanos;
        Perf.resetAll();
    }

    /** Called once per rendered frame. */
    public static void frame() {
        if (!active) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrame != 0L) {
            if (skip > 0) {
                skip--;
                if (skip == 0) {
                    phaseEnd = now + phaseNanos;
                    Perf.resetAll();
                }
            } else if (count < samples.length) {
                samples[count++] = (now - lastFrame) / 1.0e6f;
            }
        }
        lastFrame = now;
        if (skip == 0 && now >= phaseEnd) {
            finishPhase();
        }
    }

    private static void finishPhase() {
        float[] sorted = Arrays.copyOf(samples, count);
        Arrays.sort(sorted);
        double sum = 0.0;
        for (float s : sorted) {
            sum += s;
        }
        double avgMs = count == 0 ? 0.0 : sum / count;
        // "1% low": average of the slowest 1% of frames.
        int tail = Math.max(1, count / 100);
        double tailSum = 0.0;
        for (int i = count - tail; i < count; i++) {
            tailSum += sorted[Math.max(0, i)];
        }
        double lowMs = tailSum / tail;
        double p99 = count == 0 ? 0.0 : sorted[Math.min(count - 1, (int) (count * 0.99))];

        if (phase == 0) {
            onAvg = avgMs;
            onLow = lowMs;
            onP99 = p99;
            StringBuilder sb = new StringBuilder();
            for (Perf p : Perf.values()) {
                sb.append(String.format(Locale.ROOT, "    %-34s %7.0f µs mean%n", p.label, p.meanMicros()));
            }
            onSystems = sb.toString();
            onParticles = ParticleEngine.INSTANCE.count;
            onDrops = PrecipitationRenderer.INSTANCE.lastDrops;
            onDecals = FootprintTracker.INSTANCE.decals.count;
            onQuads = LivingWorldClient.batch().lastQuads;
            phase = 1;
            ClientConfig.get().enabled = false;
            beginPhase();
            return;
        }

        ClientConfig.get().enabled = previousEnabled;
        active = false;
        Minecraft client = Minecraft.getInstance();
        String report = String.format(Locale.ROOT,
                "Living World benchmark%n"
                        + "  GPU: %s (%s)%n"
                        + "  CPU: %s, %d threads; Java %s%n"
                        + "  Window: %dx%d, render distance %d, graphics %s, preset %s%n"
                        + "  Renderers: %s%n"
                        + "  Conditions: %s%n"
                        + "  Effects ON : %.1f fps avg (%.2f ms), 1%% low %.1f fps, p99 %.2f ms%n"
                        + "  Effects OFF: %.1f fps avg (%.2f ms), 1%% low %.1f fps, p99 %.2f ms%n"
                        + "  Cost of the mod here: %.2f ms per frame (%.1f%% of the frame)%n"
                        + "  Live while ON: %d particles, %d precipitation quads, %d decals, %d batched quads%n"
                        + "  Per-system time while ON:%n%s",
                GL11.glGetString(GL11.GL_RENDERER), GL11.glGetString(GL11.GL_VERSION),
                String.valueOf(System.getenv("PROCESSOR_IDENTIFIER")), Runtime.getRuntime().availableProcessors(),
                System.getProperty("java.version"),
                client.getWindow().getWidth(), client.getWindow().getHeight(),
                client.options.renderDistance().get(), client.options.graphicsMode().get(), QualityManager.preset(),
                Compat.describe(), conditions,
                1000.0 / Math.max(0.001, onAvg), onAvg, 1000.0 / Math.max(0.001, onLow), onP99,
                1000.0 / Math.max(0.001, avgMs), avgMs, 1000.0 / Math.max(0.001, lowMs), p99,
                onAvg - avgMs, onAvg <= 0.0 ? 0.0 : (onAvg - avgMs) / onAvg * 100.0,
                onParticles, onDrops, onDecals, onQuads, onSystems);
        lastReport = report;
        LivingWorld.LOGGER.info(report);
        try {
            Path file = Services.PLATFORM.getGameDir().resolve("livingworld-benchmark.txt");
            Files.writeString(file, report);
        } catch (Exception e) {
            LivingWorld.LOGGER.warn("Could not write the benchmark report: {}", e.toString());
        }
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "[Living World] ON %.0f fps / OFF %.0f fps (1%% low %.0f / %.0f). Cost %.2f ms per frame. Details in livingworld-benchmark.txt",
                    1000.0 / Math.max(0.001, onAvg), 1000.0 / Math.max(0.001, avgMs), 1000.0 / Math.max(0.001, onLow),
                    1000.0 / Math.max(0.001, lowMs), onAvg - avgMs)).withStyle(ChatFormatting.AQUA), false);
        }
    }
}
