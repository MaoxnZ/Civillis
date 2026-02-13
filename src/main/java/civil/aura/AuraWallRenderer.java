package civil.aura;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

/**
 * Client-side renderer for protection zone boundary walls using the vanilla
 * {@code forcefield.png} texture via the {@code RENDERTYPE_WORLD_BORDER} pipeline.
 *
 * <h3>Dual wall types</h3>
 * <ul>
 *   <li><b>Civilization walls</b> — warm amber-gold, global wallMinY/wallMaxY,
 *       showing boundaries between HIGH and LOW/MID civilization zones.</li>
 *   <li><b>Head zone walls</b> — amethyst/crystal purple, per-face faceMinY/faceMaxY,
 *       showing mob head "Force Allow" zone envelopes (3×3×1 VC neighborhood).</li>
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
 * increments). Each non-empty bucket is rendered in its own {@link RenderPass}.
 */
@Environment(EnvType.CLIENT)
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

    // ========== Per-face identity (head zone) ==========

    /**
     * Uniquely identifies a head zone boundary face. Includes {@code faceMinY}
     * because different heads at different Y levels produce distinct faces.
     */
    private record HeadFaceId(int axis, long planeCoord, long minU, long faceMinY) {
        static HeadFaceId of(HeadZoneFaceData face) {
            return new HeadFaceId(face.axis(), (long) face.planeCoord(),
                    (long) face.minU(), (long) face.faceMinY());
        }
    }

    /** A head zone boundary face paired with its discovery timestamp and optional fade-out. */
    private record TimedHeadFace(HeadZoneFaceData face, long arrivalNano, long fadeOutNano) {
        static final long NOT_FADING = Long.MAX_VALUE;
        TimedHeadFace(HeadZoneFaceData face, long arrivalNano) { this(face, arrivalNano, NOT_FADING); }
        boolean isFading() { return fadeOutNano != NOT_FADING; }
    }

    // ========== Timing ==========

    /** Delay before walls appear on first activation (sonar plays first). */
    private static final long SONAR_DELAY_NS = (long) (1.2 * 1_000_000_000L);

    /** How long walls stay at full brightness per activation / renewal. */
    private static final long STEADY_DURATION_NS = (long) (2.5 * 1_000_000_000L);

    /** Fade-in speed: alpha per second.  1/0.6 ≈ 1.667 → reaches 1.0 in 0.6 s. */
    private static final float FADE_IN_RATE = 1.0f / 0.6f;

    /** Fade-out speed: alpha per second.  1/2.0 = 0.5 → reaches 0.0 in 2 s. */
    private static final float FADE_OUT_RATE = 1.0f / 2.0f;

    /** Per-face fade-in duration (seconds). Newly discovered faces ramp 0→1 over this time. */
    private static final float FACE_FADE_IN_S = 0.6f;

    // ========== Alpha ==========
    private static final float BASE_ALPHA = 0.55f;

    // ========== Breathing ==========
    private static final float BREATHE_AMP   = 0.08f;
    private static final float BREATHE_SPEED = 1.5f;

    // ========== Color: civilization walls (warm amber-gold) ==========
    private static final float WALL_R = 0.90f, WALL_G = 0.78f, WALL_B = 0.50f;

    // ========== Color: head zone walls (amethyst / crystal purple) ==========
    private static final float HEAD_R = 0.68f, HEAD_G = 0.40f, HEAD_B = 0.88f;

    // ========== Texture ==========
    private static final Identifier FORCEFIELD_TEXTURE =
            Identifier.of("minecraft", "textures/misc/forcefield.png");

    // ========== GPU ==========
    private static final BufferAllocator ALLOCATOR = new BufferAllocator(262144);
    private static final BufferAllocator HEAD_ALLOCATOR = new BufferAllocator(65536);
    private static MappableRingBuffer vertexBuffer;
    private static MappableRingBuffer headVertexBuffer;

    // ========== State: civilization walls ==========

    /** Immutable snapshot of civilization faces + discovery timestamps. */
    private static volatile List<TimedFace> timedFaces = List.of();

    private static double wallMinY, wallMaxY;

    /** Per-face discovery time tracking. Only accessed from the network thread. */
    private static final HashMap<FaceId, Long> faceArrivalMap = new HashMap<>();

    // ========== State: head zone walls ==========

    /** Immutable snapshot of head zone faces + discovery timestamps. */
    private static volatile List<TimedHeadFace> timedHeadFaces = List.of();

    /** Per-face discovery time tracking for head zones. Only accessed from the network thread. */
    private static final HashMap<HeadFaceId, Long> headFaceArrivalMap = new HashMap<>();

    // ========== State: shared phase animation ==========

    /** Current rendered phase alpha [0..1], smoothly interpolated toward target each frame. */
    private static float phaseAlpha = 0.0f;

    /** Walls become visible after this nano timestamp (sonar delay). */
    private static long visibleAfterNano = 0;

    /** Steady phase ends at this nano timestamp → fade-out begins. Extended on renewal. */
    private static long steadyEndNano = 0;

    /** Previous frame nano timestamp for computing per-frame delta time. */
    private static long lastFrameNano = 0;

    // ========== Public API ==========

    public static void register() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(AuraWallRenderer::onRenderWorld);
    }

    /**
     * Called from the network thread when a new {@link SonarBoundaryPayload} arrives.
     * Updates both civilization and head zone face tracking.
     */
    public static void updateBoundaries(SonarBoundaryPayload payload) {
        long now = System.nanoTime();

        // Update geometry immediately — player may have moved.
        wallMinY = payload.wallMinY();
        wallMaxY = payload.wallMaxY();

        // Sonar delay always applies: shockwave is visual feedback for a new scan.
        visibleAfterNano = now + SONAR_DELAY_NS;
        steadyEndNano = visibleAfterNano + STEADY_DURATION_NS;

        // Fresh activation vs. renewal determines the arrival timestamp for new faces.
        boolean freshActivation = timedFaces.isEmpty() && timedHeadFaces.isEmpty();
        long newFaceArrival = freshActivation ? now : visibleAfterNano;

        if (freshActivation) {
            faceArrivalMap.clear();
            headFaceArrivalMap.clear();
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
                    // Face removed in this renewal → start fading out
                    civResult.add(new TimedFace(oldFace.face(), oldFace.arrivalNano(), now));
                } else if (oldFace.isFading() && newCivIds.contains(FaceId.of(oldFace.face()))) {
                    // Was fading but reappeared — already re-added above as non-fading
                } else if (oldFace.isFading()) {
                    // Still fading and not re-added — keep fading
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

        // ---- Head zone faces ----
        Set<HeadFaceId> newHeadIds = new HashSet<>();
        List<TimedHeadFace> headResult = new ArrayList<>(payload.headFaces().size());
        for (HeadZoneFaceData face : payload.headFaces()) {
            HeadFaceId id = HeadFaceId.of(face);
            newHeadIds.add(id);
            long arrival = headFaceArrivalMap.computeIfAbsent(id, k -> newFaceArrival);
            headResult.add(new TimedHeadFace(face, arrival));
        }
        // Retain removed head zone faces as fading-out (smooth disappearance)
        if (!freshActivation) {
            for (TimedHeadFace oldFace : timedHeadFaces) {
                HeadFaceId oldId = HeadFaceId.of(oldFace.face());
                if (!newHeadIds.contains(oldId) && !oldFace.isFading()) {
                    headResult.add(new TimedHeadFace(oldFace.face(), oldFace.arrivalNano(), now));
                } else if (oldFace.isFading() && newHeadIds.contains(HeadFaceId.of(oldFace.face()))) {
                    // Reappeared — already re-added above
                } else if (oldFace.isFading()) {
                    float fadeElapsed = (now - oldFace.fadeOutNano()) / 1_000_000_000f;
                    if (fadeElapsed < FACE_FADE_IN_S) {
                        headResult.add(oldFace);
                    }
                }
            }
        }
        headFaceArrivalMap.keySet().retainAll(newHeadIds);
        timedHeadFaces = List.copyOf(headResult);
    }

    public static void close() {
        ALLOCATOR.close();
        HEAD_ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        if (headVertexBuffer != null) {
            headVertexBuffer.close();
            headVertexBuffer = null;
        }
    }

    // ========== Render callback ==========

    private static void onRenderWorld(WorldRenderContext context) {
        SonarShockwaveEffect.tick();

        List<TimedFace> faces = timedFaces;
        List<TimedHeadFace> headFaces = timedHeadFaces;

        boolean hasFaces = !faces.isEmpty() || !headFaces.isEmpty();

        // Nothing to render and fully faded → early exit
        if (!hasFaces && phaseAlpha < 0.01f) return;

        // ---- Delta time ----
        long now = System.nanoTime();
        float dt = lastFrameNano > 0 ? (now - lastFrameNano) / 1_000_000_000f : 0.016f;
        dt = Math.min(dt, 0.1f); // Clamp to prevent huge jumps on lag spikes
        lastFrameNano = now;

        // ---- Determine target alpha (global phase) ----
        float targetAlpha;
        if (!hasFaces) {
            targetAlpha = 0.0f;
        } else if (now < visibleAfterNano) {
            // Sonar delay: fresh → invisible; renewal → hold brightness
            targetAlpha = phaseAlpha > 0.05f ? 1.0f : 0.0f;
        } else if (now < steadyEndNano) {
            targetAlpha = 1.0f; // Steady: fully visible
        } else {
            targetAlpha = 0.0f; // Past steady: fade out
        }

        // ---- Smoothly chase target ----
        if (phaseAlpha < targetAlpha) {
            phaseAlpha = Math.min(targetAlpha, phaseAlpha + FADE_IN_RATE * dt);
        } else if (phaseAlpha > targetAlpha) {
            phaseAlpha = Math.max(targetAlpha, phaseAlpha - FADE_OUT_RATE * dt);
        }

        // ---- Fully faded → cleanup ----
        if (phaseAlpha < 0.01f) {
            phaseAlpha = 0.0f;
            if (now > visibleAfterNano && now > steadyEndNano) {
                timedFaces = List.of();
                timedHeadFaces = List.of();
                lastFrameNano = 0;
            }
            return;
        }

        // ---- Breathing oscillation ----
        float breathe = 1.0f - BREATHE_AMP
                + BREATHE_AMP * (float) Math.sin(now / 1_000_000_000.0 * BREATHE_SPEED);
        float globalAlpha = BASE_ALPHA * phaseAlpha * breathe;

        // UV scroll via TextureMatrix
        float scroll = ((now / 1_000_000L) % 3000L) / 3000.0f;
        Matrix4f texMatrix = new Matrix4f().translation(scroll, scroll, 0);

        Vec3d cam = context.worldState().cameraRenderState.pos;

        // ---- Render civilization walls (gold) ----
        if (!faces.isEmpty()) {
            renderCivilizationWalls(faces, now, globalAlpha, texMatrix, cam);
        }

        // ---- Render head zone walls (amethyst) ----
        if (!headFaces.isEmpty()) {
            renderHeadZoneWalls(headFaces, now, globalAlpha, texMatrix, cam);
        }
    }

    // ========== Civilization wall rendering ==========

    private static void renderCivilizationWalls(List<TimedFace> faces, long now,
                                                 float globalAlpha, Matrix4f texMatrix, Vec3d cam) {
        var pipeline = RenderPipelines.RENDERTYPE_WORLD_BORDER;

        // Group faces into alpha buckets
        TreeMap<Integer, List<TimedFace>> buckets = new TreeMap<>();
        for (TimedFace tf : faces) {
            float faceAlpha;
            if (tf.isFading()) {
                // Fade-out: alpha decreases from 1→0 over FACE_FADE_IN_S
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

        // Build vertices
        BufferBuilder builder = new BufferBuilder(ALLOCATOR,
                pipeline.getVertexFormatMode(), pipeline.getVertexFormat());

        List<int[]> bucketRanges = new ArrayList<>();
        int totalQuads = 0;

        for (var entry : buckets.entrySet()) {
            int bucketLevel = entry.getKey();
            int bucketStartQuad = totalQuads;

            for (TimedFace tf : entry.getValue()) {
                BoundaryFaceData face = tf.face();
                double plane = face.planeCoord();
                double minU  = face.minU();
                double maxU  = minU + 16.0;

                float texU1 = (float) (minU    * 0.5);
                float texU2 = (float) (maxU    * 0.5);
                float texV1 = (float) (wallMinY * 0.5);
                float texV2 = (float) (wallMaxY * 0.5);

                if (face.axis() == 0) {
                    addQuadYZ(builder, cam, plane, minU, wallMinY, maxU, wallMaxY,
                              texU1, texV1, texU2, texV2, true);
                    addQuadYZ(builder, cam, plane, minU, wallMinY, maxU, wallMaxY,
                              texU1, texV1, texU2, texV2, false);
                } else {
                    addQuadXY(builder, cam, plane, minU, wallMinY, maxU, wallMaxY,
                              texU1, texV1, texU2, texV2, true);
                    addQuadXY(builder, cam, plane, minU, wallMinY, maxU, wallMaxY,
                              texU1, texV1, texU2, texV2, false);
                }
                totalQuads += 2;
            }

            int quadsInBucket = totalQuads - bucketStartQuad;
            if (quadsInBucket > 0) {
                bucketRanges.add(new int[]{bucketLevel, bucketStartQuad, quadsInBucket});
            }
        }

        if (totalQuads == 0) return;

        drawBucketedWalls(builder, bucketRanges, totalQuads, globalAlpha,
                WALL_R, WALL_G, WALL_B, texMatrix, true);
    }

    // ========== Head zone wall rendering ==========

    private static void renderHeadZoneWalls(List<TimedHeadFace> faces, long now,
                                             float globalAlpha, Matrix4f texMatrix, Vec3d cam) {
        var pipeline = RenderPipelines.RENDERTYPE_WORLD_BORDER;

        // Group faces into alpha buckets
        TreeMap<Integer, List<TimedHeadFace>> buckets = new TreeMap<>();
        for (TimedHeadFace tf : faces) {
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

        // Build vertices — each face has its own faceMinY/faceMaxY
        BufferBuilder builder = new BufferBuilder(HEAD_ALLOCATOR,
                pipeline.getVertexFormatMode(), pipeline.getVertexFormat());

        List<int[]> bucketRanges = new ArrayList<>();
        int totalQuads = 0;

        for (var entry : buckets.entrySet()) {
            int bucketLevel = entry.getKey();
            int bucketStartQuad = totalQuads;

            for (TimedHeadFace tf : entry.getValue()) {
                HeadZoneFaceData face = tf.face();
                double plane = face.planeCoord();
                double minU  = face.minU();
                double maxU  = minU + 16.0;
                double fMinY = face.faceMinY();
                double fMaxY = face.faceMaxY();

                float texU1 = (float) (minU  * 0.5);
                float texU2 = (float) (maxU  * 0.5);
                float texV1 = (float) (fMinY * 0.5);
                float texV2 = (float) (fMaxY * 0.5);

                if (face.axis() == 0) {
                    addQuadYZ(builder, cam, plane, minU, fMinY, maxU, fMaxY,
                              texU1, texV1, texU2, texV2, true);
                    addQuadYZ(builder, cam, plane, minU, fMinY, maxU, fMaxY,
                              texU1, texV1, texU2, texV2, false);
                } else {
                    addQuadXY(builder, cam, plane, minU, fMinY, maxU, fMaxY,
                              texU1, texV1, texU2, texV2, true);
                    addQuadXY(builder, cam, plane, minU, fMinY, maxU, fMaxY,
                              texU1, texV1, texU2, texV2, false);
                }
                totalQuads += 2;
            }

            int quadsInBucket = totalQuads - bucketStartQuad;
            if (quadsInBucket > 0) {
                bucketRanges.add(new int[]{bucketLevel, bucketStartQuad, quadsInBucket});
            }
        }

        if (totalQuads == 0) return;

        drawBucketedWalls(builder, bucketRanges, totalQuads, globalAlpha,
                HEAD_R, HEAD_G, HEAD_B, texMatrix, false);
    }

    // ========== Shared bucketed draw ==========

    /**
     * Upload vertices and draw each alpha bucket in its own RenderPass.
     *
     * @param builder       buffer builder with all vertices
     * @param bucketRanges  list of [bucketLevel, firstQuad, quadCount]
     * @param totalQuads    total number of quads in the buffer
     * @param globalAlpha   global alpha (base × phase × breathe)
     * @param r, g, b       wall color
     * @param texMatrix     texture scroll matrix
     * @param usePrimaryBuf true for civilization buffer, false for head zone buffer
     */
    private static void drawBucketedWalls(BufferBuilder builder, List<int[]> bucketRanges,
                                           int totalQuads, float globalAlpha,
                                           float r, float g, float b,
                                           Matrix4f texMatrix, boolean usePrimaryBuf) {
        var pipeline = RenderPipelines.RENDERTYPE_WORLD_BORDER;

        BuiltBuffer built = builder.endNullable();
        if (built == null) return;

        try {
            BuiltBuffer.DrawParameters drawParams = built.getDrawParameters();
            VertexFormat format = drawParams.format();
            int totalBytes = drawParams.vertexCount() * format.getVertexSize();

            // Select or create the appropriate ring buffer
            MappableRingBuffer ringBuf;
            if (usePrimaryBuf) {
                if (vertexBuffer == null || vertexBuffer.size() < totalBytes) {
                    if (vertexBuffer != null) vertexBuffer.close();
                    vertexBuffer = new MappableRingBuffer(
                            () -> "civil aura wall",
                            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                            Math.max(totalBytes, 16384));
                }
                ringBuf = vertexBuffer;
            } else {
                if (headVertexBuffer == null || headVertexBuffer.size() < totalBytes) {
                    if (headVertexBuffer != null) headVertexBuffer.close();
                    headVertexBuffer = new MappableRingBuffer(
                            () -> "civil head zone wall",
                            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                            Math.max(totalBytes, 8192));
                }
                ringBuf = headVertexBuffer;
            }

            GpuBuffer gpuVerts = ringBuf.getBlocking();
            var uploadEncoder = RenderSystem.getDevice().createCommandEncoder();
            try (GpuBuffer.MappedView mapped = uploadEncoder.mapBuffer(
                    gpuVerts.slice(0, built.getBuffer().remaining()), false, true)) {
                MemoryUtil.memCopy(built.getBuffer(), mapped.data());
            }

            // Shared index buffer
            int totalIndices = totalQuads * 6;
            RenderSystem.ShapeIndexBuffer shapeIdxBuf =
                    RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            GpuBuffer indices = shapeIdxBuf.getIndexBuffer(totalIndices);
            VertexFormat.IndexType indexType = shapeIdxBuf.getIndexType();

            MinecraftClient client = MinecraftClient.getInstance();
            AbstractTexture forcefield = client.getTextureManager().getTexture(FORCEFIELD_TEXTURE);
            var fb = client.getFramebuffer();

            // Draw each alpha bucket
            for (int[] range : bucketRanges) {
                int bucketLevel = range[0];
                int firstQuad   = range[1];
                int quadCount   = range[2];

                float faceAlpha  = bucketLevel / 10.0f;
                float finalAlpha = globalAlpha * faceAlpha;
                if (finalAlpha < 0.005f) continue;

                Vector4f color = new Vector4f(r, g, b, finalAlpha);

                GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                        .write(RenderSystem.getModelViewMatrix(), color, new Vector3f(), texMatrix);

                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                        .createRenderPass(
                                () -> usePrimaryBuf ? "civil aura wall" : "civil head zone wall",
                                fb.getColorAttachmentView(), OptionalInt.empty(),
                                fb.getDepthAttachmentView(), OptionalDouble.empty())) {
                    pass.setPipeline(pipeline);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", dynamicTransforms);
                    pass.bindTexture("Sampler0",
                            forcefield.getGlTextureView(), forcefield.getSampler());
                    pass.setVertexBuffer(0, gpuVerts);
                    pass.setIndexBuffer(indices, indexType);
                    pass.drawIndexed(0, firstQuad * 6, quadCount * 6, 1);
                }
            }

            ringBuf.rotate();
        } finally {
            built.close();
        }
    }

    // ========== Vertex emission ==========

    // ---- YZ-plane quad (X axis boundary) ----
    private static void addQuadYZ(BufferBuilder buf, Vec3d cam,
                                  double x, double minZ, double minY, double maxZ, double maxY,
                                  float u1, float v1, float u2, float v2,
                                  boolean front) {
        float fx  = (float) (x    - cam.x);
        float fy1 = (float) (minY - cam.y);
        float fy2 = (float) (maxY - cam.y);
        float fz1 = (float) (minZ - cam.z);
        float fz2 = (float) (maxZ - cam.z);

        if (front) {
            buf.vertex(fx, fy1, fz1).texture(u1, v1);
            buf.vertex(fx, fy1, fz2).texture(u2, v1);
            buf.vertex(fx, fy2, fz2).texture(u2, v2);
            buf.vertex(fx, fy2, fz1).texture(u1, v2);
        } else {
            buf.vertex(fx, fy2, fz1).texture(u1, v2);
            buf.vertex(fx, fy2, fz2).texture(u2, v2);
            buf.vertex(fx, fy1, fz2).texture(u2, v1);
            buf.vertex(fx, fy1, fz1).texture(u1, v1);
        }
    }

    // ---- XY-plane quad (Z axis boundary) ----
    private static void addQuadXY(BufferBuilder buf, Vec3d cam,
                                  double z, double minX, double minY, double maxX, double maxY,
                                  float u1, float v1, float u2, float v2,
                                  boolean front) {
        float fx1 = (float) (minX - cam.x);
        float fx2 = (float) (maxX - cam.x);
        float fy1 = (float) (minY - cam.y);
        float fy2 = (float) (maxY - cam.y);
        float fz  = (float) (z    - cam.z);

        if (front) {
            buf.vertex(fx1, fy1, fz).texture(u1, v1);
            buf.vertex(fx2, fy1, fz).texture(u2, v1);
            buf.vertex(fx2, fy2, fz).texture(u2, v2);
            buf.vertex(fx1, fy2, fz).texture(u1, v2);
        } else {
            buf.vertex(fx1, fy2, fz).texture(u1, v2);
            buf.vertex(fx2, fy2, fz).texture(u2, v2);
            buf.vertex(fx2, fy1, fz).texture(u2, v1);
            buf.vertex(fx1, fy1, fz).texture(u1, v1);
        }
    }
}
