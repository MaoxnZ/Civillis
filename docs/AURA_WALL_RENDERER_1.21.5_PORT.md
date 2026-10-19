# AuraWallRenderer: 1.21.11 → 1.21.5 porting guide

This document answers the API differences when porting the RenderPass-based AuraWallRenderer from MC 1.21.11 to 1.21.5.

---

## 1. WorldRenderEvents package and event in Fabric API 1.21.5

- **Package**: In 1.21.5, `WorldRenderEvents` and `WorldRenderContext` live in **`net.fabricmc.fabric.api.client.rendering.v1`** (no `.world` subpackage).
  - Use: `net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents`
  - Use: `net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext`

- **Event**: **`BEFORE_TRANSLUCENT` does not exist in Fabric API 1.21.5.** The available events (in order) are: START, AFTER_SETUP, BEFORE_ENTITIES, AFTER_ENTITIES, BEFORE_BLOCK_OUTLINE, BLOCK_OUTLINE, BEFORE_DEBUG_RENDER, AFTER_TRANSLUCENT, LAST, END.

  For “before translucency” timing, use **`WorldRenderEvents.BEFORE_DEBUG_RENDER`** (runs before translucency is drawn). Alternatively **`AFTER_ENTITIES`** or **`AFTER_TRANSLUCENT`** if you need a different phase.

---

## 2. Vertex buffers in 1.21.5 (no MappableRingBuffer / GpuBufferSlice)

- **`MappableRingBuffer`** and **`GpuBufferSlice`** do not exist in 1.21.5.

- **Create + upload in one go** using:
  - `GpuDevice.createBuffer(Supplier<String> labelGetter, BufferType type, BufferUsage usage, ByteBuffer source)`
  - `BufferType.VERTICES`, `BufferUsage.STREAM_WRITE` (or `DYNAMIC_WRITE`).
  - Build vertices with `BufferBuilder` → `BuiltBuffer` → `built.getBuffer()` (ByteBuffer). Pass that ByteBuffer as `source`; the device will create a GPU buffer and upload the data.

- **Alternative: create then upload**
  - `GpuDevice.createBuffer(..., BufferType.VERTICES, BufferUsage.STREAM_WRITE, size)` to create an empty buffer.
  - `CommandEncoder.writeToBuffer(GpuBuffer target, ByteBuffer source, int offset)` to upload (same encoder used for the render pass).

- **No ring buffer**: create a new `GpuBuffer` per frame (or per `drawBucketedWalls` call), use it, then **`close()`** it when done so it can be freed.

- **BufferType** and **BufferUsage** are in **`com.mojang.blaze3d.buffers`** (no `GpuBuffer.USAGE_*`; use the enums).

---

## 3. Binding textures and uniforms with RenderPass in 1.21.5

- **Textures**: `RenderPass.bindSampler(String name, GpuTexture texture)` takes a **single `GpuTexture`**. There is no separate “view” and “sampler” in 1.21.5.

- **Uniforms**: There is no **`RenderSystem.getDynamicUniforms()`** or **`RenderSystem.bindDefaultUniforms(pass)`** in 1.21.5. Set uniforms explicitly on the pass:
  - `pass.setUniform(String name, Matrix4f matrix)`
  - `pass.setUniform(String name, float[] values)`
  - `pass.setUniform(String name, int[] values)`

  For `RENDERTYPE_WORLD_BORDER`-style pipelines, typical names are **`ProjMat`**, **`ModelViewMat`**, **`TextureMat`**, **`ColorModulator`**, **`ModelOffset`**. Use `RenderSystem.getProjectionMatrix()`, `getModelViewMatrix()`, `getTextureMatrix()`, `getShaderColor()`, `getModelOffset()` and pass them via `setUniform` (and a float[] for color).

---

## 4. RenderPass.drawIndexed() in 1.21.5

- In 1.21.5 the signature is **two arguments**: **`drawIndexed(int offset, int count)`** (index offset and index count).
- Example: `pass.drawIndexed(firstQuad * 6, quadCount * 6)`.

---

## 5. Framebuffer “views” for RenderPass in 1.21.5

- In 1.21.5, **`Framebuffer`** does not have **`getColorAttachmentView()`** / **`getDepthAttachmentView()`**.
- It has **`getColorAttachment()`** and **`getDepthAttachment()`**, which return **`GpuTexture`** (not a “view” type).

- **Create the render pass** with:
  - `CommandEncoder.createRenderPass(GpuTexture colorAttachment, OptionalInt clearColor, @Nullable GpuTexture depthAttachment, OptionalDouble clearDepth)`
  - Example: `encoder.createRenderPass(fb.getColorAttachment(), OptionalInt.empty(), fb.getDepthAttachment(), OptionalDouble.empty())`.

---

## 6. Texture “view” and “sampler” for binding in 1.21.5

- **`AbstractTexture.getGlTextureView()`** and **`AbstractTexture.getSampler()`** do not exist in 1.21.5.
- **`AbstractTexture.getGlTexture()`** exists and returns **`GpuTexture`**.
- Use that single **`GpuTexture`** when binding: **`pass.bindSampler("Sampler0", texture.getGlTexture())`**.

---

## 7. WorldRenderContext camera position in 1.21.5

- **`context.worldState().cameraRenderState.pos`** does not exist in 1.21.5.
- Use **`context.camera().getPos()`** (returns `Vec3d`) for the camera position.

---

## 8. Tessellator / BufferBuilder with RenderPipelines in 1.21.5

- **`BufferBuilder`** and **`BufferAllocator`** exist and work in 1.21.5. You still build vertices with **`BufferBuilder`** (vertex format and draw mode from the pipeline), then call **`builder.endNullable()`** to get **`BuiltBuffer`**.
- The **simpler approach** that works in 1.21.5 is: build with **BufferBuilder** → get **ByteBuffer** from **`BuiltBuffer.getBuffer()`** → create a **GpuBuffer** with **`GpuDevice.createBuffer(..., ByteBuffer source)`** → use that **GpuBuffer** in **RenderPass** with **`setVertexBuffer`** / **`setIndexBuffer`** / **`drawIndexed`**. No Tessellator singleton is required for this path; the pipeline’s vertex format is used on the existing **BufferBuilder** as in your current code.

---

## Summary table

| 1.21.11 | 1.21.5 |
|--------|--------|
| `v1.world.WorldRenderContext` / `WorldRenderEvents` | `v1.WorldRenderContext` / `WorldRenderEvents` |
| `WorldRenderEvents.BEFORE_TRANSLUCENT` | `WorldRenderEvents.BEFORE_DEBUG_RENDER` (or AFTER_ENTITIES / AFTER_TRANSLUCENT) |
| `MappableRingBuffer` + `GpuBuffer.slice()` + `mapBuffer` | `GpuDevice.createBuffer(..., ByteBuffer source)` or createBuffer(size) + `writeToBuffer` |
| `GpuBuffer.USAGE_VERTEX \| USAGE_MAP_WRITE` | `BufferType.VERTICES`, `BufferUsage.STREAM_WRITE` |
| `getDynamicUniforms()` / `bindDefaultUniforms(pass)` | Set each uniform with `pass.setUniform(name, matrix)` / `setUniform(name, float[])` |
| `pass.drawIndexed(0, firstQuad*6, quadCount*6, 1)` (4 args) | `pass.drawIndexed(firstQuad*6, quadCount*6)` (2 args) |
| `fb.getColorAttachmentView()` / `getDepthAttachmentView()` | `fb.getColorAttachment()` / `getDepthAttachment()` (return GpuTexture) |
| `forcefield.getGlTextureView()`, `getSampler()` | `forcefield.getGlTexture()` (single GpuTexture), `pass.bindSampler("Sampler0", texture)` |
| `context.worldState().cameraRenderState.pos` | `context.camera().getPos()` |
