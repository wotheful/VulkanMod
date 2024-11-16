package net.vulkanmod.render.chunk.buffer;

import net.minecraft.world.phys.Vec3;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.render.chunk.ChunkArea;
import net.vulkanmod.render.chunk.ChunkAreaManager;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.cull.QuadFacing;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.IndirectBuffer;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.util.EnumMap;

import static org.lwjgl.vulkan.VK10.*;

public class DrawBuffers {
    private static final int VERTEX_SIZE = PipelineManager.terrainVertexFormat.getVertexSize();
    private static final int INDEX_SIZE = Short.BYTES;

    private static final int CMD_STRIDE = 32;

    private static final long cmdBufferPtr = MemoryUtil.nmemAlignedAlloc(CMD_STRIDE, (long) ChunkAreaManager.AREA_SIZE * QuadFacing.COUNT * CMD_STRIDE);

    private final int index;
    private final Vector3i origin;
    private final int minHeight;

    private boolean allocated = false;
    AreaBuffer indexBuffer;
    private final EnumMap<TerrainRenderType, AreaBuffer> vertexBuffers = new EnumMap<>(TerrainRenderType.class);

    //Need ugly minHeight Parameter to fix custom world heights (exceeding 384 Blocks in total)
    public DrawBuffers(int index, Vector3i origin, int minHeight) {
        this.index = index;
        this.origin = origin;
        this.minHeight = minHeight;
    }

    public void upload(RenderSection section, UploadBuffer buffer, TerrainRenderType renderType) {
        var vertexBuffers = buffer.getVertexBuffers();

        if (buffer.indexOnly) {
            DrawParameters drawParameters = section.getDrawParameters(renderType, QuadFacing.UNDEFINED.ordinal());

            AreaBuffer.Segment segment = this.indexBuffer.upload(buffer.getIndexBuffer(), drawParameters.firstIndex, drawParameters);
            drawParameters.firstIndex = segment.offset / INDEX_SIZE;

            buffer.release();
            return;
        }

        for (int i = 0; i < QuadFacing.COUNT; i++) {
            DrawParameters drawParameters = section.getDrawParameters(renderType, i);
            int vertexOffset = drawParameters.vertexOffset;
            int firstIndex = -1;
            int indexCount = 0;

            var vertexBuffer = vertexBuffers[i];

            if (vertexBuffer != null) {
                AreaBuffer.Segment segment = this.getAreaBufferOrAlloc(renderType).upload(vertexBuffer, vertexOffset, drawParameters);
                vertexOffset = segment.offset / VERTEX_SIZE;

                drawParameters.baseInstance = encodeSectionOffset(section.xOffset(), section.yOffset(), section.zOffset());
                indexCount = vertexBuffer.limit() / VERTEX_SIZE * 6 / 4;
            }

		if (i == QuadFacing.UNDEFINED.ordinal() && !buffer.autoIndices) {
			if (this.indexBuffer == null) {
                this.indexBuffer = new AreaBuffer(AreaBuffer.Usage.INDEX, 60000, INDEX_SIZE);
            }

                AreaBuffer.Segment segment = this.indexBuffer.upload(buffer.getIndexBuffer(), drawParameters.firstIndex, drawParameters);
                firstIndex = segment.offset / INDEX_SIZE;
            }

            drawParameters.firstIndex = firstIndex;
            drawParameters.vertexOffset = vertexOffset;
            drawParameters.indexCount = indexCount;
        }

        buffer.release();
    }

    private AreaBuffer getAreaBufferOrAlloc(TerrainRenderType renderType) {
        this.allocated = true;

        int initialSize = switch (renderType) {
            case SOLID, CUTOUT -> 100000;
            case CUTOUT_MIPPED -> 250000;
            case TRANSLUCENT, TRIPWIRE -> 60000;
        };

        return this.vertexBuffers.computeIfAbsent(
                renderType, renderType1 -> new AreaBuffer(AreaBuffer.Usage.VERTEX, initialSize, VERTEX_SIZE));
    }

    public AreaBuffer getAreaBuffer(TerrainRenderType r) {
        return this.vertexBuffers.get(r);
    }

    private boolean hasRenderType(TerrainRenderType r) {
        return this.vertexBuffers.containsKey(r);
    }

    private int encodeSectionOffset(int xOffset, int yOffset, int zOffset) {
        final int xOffset1 = (xOffset & 127);
        final int zOffset1 = (zOffset & 127);
        final int yOffset1 = (yOffset - this.minHeight & 127);
        return yOffset1 << 16 | zOffset1 << 8 | xOffset1;
    }

    // TODO: refactor
    public static final float POS_OFFSET = PipelineManager.terrainVertexFormat == CustomVertexFormat.COMPRESSED_TERRAIN ? 4.0f : 0.0f;

    private void updateChunkAreaOrigin(VkCommandBuffer commandBuffer, Pipeline pipeline, double camX, double camY, double camZ, MemoryStack stack) {
        float xOffset = (float) ((this.origin.x) + POS_OFFSET - camX);
        float yOffset = (float) ((this.origin.y) + POS_OFFSET - camY);
        float zOffset = (float) ((this.origin.z) + POS_OFFSET - camZ);

        ByteBuffer byteBuffer = stack.malloc(12);

        byteBuffer.putFloat(0, xOffset);
        byteBuffer.putFloat(4, yOffset);
        byteBuffer.putFloat(8, zOffset);

        vkCmdPushConstants(commandBuffer, pipeline.getLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, byteBuffer);
    }

    public void buildDrawBatchesIndirect(Vec3 cameraPos, IndirectBuffer indirectBuffer, StaticQueue<RenderSection> queue, TerrainRenderType terrainRenderType) {
        long bufferPtr = cmdBufferPtr;

        boolean isTranslucent = terrainRenderType == TerrainRenderType.TRANSLUCENT;

        int drawCount = 0;
        for (var iterator = queue.iterator(isTranslucent); iterator.hasNext(); ) {

            final RenderSection section = iterator.next();

            int mask = getMask(cameraPos, section);

            for (int i = 0; i < QuadFacing.COUNT; i++) {

                if ((mask & 1 << i) == 0)
                    continue;

                final DrawParameters drawParameters = section.getDrawParameters(terrainRenderType, i);

                if (drawParameters.indexCount <= 0)
                    continue;

                long ptr = bufferPtr + ((long) drawCount * CMD_STRIDE);
                MemoryUtil.memPutInt(ptr, drawParameters.indexCount);
                MemoryUtil.memPutInt(ptr + 4, 1);
                MemoryUtil.memPutInt(ptr + 8, drawParameters.firstIndex == -1 ? 0 : drawParameters.firstIndex);
                MemoryUtil.memPutInt(ptr + 12, drawParameters.vertexOffset);
                MemoryUtil.memPutInt(ptr + 16, drawParameters.baseInstance);

                drawCount++;
            }
        }

        if (drawCount == 0)
            return;

        ByteBuffer byteBuffer = MemoryUtil.memByteBuffer(cmdBufferPtr, queue.size() * QuadFacing.COUNT * CMD_STRIDE);
        indirectBuffer.recordCopyCmd(byteBuffer.position(0));

        vkCmdDrawIndexedIndirect(Renderer.getCommandBuffer(), indirectBuffer.getId(), indirectBuffer.getOffset(), drawCount, CMD_STRIDE);
    }

    public void buildDrawBatchesDirect(Vec3 cameraPos, StaticQueue<RenderSection> queue, TerrainRenderType renderType) {
        boolean isTranslucent = renderType == TerrainRenderType.TRANSLUCENT;
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();

        for (var iterator = queue.iterator(isTranslucent); iterator.hasNext(); ) {
            final RenderSection section = iterator.next();

            int mask = getMask(cameraPos, section);

            for (int i = 0; i < QuadFacing.COUNT; i++) {

                if((mask & 1 << i) == 0)
                    continue;

                final DrawParameters drawParameters = section.getDrawParameters(renderType, i);

                if (drawParameters.indexCount <= 0)
                    continue;

                final int firstIndex = drawParameters.firstIndex == -1 ? 0 : drawParameters.firstIndex;
                vkCmdDrawIndexed(commandBuffer, drawParameters.indexCount, 1, firstIndex, drawParameters.vertexOffset, drawParameters.baseInstance);
            }

        }
    }

    private int getMask(Vec3 camera, RenderSection section) {
        final int secX = section.xOffset;
        final int secY = section.yOffset;
        final int secZ = section.zOffset;

        int mask = 1 << QuadFacing.UNDEFINED.ordinal();

        mask |= camera.x - secX >= 0 ? 1 << QuadFacing.X_POS.ordinal() : 0;
        mask |= camera.y - secY >= 0 ? 1 << QuadFacing.Y_POS.ordinal() : 0;
        mask |= camera.z - secZ >= 0 ? 1 << QuadFacing.Z_POS.ordinal() : 0;
        mask |= camera.x - (secX + 16) < 0 ? 1 << QuadFacing.X_NEG.ordinal() : 0;
        mask |= camera.y - (secY + 16) < 0 ? 1 << QuadFacing.Y_NEG.ordinal() : 0;
        mask |= camera.z - (secZ + 16) < 0 ? 1 << QuadFacing.Z_NEG.ordinal() : 0;

        return mask;
    }

    public void bindBuffers(VkCommandBuffer commandBuffer, Pipeline pipeline, TerrainRenderType terrainRenderType, double camX, double camY, double camZ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var vertexBuffer = getAreaBuffer(terrainRenderType);
            nvkCmdBindVertexBuffers(commandBuffer, 0, 1, stack.npointer(vertexBuffer.getId()), stack.npointer(0));
            updateChunkAreaOrigin(commandBuffer, pipeline, camX, camY, camZ, stack);
        }

        if (terrainRenderType == TerrainRenderType.TRANSLUCENT && this.indexBuffer != null) {
            vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
        }
    }

    public void releaseBuffers() {
        if (!this.allocated)
            return;

        this.vertexBuffers.values().forEach(AreaBuffer::freeBuffer);
        this.vertexBuffers.clear();

        if (this.indexBuffer != null)
            this.indexBuffer.freeBuffer();
        this.indexBuffer = null;

        this.allocated = false;
    }

    public boolean isAllocated() {
        return !this.vertexBuffers.isEmpty();
    }

    public EnumMap<TerrainRenderType, AreaBuffer> getVertexBuffers() {
        return vertexBuffers;
    }

    public AreaBuffer getIndexBuffer() {
        return indexBuffer;
    }

    public static class DrawParameters {
        int indexCount = 0;
        int firstIndex = -1;
        int vertexOffset = -1;
        int baseInstance;

        public DrawParameters() {}

        public void reset(ChunkArea chunkArea, TerrainRenderType r) {
            AreaBuffer areaBuffer = chunkArea.getDrawBuffers().getAreaBuffer(r);
            if (areaBuffer != null && this.vertexOffset != -1) {
                int segmentOffset = this.vertexOffset * VERTEX_SIZE;
                areaBuffer.setSegmentFree(segmentOffset);
            }

            this.indexCount = 0;
            this.firstIndex = -1;
            this.vertexOffset = -1;
        }
    }

}
