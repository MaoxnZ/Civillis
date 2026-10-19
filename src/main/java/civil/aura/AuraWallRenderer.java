package civil.aura;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Client-side renderer for protection zone boundary walls using the vanilla
 * {@code forcefield.png} texture via {@code GameRenderer.getPositionTexProgram()}.
 *
 * <h3>Dual wall types</h3>
 * <ul>
 *   <li><b>Civilization walls</b> -- warm amber-gold, global wallMinY/wallMaxY,
 *       showing boundaries between HIGH and LOW/MID civilization zones.</li>
 *   <li><b>Farm shrine bypass walls</b> -- amethyst/crystal purple, per-face faceMinY/faceMaxY,
 *       showing activated shrine bypass envelopes (VC neighborhood from config ranges).</li>
 * </ul>
 * Both types share the same phase timing (sonar delay, fade-in, steady, fade-out)
 * but have independent per-face alpha tracking.
 *
 * <h3>Per-face alpha tracking</h3>
 * <p>Each boundary face is individually tracked by identity. Newly discovered faces
 * independently fade in from 0 over {@link #FACE_FADE_IN_S} seconds.
 *
 * <h3>Bucketed multi-pass rendering</h3>
 * <p>Faces at different alpha levels are grouped into buckets (quantized to 0.1
 * increments). Each non-empty bucket is rendered in its own draw call with a
 * distinct {@code RenderSystem.setShaderColor()}.
 *
 * <h3>1.20.1 rendering</h3>
 * <p>{@link Tesselator#getBuilder()}, {@code POSITION_TEX} quads, {@link BufferUploader#drawWithShader}.
 */
public final class AuraWallRenderer {

    private AuraWallRenderer() {}

    // ========== Per-face identity (civilization) ==========

    /**
     * Uniquely identifies a civilization boundary face across successive scans.
     */
    private record FaceId(int axis, long planeCoord, long minU) {
        static FaceId of(BoundaryFaceData face) {
            return new FaceId(face.axis(), (long) face.planeCoord(), (long) face.minU());
        }
    }

    /** A civilization boundary face paired with its discovery timestamp and optional fade-out. */
    private record TimedFace(BoundaryFaceData face, long arrivalNano, long fadeOutNano) {
        /** Sentinel value: not fading out. */
        static final long NOT_FADING = Long.MAX_VALUE;
        TimedFace(BoundaryFaceData face, long arrivalNano) { this(face, arrivalNano, NOT_FADING); }
        boolean isFading() { return fadeOutNano != NOT_FADING; }
    }

    // ========== per-face identity (shrine bypass zone) ==========

    /**
     * Uniquely identifies a zone/shrine boundary face. Includes {@code faceMinY}
     * so scans taken on different Y layers do not reuse fade state accidentally.
     */
    private record ShrineFaceId(int axis, long planeCoord, long minU, long faceMinY) {
        static ShrineFaceId of(ShrineFaceData face) {
            return new ShrineFaceId(face.axis(), (long) face.planeCoord(),
                    (long) face.minU(), (long) face.faceMinY());
        }
    }

    /** A shrine bypass boundary face paired with its discovery timestamp and optional fade-out. */
    private record TimedShrineFace(ShrineFaceData face, long arrivalNano, long fadeOutNano) {
        static final long NOT_FADING = Long.MAX_VALUE;
        TimedShrineFace(ShrineFaceData face, long arrivalNano) { this(face, arrivalNano, NOT_FADING); }
        boolean isFading() { return fadeOutNano != NOT_FADING; }
    }

    // ========== Timing ==========

    /** Delay before walls appear on first activation (sonar plays first). */
    private static final long SONAR_DELAY_NS = (long) (1.2 * 1_000_000_000L);

    /** Fade-in speed: alpha per second.  1/0.6 = 1.667 -> reaches 1.0 in 0.6 s. */
    private static final float FADE_IN_RATE = 1.0f / 0.6f;

    /** Fade-out speed: alpha per second.  1/2.0 = 0.5 -> reaches 0.0 in 2 s. */
    private static final float FADE_OUT_RATE = 1.0f / 2.0f;

    /** Per-face fade-in duration (seconds). Newly discovered faces ramp 0->1 over this time. */
    private static final float FACE_FADE_IN_S = 0.6f;

    // ========== Alpha ==========
    private static final float BASE_ALPHA = 0.55f;

    // ========== Breathing ==========
    private static final float BREATHE_AMP   = 0.08f;
    private static final float BREATHE_SPEED = 1.5f;

    // ========== Color: civilization walls (warm amber-gold) ==========
    private static final float WALL_R = 0.90f, WALL_G = 0.78f, WALL_B = 0.50f;

    // ========== Color: shrine bypass walls (amethyst / crystal purple) ==========
    private static final float HEAD_R = 0.68f, HEAD_G = 0.40f, HEAD_B = 0.88f;

    // ========== Color: structure zone policy (caution red-orange) ==========
    private static final float ZONE_R = 1.0f, ZONE_G = 0.50f, ZONE_B = 0.30f;

    // ========== Texture ==========
    private static final ResourceLocation FORCEFIELD_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/forcefield.png");

    // ========== State: civilization walls ==========

    /** Immutable snapshot of civilization faces + discovery timestamps. */
    private static volatile List<TimedFace> timedFaces = List.of();

    private static double wallMinY, wallMaxY;

    /** Per-face discovery time tracking. Only accessed from the network thread. */
    private static final HashMap<FaceId, Long> faceArrivalMap = new HashMap<>();

    // ========== State: shrine bypass walls ==========

    /** Immutable snapshot of shrine bypass faces + discovery timestamps. */
    private static volatile List<TimedShrineFace> timedShrineFaces = List.of();

    /** Per-face discovery time tracking for shrine bypass zones. Only accessed from the network thread. */
    private static final HashMap<ShrineFaceId, Long> shrineFaceArrivalMap = new HashMap<>();

    // ========== State: structure zone policy walls (same geometry type as shrine faces) ==========

    private static volatile List<TimedShrineFace> timedZoneFaces = List.of();

    private static final HashMap<ShrineFaceId, Long> zoneFaceArrivalMap = new HashMap<>();

    // ========== State: shared phase animation ==========

    /** Current rendered phase alpha [0..1], smoothly interpolated toward target each frame. */
    private static float phaseAlpha = 0.0f;

    /** Walls become visible after this nano timestamp (sonar delay). */
    private static long visibleAfterNano = 0;

    /** Steady phase ends at this nano timestamp -> fade-out begins. Extended on renewal. */
    private static long steadyEndNano = 0;

    /** Previous frame nano timestamp for computing per-frame delta time. */
    private static long lastFrameNano = 0;

    // ========== Public API ==========

    /**
     * Called from the network thread when a new {@link SonarBoundaryPayload} arrives.
     * Updates civilization walls, farm shrine bypass faces, and structure-policy zone faces.
     */
    public static void updateBoundaries(SonarBoundaryPayload payload) {
        long now = System.nanoTime();

        // Update geometry immediately -- player may have moved.
        wallMinY = payload.wallMinY();
        wallMaxY = payload.wallMaxY();

        // Sonar delay always applies: shockwave is visual feedback for a new scan.
        SonarType type = SonarType.fromId(payload.sonarType());
        long steadyDurationNs = (long) (type.wallSteadyDurationSeconds() * 1_000_000_000L);
        visibleAfterNano = now + SONAR_DELAY_NS;
        steadyEndNano = visibleAfterNano + steadyDurationNs;

        // Fresh activation vs. renewal determines the arrival timestamp for new faces.
        boolean freshActivation = timedFaces.isEmpty() && timedShrineFaces.isEmpty() && timedZoneFaces.isEmpty();
        long newFaceArrival = freshActivation ? now : visibleAfterNano;

        if (freshActivation) {
            faceArrivalMap.clear();
            shrineFaceArrivalMap.clear();
            zoneFaceArrivalMap.clear();
        }

        // ---- Civilization faces ----
        Set<FaceId> newCivIds = new HashSet<>();
        List<TimedFace> civResult = new ArrayList<>(payload.faces().size());
        for (BoundaryFaceData face : payload.faces()) {
            FaceId id = FaceId.of(face);
            newCivIds.add(id);
            long arrival = faceArrivalMap.computeIfAbsent(id, k -> newFaceArrival);
            civResult.add(new TimedFace(face, arrival));
        }
        // Retain removed faces as fading-out (smooth disappearance)
        if (!freshActivation) {
            for (TimedFace oldFace : timedFaces) {
                FaceId oldId = FaceId.of(oldFace.face());
                if (!newCivIds.contains(oldId) && !oldFace.isFading()) {
                    // Face removed in this renewal -> start fading out
                    civResult.add(new TimedFace(oldFace.face(), oldFace.arrivalNano(), now));
                } else if (oldFace.isFading() && newCivIds.contains(FaceId.of(oldFace.face()))) {
                    // Was fading but reappeared -- already re-added above as non-fading
                } else if (oldFace.isFading()) {
                    // Still fading and not re-added -- keep fading
                    float fadeElapsed = (now - oldFace.fadeOutNano()) / 1_000_000_000f;
                    if (fadeElapsed < FACE_FADE_IN_S) {
                        civResult.add(oldFace); // keep until fully faded
                    }
                }
            }
        }
        faceArrivalMap.keySet().retainAll(newCivIds);
        // Volatile write provides memory fence
        timedFaces = List.copyOf(civResult);

        // ---- Shrine bypass faces ----
        Set<ShrineFaceId> newShrineIds = new HashSet<>();
        List<TimedShrineFace> shrineResult = new ArrayList<>(payload.shrineFaces().size());
        for (ShrineFaceData face : payload.shrineFaces()) {
            ShrineFaceId id = ShrineFaceId.of(face);
            newShrineIds.add(id);
            long arrival = shrineFaceArrivalMap.computeIfAbsent(id, k -> newFaceArrival);
            shrineResult.add(new TimedShrineFace(face, arrival));
        }
        // Retain removed shrine bypass faces as fading-out (smooth disappearance)
        if (!freshActivation) {
            for (TimedShrineFace oldFace : timedShrineFaces) {
                ShrineFaceId oldId = ShrineFaceId.of(oldFace.face());
                if (!newShrineIds.contains(oldId) && !oldFace.isFading()) {
                    shrineResult.add(new TimedShrineFace(oldFace.face(), oldFace.arrivalNano(), now));
                } else if (oldFace.isFading() && newShrineIds.contains(ShrineFaceId.of(oldFace.face()))) {
                    // Reappeared -- already re-added above
                } else if (oldFace.isFading()) {
                    float fadeElapsed = (now - oldFace.fadeOutNano()) / 1_000_000_000f;
                    if (fadeElapsed < FACE_FADE_IN_S) {
                        shrineResult.add(oldFace);
                    }
                }
            }
        }
        shrineFaceArrivalMap.keySet().retainAll(newShrineIds);
        timedShrineFaces = List.copyOf(shrineResult);

        // ---- Structure zone policy faces ----
        Set<ShrineFaceId> newZoneIds = new HashSet<>();
        List<TimedShrineFace> zoneResult = new ArrayList<>(payload.zoneFaces().size());
        for (ShrineFaceData face : payload.zoneFaces()) {
            ShrineFaceId id = ShrineFaceId.of(face);
            newZoneIds.add(id);
            long arrival = zoneFaceArrivalMap.computeIfAbsent(id, k -> newFaceArrival);
            zoneResult.add(new TimedShrineFace(face, arrival));
        }
        if (!freshActivation) {
            for (TimedShrineFace oldFace : timedZoneFaces) {
                ShrineFaceId oldId = ShrineFaceId.of(oldFace.face());
                if (!newZoneIds.contains(oldId) && !oldFace.isFading()) {
                    zoneResult.add(new TimedShrineFace(oldFace.face(), oldFace.arrivalNano(), now));
                } else if (oldFace.isFading() && newZoneIds.contains(ShrineFaceId.of(oldFace.face()))) {
                    // Reappeared -- already re-added above
                } else if (oldFace.isFading()) {
                    float fadeElapsed = (now - oldFace.fadeOutNano()) / 1_000_000_000f;
                    if (fadeElapsed < FACE_FADE_IN_S) {
                        zoneResult.add(oldFace);
                    }
                }
            }
        }
        zoneFaceArrivalMap.keySet().retainAll(newZoneIds);
        timedZoneFaces = List.copyOf(zoneResult);
    }

    /** No GPU resources to release in legacy rendering mode. */
    public static void close() {
        // No-op: 1.20.1 uses Tessellator (singleton, not owned by us)
    }

    // ========== Render callback ==========

    public static void onRender(PoseStack poseStack) {
        SonarShockwaveEffect.tick();

        List<TimedFace> faces = timedFaces;
        List<TimedShrineFace> shrineFaces = timedShrineFaces;
        List<TimedShrineFace> zoneFaces = timedZoneFaces;

        boolean hasFaces = !faces.isEmpty() || !shrineFaces.isEmpty() || !zoneFaces.isEmpty();

        if (!hasFaces && phaseAlpha < 0.01f) return;

        long now = System.nanoTime();
        float dt = lastFrameNano > 0 ? (now - lastFrameNano) / 1_000_000_000f : 0.016f;
        dt = Math.min(dt, 0.1f);
        lastFrameNano = now;

        float targetAlpha;
        if (!hasFaces) {
            targetAlpha = 0.0f;
        } else if (now < visibleAfterNano) {
            targetAlpha = phaseAlpha > 0.05f ? 1.0f : 0.0f;
        } else if (now < steadyEndNano) {
            targetAlpha = 1.0f;
        } else {
            targetAlpha = 0.0f;
        }

        if (phaseAlpha < targetAlpha) {
            phaseAlpha = Math.min(targetAlpha, phaseAlpha + FADE_IN_RATE * dt);
        } else if (phaseAlpha > targetAlpha) {
            phaseAlpha = Math.max(targetAlpha, phaseAlpha - FADE_OUT_RATE * dt);
        }

        if (phaseAlpha < 0.01f) {
            phaseAlpha = 0.0f;
            if (now > visibleAfterNano && now > steadyEndNano) {
                timedFaces = List.of();
                timedShrineFaces = List.of();
                timedZoneFaces = List.of();
                lastFrameNano = 0;
            }
            return;
        }

        float breathe = 1.0f - BREATHE_AMP
                + BREATHE_AMP * (float) Math.sin(now / 1_000_000_000.0 * BREATHE_SPEED);
        float globalAlpha = BASE_ALPHA * phaseAlpha * breathe;

        float scroll = ((now / 1_000_000L) % 3000L) / 3000.0f;

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Matrix4f posMatrix = poseStack.last().pose();

        if (!faces.isEmpty()) {
            renderCivilizationWalls(faces, now, globalAlpha, scroll, cam, posMatrix);
        }

        if (!shrineFaces.isEmpty()) {
            renderShrineStyleWalls(shrineFaces, now, globalAlpha, scroll, cam, posMatrix, HEAD_R, HEAD_G, HEAD_B);
        }
        if (!zoneFaces.isEmpty()) {
            renderShrineStyleWalls(zoneFaces, now, globalAlpha, scroll, cam, posMatrix, ZONE_R, ZONE_G, ZONE_B);
        }
    }

    // ========== Civilization wall rendering ==========

    private static void renderCivilizationWalls(List<TimedFace> faces, long now,
                                                 float globalAlpha, float scroll, Vec3 cam, Matrix4f posMatrix) {
        // Group faces into alpha buckets
        TreeMap<Integer, List<TimedFace>> buckets = new TreeMap<>();
        for (TimedFace tf : faces) {
            float faceAlpha;
            if (tf.isFading()) {
                // Fade-out: alpha decreases from 1->0 over FACE_FADE_IN_S
                float fadeElapsed = (now - tf.fadeOutNano()) / 1_000_000_000f;
                faceAlpha = Math.max(0.0f, 1.0f - fadeElapsed / FACE_FADE_IN_S);
            } else {
                // Normal fade-in
                float elapsed = (now - tf.arrivalNano()) / 1_000_000_000f;
                if (elapsed < 0.0f) continue; // Not yet visible (sonar delay)
                faceAlpha = Math.min(1.0f, elapsed / FACE_FADE_IN_S);
            }
            if (faceAlpha <= 0.0f) continue; // Fully faded out
            int bucket = Math.max(1, Math.min(10, Math.round(faceAlpha * 10)));
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(tf);
        }
        if (buckets.isEmpty()) return;

        // Set up render state
        beginWallRender();

        for (var entry : buckets.entrySet()) {
            int bucketLevel = entry.getKey();
            float faceAlpha  = bucketLevel / 10.0f;
            float finalAlpha = globalAlpha * faceAlpha;
            if (finalAlpha < 0.005f) continue;

            RenderSystem.setShaderColor(WALL_R, WALL_G, WALL_B, finalAlpha);

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

            for (TimedFace tf : entry.getValue()) {
                BoundaryFaceData face = tf.face();
                double plane = face.planeCoord();
                double minU  = face.minU();
                double maxU  = minU + 16.0;

                float texU1 = (float) (minU    * 0.5) + scroll;
                float texU2 = (float) (maxU    * 0.5) + scroll;
                float texV1 = (float) (wallMinY * 0.5) + scroll;
                float texV2 = (float) (wallMaxY * 0.5) + scroll;

                if (face.axis() == 0) {
                    addQuadYZ(builder, posMatrix, cam, plane, minU, wallMinY, maxU, wallMaxY,
                              texU1, texV1, texU2, texV2, true);
                    addQuadYZ(builder, posMatrix, cam, plane, minU, wallMinY, maxU, wallMaxY,
                              texU1, texV1, texU2, texV2, false);
                } else {
                    addQuadXY(builder, posMatrix, cam, plane, minU, wallMinY, maxU, wallMaxY,
                              texU1, texV1, texU2, texV2, true);
                    addQuadXY(builder, posMatrix, cam, plane, minU, wallMinY, maxU, wallMaxY,
                              texU1, texV1, texU2, texV2, false);
                }
            }

            BufferUploader.drawWithShader(builder.end());
        }

        endWallRender();
    }

    // ========== Shrine bypass wall rendering ==========

    private static void renderShrineStyleWalls(List<TimedShrineFace> faces, long now,
                                              float globalAlpha, float scroll, Vec3 cam, Matrix4f posMatrix,
                                              float cr, float cg, float cb) {
        // Group faces into alpha buckets
        TreeMap<Integer, List<TimedShrineFace>> buckets = new TreeMap<>();
        for (TimedShrineFace tf : faces) {
            float faceAlpha;
            if (tf.isFading()) {
                float fadeElapsed = (now - tf.fadeOutNano()) / 1_000_000_000f;
                faceAlpha = Math.max(0.0f, 1.0f - fadeElapsed / FACE_FADE_IN_S);
            } else {
                float elapsed = (now - tf.arrivalNano()) / 1_000_000_000f;
                if (elapsed < 0.0f) continue;
                faceAlpha = Math.min(1.0f, elapsed / FACE_FADE_IN_S);
            }
            if (faceAlpha <= 0.0f) continue;
            int bucket = Math.max(1, Math.min(10, Math.round(faceAlpha * 10)));
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(tf);
        }
        if (buckets.isEmpty()) return;

        // Set up render state
        beginWallRender();

        for (var entry : buckets.entrySet()) {
            int bucketLevel = entry.getKey();
            float faceAlpha  = bucketLevel / 10.0f;
            float finalAlpha = globalAlpha * faceAlpha;
            if (finalAlpha < 0.005f) continue;

            RenderSystem.setShaderColor(cr, cg, cb, finalAlpha);

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

            for (TimedShrineFace tf : entry.getValue()) {
                ShrineFaceData face = tf.face();
                double plane = face.planeCoord();
                double minU  = face.minU();
                double maxU  = minU + 16.0;
                double fMinY = face.faceMinY();
                double fMaxY = face.faceMaxY();

                float texU1 = (float) (minU  * 0.5) + scroll;
                float texU2 = (float) (maxU  * 0.5) + scroll;
                float texV1 = (float) (fMinY * 0.5) + scroll;
                float texV2 = (float) (fMaxY * 0.5) + scroll;

                if (face.axis() == 0) {
                    addQuadYZ(builder, posMatrix, cam, plane, minU, fMinY, maxU, fMaxY,
                              texU1, texV1, texU2, texV2, true);
                    addQuadYZ(builder, posMatrix, cam, plane, minU, fMinY, maxU, fMaxY,
                              texU1, texV1, texU2, texV2, false);
                } else {
                    addQuadXY(builder, posMatrix, cam, plane, minU, fMinY, maxU, fMaxY,
                              texU1, texV1, texU2, texV2, true);
                    addQuadXY(builder, posMatrix, cam, plane, minU, fMinY, maxU, fMaxY,
                              texU1, texV1, texU2, texV2, false);
                }
            }

            BufferUploader.drawWithShader(builder.end());
        }

        endWallRender();
    }

    // ========== Render state management ==========

    /** Set up OpenGL state for translucent wall rendering. */
    private static void beginWallRender() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        // Bind the forcefield texture
        Minecraft client = Minecraft.getInstance();
        AbstractTexture tex = client.getTextureManager().getTexture(FORCEFIELD_TEXTURE);
        RenderSystem.setShaderTexture(0, tex.getId());
    }

    /** Restore OpenGL state after wall rendering. */
    private static void endWallRender() {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    // ========== Vertex emission ==========

    // ---- YZ-plane quad (X axis boundary) ----
    private static void addQuadYZ(BufferBuilder buf, Matrix4f matrix, Vec3 cam,
                                  double x, double minZ, double minY, double maxZ, double maxY,
                                  float u1, float v1, float u2, float v2,
                                  boolean front) {
        float fx  = (float) (x    - cam.x);
        float fy1 = (float) (minY - cam.y);
        float fy2 = (float) (maxY - cam.y);
        float fz1 = (float) (minZ - cam.z);
        float fz2 = (float) (maxZ - cam.z);

        if (front) {
            buf.vertex(matrix, fx, fy1, fz1).uv(u1, v1).endVertex();
            buf.vertex(matrix, fx, fy1, fz2).uv(u2, v1).endVertex();
            buf.vertex(matrix, fx, fy2, fz2).uv(u2, v2).endVertex();
            buf.vertex(matrix, fx, fy2, fz1).uv(u1, v2).endVertex();
        } else {
            buf.vertex(matrix, fx, fy2, fz1).uv(u1, v2).endVertex();
            buf.vertex(matrix, fx, fy2, fz2).uv(u2, v2).endVertex();
            buf.vertex(matrix, fx, fy1, fz2).uv(u2, v1).endVertex();
            buf.vertex(matrix, fx, fy1, fz1).uv(u1, v1).endVertex();
        }
    }

    // ---- XY-plane quad (Z axis boundary) ----
    private static void addQuadXY(BufferBuilder buf, Matrix4f matrix, Vec3 cam,
                                  double z, double minX, double minY, double maxX, double maxY,
                                  float u1, float v1, float u2, float v2,
                                  boolean front) {
        float fx1 = (float) (minX - cam.x);
        float fx2 = (float) (maxX - cam.x);
        float fy1 = (float) (minY - cam.y);
        float fy2 = (float) (maxY - cam.y);
        float fz  = (float) (z    - cam.z);

        if (front) {
            buf.vertex(matrix, fx1, fy1, fz).uv(u1, v1).endVertex();
            buf.vertex(matrix, fx2, fy1, fz).uv(u2, v1).endVertex();
            buf.vertex(matrix, fx2, fy2, fz).uv(u2, v2).endVertex();
            buf.vertex(matrix, fx1, fy2, fz).uv(u1, v2).endVertex();
        } else {
            buf.vertex(matrix, fx1, fy2, fz).uv(u1, v2).endVertex();
            buf.vertex(matrix, fx2, fy2, fz).uv(u2, v2).endVertex();
            buf.vertex(matrix, fx2, fy1, fz).uv(u2, v1).endVertex();
            buf.vertex(matrix, fx1, fy1, fz).uv(u1, v1).endVertex();
        }
    }
}

