package io.toaruesper.addon.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.toaruesper.addon.ToaruEsper;
import io.toaruesper.addon.net.BlackHoleEnd;
import io.toaruesper.addon.net.BlackHoleStart;
import io.toaruesper.addon.senior.SeniorClientConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws the Black Hole (version 2). Client only. Everything is plain 3D geometry drawn directly into the world:
 * <ul>
 * <li>a solid black core with a bright glowing ring around it that always faces you (the "photon ring"),</li>
 * <li>a dim halo that always faces you (the bent light of the disc behind the hole),</li>
 * <li>two tilted discs that glow brighter on one side and swirl,</li>
 * <li>two energy jets along the disc axis,</li>
 * <li>streaks of matter spiralling inward, and rings flowing inward,</li>
 * <li>in the last 0.6 s the core contracts and everything brightens, then a double shockwave and a flash.</li>
 * </ul>
 * Close to a hole, the screen gets dark edges (and a flash at the collapse). That can be turned off in
 * config/toaruesper-client.toml.
 * Nothing here spawns Minecraft particles, and no Minecraft entities are created.
 *
 * Speed protections: at most MAX_ACTIVE holes at once (the oldest is dropped), holes farther than MAX_DISTANCE or outside
 * the camera view are skipped, far holes use fewer segments, the sphere shape is built once, and finished holes remove
 * themselves (also if the "ended" message never arrives).
 */
@Mod.EventBusSubscriber(modid = ToaruEsper.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientBlackHoles {
    private static final int MAX_ACTIVE = 6;
    private static final double MAX_DISTANCE = 160.0;
    private static final int GROW_TICKS = 20;
    private static final int CRITICAL_TICKS = 12;
    private static final int COLLAPSE_TICKS = 16;
    private static final int FADE_TICKS = 10;
    private static final int LEASE_EXTRA_TICKS = 100;
    private static final float CORE_RADIUS = 1.0f;

    private static final float[] WHITE = {0.95f, 0.90f, 1.0f};
    private static final float[] VIOLET = {0.62f, 0.42f, 1.0f};
    private static final float[] BLUE = {0.20f, 0.30f, 1.0f};

    private static final List<Hole> HOLES = new ArrayList<>();

    private static final class Hole {
        final int id;
        final ResourceKey<Level> dimension;
        final Vec3 center;
        final float radius;
        final int duration;
        long startTick;
        long endTick = -1;
        boolean collapsed;

        Hole(int id, ResourceKey<Level> dimension, Vec3 center, float radius, int duration, long startTick) {
            this.id = id;
            this.dimension = dimension;
            this.center = center;
            this.radius = radius;
            this.duration = duration;
            this.startTick = startTick;
        }
    }

    private ClientBlackHoles() { }

    // ---------------------------------------------------------------- messages from the server

    public static void start(BlackHoleStart msg) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        for (var h : HOLES) {
            if (h.id == msg.id()) {
                // Repeated message: just keep our clock in step with the server's.
                if (h.endTick < 0) h.startTick = now - msg.elapsed();
                return;
            }
        }
        if (HOLES.size() >= MAX_ACTIVE) HOLES.remove(0); // drop the oldest
        HOLES.add(new Hole(msg.id(), level.dimension(), new Vec3(msg.x(), msg.y(), msg.z()), msg.radius(), msg.duration(),
                now - msg.elapsed()));
    }

    public static void end(BlackHoleEnd msg) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (var h : HOLES) {
            if (h.id == msg.id() && h.endTick < 0) {
                h.endTick = level.getGameTime();
                h.collapsed = msg.collapsed();
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || HOLES.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) {
            HOLES.clear();
            return;
        }
        long now = level.getGameTime();
        HOLES.removeIf(h -> h.endTick >= 0
                ? now - h.endTick > (h.collapsed ? COLLAPSE_TICKS : FADE_TICKS)
                : now - h.startTick > h.duration + LEASE_EXTRA_TICKS);
    }

    // ---------------------------------------------------------------- the 3D drawing

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || HOLES.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        double time = level.getGameTime() + event.getPartialTick();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        try {
            for (var h : HOLES) {
                if (!h.dimension.equals(level.dimension())) continue;
                double dist = h.center.distanceTo(cam);
                if (dist > MAX_DISTANCE) continue;
                if (!event.getFrustum().isVisible(new AABB(h.center, h.center).inflate(h.radius + 2.0))) continue;
                int step = dist > 110 ? 4 : dist > 48 ? 2 : 1;
                draw(pose, h, cam, dist, time, step);
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static void draw(PoseStack pose, Hole h, Vec3 cam, double camDist, double time, int step) {
        double t = time - h.startTick;
        double critStart = h.duration - CRITICAL_TICKS;
        float grow = smooth((float) (t / GROW_TICKS));
        float field = grow;          // how visible the pull field (discs, jets, streaks, rings) is
        float coreR = CORE_RADIUS * grow;
        // 0 before the final 0.6 s, rising to 1: the hole contracts and brightens
        float crit = smooth((float) ((t - critStart) / CRITICAL_TICKS));
        float shock = -1f;           // below 0 = no shockwave
        if (h.endTick >= 0) {
            double te = time - h.endTick;
            if (h.collapsed) {
                field = (float) Math.max(0.0, 1.0 - te / 3.0);
                coreR *= field;
                shock = (float) Math.min(1.0, te / COLLAPSE_TICKS);
            } else {
                field *= (float) Math.max(0.0, 1.0 - te / FADE_TICKS);
                coreR *= field;
            }
        } else {
            coreR *= 1f - 0.35f * crit;
        }
        float bright = 1f + 0.6f * crit;
        float phase = (float) ((time * 0.35 + 0.8 * crit * crit * Math.max(0.0, t - critStart)) % (2.0 * Math.PI));

        BufferBuilder b = Tesselator.getInstance().getBuilder();
        pose.pushPose();
        pose.translate(h.center.x - cam.x, h.center.y - cam.y, h.center.z - cam.z);
        try {
            Vec3 toCam = cam.subtract(h.center);
            boolean haveFacing = toCam.lengthSqr() > 1.0e-4;
            if (haveFacing) toCam = toCam.normalize();

            // 1. The core: solid black. Skipped when the camera is inside it, so the screen does not go fully black.
            if (coreR > 0.02f && camDist > coreR + 0.3) {
                RenderSystem.depthMask(true);
                RenderSystem.defaultBlendFunc();
                begin(b);
                sphere(b, pose.last().pose(), coreR, 0.01f, 0.0f, 0.03f, 1f);
                flush(b);
            }

            // Everything else glows (adds light) and does not write depth.
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

            if (coreR > 0.02f && step == 1) {
                begin(b);
                sphere(b, pose.last().pose(), coreR * 1.08f, 0.55f, 0.35f, 1.0f, 0.22f * field);
                flush(b);
            }

            // 2. Bright ring and dim halo around the core. They are turned to face the camera, so they always look round.
            if (haveFacing && coreR > 0.05f && field > 0.01f) {
                pose.pushPose();
                pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, (float) toCam.x, (float) toCam.y, (float) toCam.z));
                Matrix4f m = pose.last().pose();
                ring(m, b, coreR * 1.12f, coreR * 0.07f, WHITE, Math.min(1f, 0.95f * field * bright), step);
                ring(m, b, coreR * 1.30f, coreR * 0.16f, VIOLET, 0.35f * field * bright, step);
                annulus(m, b, coreR * 1.25f, coreR * 2.9f, WHITE, 0.30f * field * bright, BLUE, 0f, step);
                pose.popPose();
            }

            if (field > 0.01f) {
                float inner = Math.max(coreR, 0.3f) * 1.5f;
                float outer = Math.max(coreR, 0.3f) * 4.2f;
                disc(pose, b, 72f, inner, outer, Math.min(1f, 0.9f * field * bright), phase, step);
                disc(pose, b, -35f, inner, outer * 0.85f, Math.min(1f, 0.7f * field * bright), -phase * 1.3f, step);
                jets(pose, b, 72f, Math.max(coreR, 0.3f), Math.max(2.5f, h.radius * 0.5f), 0.55f * Math.max(coreR, 0.3f),
                        0.65f * field * bright, phase, step);
                streaks(pose, b, -35f, h.radius, Math.max(coreR, 0.3f), field * bright, time, step);
                inflowRings(pose, b, h.radius, coreR, field, time, step);
            }
            if (shock >= 0f) shockwave(pose, b, h.radius, shock, step);
        } finally {
            pose.popPose();
        }
    }

    // ---------------------------------------------------------------- the screen effect

    @Mod.EventBusSubscriber(modid = ToaruEsper.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ScreenOverlay {
        @SubscribeEvent
        public static void register(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("black_hole_screen", ClientBlackHoles::drawScreen);
        }
    }

    private static final int BANDS = 10;

    private static void drawScreen(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        if (HOLES.isEmpty() || !SeniorClientConfig.SCREEN_EFFECT.get()) return;
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || mc.player == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double time = level.getGameTime() + partial;

        float vignette = 0f;
        float flash = 0f;
        for (var h : HOLES) {
            if (!h.dimension.equals(level.dimension())) continue;
            double dist = h.center.distanceTo(cam);
            double t = time - h.startTick;
            float f = smooth((float) (t / GROW_TICKS));
            if (h.endTick >= 0) {
                double te = time - h.endTick;
                if (h.collapsed) {
                    f = (float) Math.max(0.0, 1.0 - te / 3.0);
                    float u = (float) Math.min(1.0, te / COLLAPSE_TICKS);
                    float near = (float) Mth.clamp(1.0 - dist / (h.radius * 1.1), 0.0, 1.0);
                    flash = Math.max(flash, (1f - u) * (1f - u) * near * 0.6f);
                } else {
                    f *= (float) Math.max(0.0, 1.0 - te / FADE_TICKS);
                }
            }
            float near = (float) Mth.clamp(1.0 - dist / (h.radius * 0.9), 0.0, 1.0);
            vignette = Math.max(vignette, near * near * f);
        }

        if (vignette > 0.01f) {
            int band = Math.max(2, Math.min(width, height) / 22);
            for (int i = 0; i < BANDS; i++) {
                float fall = 1f - i / (float) BANDS;
                int alpha = (int) (vignette * 0.65f * fall * fall * 255f);
                if (alpha <= 0) continue;
                int color = alpha << 24 | 0x14 << 16 | 0x00 << 8 | 0x2c;
                int a = i * band;
                int bb = (i + 1) * band;
                g.fill(a, a, width - a, bb, color);                  // top
                g.fill(a, height - bb, width - a, height - a, color); // bottom
                g.fill(a, bb, bb, height - bb, color);               // left
                g.fill(width - bb, bb, width - a, height - bb, color); // right
            }
        }
        if (flash > 0.01f) {
            int alpha = (int) (Math.min(1f, flash) * 255f);
            g.fill(0, 0, width, height, alpha << 24 | 0xe2 << 16 | 0xd8 << 8 | 0xff);
        }
    }

    // ---------------------------------------------------------------- shapes

    private static final int SEGS = 64;
    private static final float[] COS = new float[SEGS];
    private static final float[] SIN = new float[SEGS];
    private static final float[] ANGLE = new float[SEGS];

    static {
        for (int i = 0; i < SEGS; i++) {
            ANGLE[i] = Mth.TWO_PI * i / SEGS;
            COS[i] = Mth.cos(ANGLE[i]);
            SIN[i] = Mth.sin(ANGLE[i]);
        }
    }

    private static final int STACKS = 12;
    private static final int SLICES = 20;
    /** A unit sphere made of quads, built once. 12 numbers (4 corners x xyz) per quad. */
    private static final float[] SPHERE = buildSphere();

    private static float[] buildSphere() {
        float[] q = new float[STACKS * SLICES * 12];
        int n = 0;
        for (int i = 0; i < STACKS; i++) {
            float p0 = Mth.PI * i / STACKS;
            float p1 = Mth.PI * (i + 1) / STACKS;
            for (int j = 0; j < SLICES; j++) {
                float t0 = Mth.TWO_PI * j / SLICES;
                float t1 = Mth.TWO_PI * (j + 1) / SLICES;
                n = corner(q, n, p0, t0);
                n = corner(q, n, p1, t0);
                n = corner(q, n, p1, t1);
                n = corner(q, n, p0, t1);
            }
        }
        return q;
    }

    private static int corner(float[] q, int n, float phi, float theta) {
        q[n++] = Mth.sin(phi) * Mth.cos(theta);
        q[n++] = Mth.cos(phi);
        q[n++] = Mth.sin(phi) * Mth.sin(theta);
        return n;
    }

    private static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    private static void begin(BufferBuilder b) {
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void flush(BufferBuilder b) {
        BufferUploader.drawWithShader(b.end());
    }

    private static void sphere(BufferBuilder b, Matrix4f m, float r, float red, float green, float blue, float alpha) {
        if (alpha <= 0.002f || r <= 0.001f) return;
        for (int i = 0; i < SPHERE.length; i += 3) {
            b.vertex(m, SPHERE[i] * r, SPHERE[i + 1] * r, SPHERE[i + 2] * r).color(red, green, blue, alpha).endVertex();
        }
    }

    /**
     * A flat disc (in the local flat plane, tilted by tiltDeg). Violet-white inside fading to clear blue outside.
     * One side is brighter than the other (like light from matter rushing toward you), and a swirl runs through it.
     */
    private static void disc(PoseStack pose, BufferBuilder b, float tiltDeg, float inner, float outer, float alpha,
                             float phase, int step) {
        if (alpha <= 0.002f) return;
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(tiltDeg));
        Matrix4f m = pose.last().pose();
        begin(b);
        for (int i = 0; i < SEGS; i += step) {
            int j = (i + step) % SEGS;
            float a0 = alpha * (0.55f + 0.45f * Mth.sin(3f * ANGLE[i] + phase)) * (0.65f + 0.35f * COS[i]);
            float a1 = alpha * (0.55f + 0.45f * Mth.sin(3f * ANGLE[j] + phase)) * (0.65f + 0.35f * COS[j]);
            b.vertex(m, COS[i] * inner, 0f, SIN[i] * inner).color(0.85f, 0.70f, 1.0f, a0).endVertex();
            b.vertex(m, COS[i] * outer, 0f, SIN[i] * outer).color(0.20f, 0.30f, 1.0f, 0f).endVertex();
            b.vertex(m, COS[j] * outer, 0f, SIN[j] * outer).color(0.20f, 0.30f, 1.0f, 0f).endVertex();
            b.vertex(m, COS[j] * inner, 0f, SIN[j] * inner).color(0.85f, 0.70f, 1.0f, a1).endVertex();
        }
        flush(b);
        pose.popPose();
    }

    /** A flat band from one colour and brightness at the inside to another at the outside. */
    private static void annulus(Matrix4f m, BufferBuilder b, float inner, float outer, float[] innerColor, float innerAlpha,
                                float[] outerColor, float outerAlpha, int step) {
        if (innerAlpha <= 0.002f && outerAlpha <= 0.002f) return;
        begin(b);
        for (int i = 0; i < SEGS; i += step) {
            int j = (i + step) % SEGS;
            b.vertex(m, COS[i] * inner, 0f, SIN[i] * inner).color(innerColor[0], innerColor[1], innerColor[2], innerAlpha).endVertex();
            b.vertex(m, COS[i] * outer, 0f, SIN[i] * outer).color(outerColor[0], outerColor[1], outerColor[2], outerAlpha).endVertex();
            b.vertex(m, COS[j] * outer, 0f, SIN[j] * outer).color(outerColor[0], outerColor[1], outerColor[2], outerAlpha).endVertex();
            b.vertex(m, COS[j] * inner, 0f, SIN[j] * inner).color(innerColor[0], innerColor[1], innerColor[2], innerAlpha).endVertex();
        }
        flush(b);
    }

    /** A thin glowing ring in the flat plane: clear at both edges, brightest in the middle. */
    private static void ring(Matrix4f m, BufferBuilder b, float radius, float halfWidth, float[] c, float alpha, int step) {
        ring(m, b, radius, halfWidth, c[0], c[1], c[2], alpha, step);
    }

    private static void ring(Matrix4f m, BufferBuilder b, float radius, float halfWidth, float red, float green, float blue,
                             float alpha, int step) {
        if (alpha <= 0.002f) return;
        float in = Math.max(0.01f, radius - halfWidth);
        float mid = radius;
        float out = radius + halfWidth;
        begin(b);
        for (int i = 0; i < SEGS; i += step) {
            int j = (i + step) % SEGS;
            // inner half
            b.vertex(m, COS[i] * in, 0f, SIN[i] * in).color(red, green, blue, 0f).endVertex();
            b.vertex(m, COS[i] * mid, 0f, SIN[i] * mid).color(red, green, blue, alpha).endVertex();
            b.vertex(m, COS[j] * mid, 0f, SIN[j] * mid).color(red, green, blue, alpha).endVertex();
            b.vertex(m, COS[j] * in, 0f, SIN[j] * in).color(red, green, blue, 0f).endVertex();
            // outer half
            b.vertex(m, COS[i] * mid, 0f, SIN[i] * mid).color(red, green, blue, alpha).endVertex();
            b.vertex(m, COS[i] * out, 0f, SIN[i] * out).color(red, green, blue, 0f).endVertex();
            b.vertex(m, COS[j] * out, 0f, SIN[j] * out).color(red, green, blue, 0f).endVertex();
            b.vertex(m, COS[j] * mid, 0f, SIN[j] * mid).color(red, green, blue, alpha).endVertex();
        }
        flush(b);
    }

    /**
     * Two thin energy jets shooting out of the core along the axis of the disc (up and down along its tilted axis).
     * Each jet is two crossed strips that taper to a point, with pulses of light travelling outward.
     */
    private static void jets(PoseStack pose, BufferBuilder b, float tiltDeg, float coreR, float length, float width,
                             float alpha, float phase, int step) {
        if (alpha <= 0.002f) return;
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(tiltDeg));
        Matrix4f m = pose.last().pose();
        int segs = step == 1 ? 8 : 4;
        begin(b);
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 2; k++) { // two strips crossing each other
                for (int s = 0; s < segs; s++) {
                    float f0 = s / (float) segs;
                    float f1 = (s + 1) / (float) segs;
                    float y0 = side * (coreR * 1.1f + f0 * length);
                    float y1 = side * (coreR * 1.1f + f1 * length);
                    float w0 = width * (1f - f0);
                    float w1 = width * (1f - f1);
                    float a0 = alpha * (1f - f0) * (0.6f + 0.4f * Mth.sin(f0 * 12f - phase * 1.4f));
                    float a1 = alpha * (1f - f1) * (0.6f + 0.4f * Mth.sin(f1 * 12f - phase * 1.4f));
                    float x0 = k == 0 ? w0 : 0f;
                    float z0 = k == 0 ? 0f : w0;
                    float x1 = k == 0 ? w1 : 0f;
                    float z1 = k == 0 ? 0f : w1;
                    b.vertex(m, -x0, y0, -z0).color(0.80f, 0.70f, 1.0f, a0).endVertex();
                    b.vertex(m, x0, y0, z0).color(0.80f, 0.70f, 1.0f, a0).endVertex();
                    b.vertex(m, x1, y1, z1).color(0.80f, 0.70f, 1.0f, a1).endVertex();
                    b.vertex(m, -x1, y1, -z1).color(0.80f, 0.70f, 1.0f, a1).endVertex();
                }
            }
        }
        flush(b);
        pose.popPose();
    }

    /** Short bright arcs of matter that spiral inward toward the core and fade in and out as they travel. */
    private static void streaks(PoseStack pose, BufferBuilder b, float tiltDeg, float reach, float coreR, float alpha,
                                double time, int step) {
        if (alpha <= 0.002f) return;
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(tiltDeg));
        Matrix4f m = pose.last().pose();
        int n = step == 1 ? 20 : 10;
        begin(b);
        for (int k = 0; k < n; k++) {
            float f = (float) ((time / 45.0 + k / (float) n + k * 0.37) % 1.0); // 0 at the edge, 1 at the core
            float r = Mth.lerp(f * f, reach * 0.9f, coreR * 1.5f);
            float ang = k * 2.399963f + f * 5.0f;                               // golden angle plus the inward spiral
            float len = 0.30f + 0.25f * (1f - f);                               // arc length in radians
            float w = 0.05f + 0.05f * (1f - f);
            float a = Mth.sin(f * Mth.PI) * 0.7f * alpha;
            int parts = 3;
            for (int s = 0; s < parts; s++) {
                float t0 = ang - len * s / parts;
                float t1 = ang - len * (s + 1) / parts;
                float a0 = a * (1f - s / (float) parts);
                float a1 = a * (1f - (s + 1) / (float) parts);
                b.vertex(m, Mth.cos(t0) * (r - w), 0f, Mth.sin(t0) * (r - w)).color(0.85f, 0.75f, 1.0f, a0).endVertex();
                b.vertex(m, Mth.cos(t0) * (r + w), 0f, Mth.sin(t0) * (r + w)).color(0.85f, 0.75f, 1.0f, a0).endVertex();
                b.vertex(m, Mth.cos(t1) * (r + w), 0f, Mth.sin(t1) * (r + w)).color(0.85f, 0.75f, 1.0f, a1).endVertex();
                b.vertex(m, Mth.cos(t1) * (r - w), 0f, Mth.sin(t1) * (r - w)).color(0.85f, 0.75f, 1.0f, a1).endVertex();
            }
        }
        flush(b);
        pose.popPose();
    }

    /** Rings that start at the edge of the pull area and shrink toward the core, showing that space is being drawn in. */
    private static void inflowRings(PoseStack pose, BufferBuilder b, float radius, float coreR, float field, double time, int step) {
        for (int k = 0; k < 2; k++) {
            float f = (float) ((time / 50.0 + k / 2.0) % 1.0);
            float r = Mth.lerp(f, radius * 0.95f, Math.max(coreR, 0.3f) * 1.8f);
            float a = Mth.sin(f * Mth.PI) * 0.30f * field;
            float halfWidth = 0.10f + 0.12f * (1f - f);
            for (int o = 0; o < 2; o++) {
                pose.pushPose();
                if (o == 1) pose.mulPose(Axis.XP.rotationDegrees(90f));
                ring(pose.last().pose(), b, r, halfWidth, 0.45f, 0.40f, 1.0f, a, step);
                pose.popPose();
            }
        }
    }

    /** The collapse: a bright flash, two glowing shells one after the other, and a flat ring that races outward. */
    private static void shockwave(PoseStack pose, BufferBuilder b, float radius, float u, int step) {
        float eased = 1f - (1f - u) * (1f - u) * (1f - u);
        float fade = 1f - u;
        Matrix4f m = pose.last().pose();
        begin(b);
        sphere(b, m, CORE_RADIUS * (1f + 2.5f * u), 1.0f, 0.90f, 1.0f, 0.85f * fade * fade * fade);
        flush(b);
        float shell = Mth.lerp(eased, CORE_RADIUS, radius * 1.05f);
        begin(b);
        sphere(b, m, shell, 0.62f, 0.45f, 1.0f, 0.50f * fade * fade);
        flush(b);
        // a second, slightly later shell
        float u2 = Mth.clamp((u - 0.2f) / 0.8f, 0f, 1f);
        if (u2 > 0f) {
            float eased2 = 1f - (1f - u2) * (1f - u2) * (1f - u2);
            begin(b);
            sphere(b, m, Mth.lerp(eased2, CORE_RADIUS, radius * 0.8f), 0.85f, 0.75f, 1.0f, 0.30f * (1f - u2) * (1f - u2));
            flush(b);
        }
        ring(m, b, shell * 1.25f, 0.5f + 0.8f * u, 0.85f, 0.75f, 1.0f, 0.8f * fade, step);
    }
}
