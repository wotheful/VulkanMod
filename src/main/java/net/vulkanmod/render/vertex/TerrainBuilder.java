package net.vulkanmod.render.vertex;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.render.chunk.cull.QuadFacing;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class TerrainBuilder {
    private static final Logger LOGGER = Initializer.LOGGER;
    private static final MemoryUtil.MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);

    protected long indexBufferPtr;

    private int indexBufferCapacity;
    protected long bufferPtr;

    private final VertexFormat format;

    private boolean building;

    private final QuadSorter quadSorter = new QuadSorter();

    private boolean needsSorting;
    private boolean indexOnly;

    protected VertexBuilder vertexBuilder;

    private final TerrainBufferBuilder[] bufferBuilders;

    public TerrainBuilder(int size) {
        // TODO index buffer
        this.indexBufferPtr = ALLOCATOR.malloc(size);
        this.indexBufferCapacity = size;

        this.format = PipelineManager.terrainVertexFormat;
        this.vertexBuilder = PipelineManager.terrainVertexFormat == CustomVertexFormat.COMPRESSED_TERRAIN
                ? new VertexBuilder.CompressedVertexBuilder() : new VertexBuilder.DefaultVertexBuilder();

        var bufferBuilders = new TerrainBufferBuilder[QuadFacing.COUNT];
        for (int i = 0; i < QuadFacing.COUNT; i++) {
            bufferBuilders[i] = new TerrainBufferBuilder(size, this.format.getVertexSize(), this.vertexBuilder);
        }

        this.bufferBuilders = bufferBuilders;
    }

    public TerrainBufferBuilder getBufferBuilder(int i) {
        return this.bufferBuilders[i];
    }

    private void ensureIndexCapacity(int size) {
        if (size > this.indexBufferCapacity) {
            int capacity = this.indexBufferCapacity;
            int newSize = (capacity + size) * 2;
            this.resizeIndexBuffer(newSize);
        }
    }

    private void resizeIndexBuffer(int i) {
        this.bufferPtr = ALLOCATOR.realloc(this.bufferPtr, i);
        LOGGER.debug("Needed to grow index buffer: Old size {} bytes, new size {} bytes.", this.indexBufferCapacity, i);
        if (this.bufferPtr == 0L) {
            throw new OutOfMemoryError("Failed to resize buffer from " + this.indexBufferCapacity + " bytes to " + i + " bytes");
        } else {
            this.indexBufferCapacity = i;
        }
    }

    public void setupQuadSorting(float x, float y, float z) {
        this.quadSorter.setQuadSortOrigin(x, y, z);
        this.needsSorting = true;
    }

    public QuadSorter.SortState getSortState() {
        return this.quadSorter.getSortState();
    }

    public void restoreSortState(QuadSorter.SortState sortState) {
        this.quadSorter.restoreSortState(sortState);

        this.indexOnly = true;
    }

    public void setIndexOnly() {
        this.indexOnly = true;
    }

    public void begin() {
        if (this.building) {
            throw new IllegalStateException("Already building!");
        } else {
            this.building = true;
        }
    }

    public void setupQuadSortingPoints() {
        TerrainBufferBuilder bufferBuilder = bufferBuilders[QuadFacing.UNDEFINED.ordinal()];
        long bufferPtr = bufferBuilder.getPtr();
        int vertexCount = bufferBuilder.getVertices();

        this.quadSorter.setupQuadSortingPoints(bufferPtr, vertexCount, this.format);
    }

    public DrawState endDrawing() {
        for (TerrainBufferBuilder bufferBuilder : this.bufferBuilders) {
            bufferBuilder.end();
        }

        int vertexCount = this.quadSorter.getVertexCount();

        int indexCount = vertexCount / 4 * 6;

        VertexFormat.IndexType indexType = VertexFormat.IndexType.least(indexCount);
        boolean sequentialIndexing;

        // TODO sorting
        if (this.needsSorting) {
            int indexBufferSize = indexCount * indexType.bytes;
            this.ensureIndexCapacity(indexBufferSize);

            this.quadSorter.putSortedQuadIndices(this, indexType);

            sequentialIndexing = false;
        } else {
            sequentialIndexing = true;
        }

        return new DrawState(this.format.getVertexSize(), indexCount, indexType, this.indexOnly, sequentialIndexing);
    }

    // TODO hardcoded index type size
    public ByteBuffer getIndexBuffer() {
        int indexCount = this.quadSorter.getVertexCount() * 6 / 4;

        return MemoryUtil.memByteBuffer(this.indexBufferPtr, indexCount * 2);
    }

    private void ensureDrawing() {
        if (!this.building) {
            throw new IllegalStateException("Not building!");
        }
    }

    public void reset() {
        this.building = false;

        this.indexOnly = false;
        this.needsSorting = false;
    }

    public void clear() {
        this.reset();

        for (TerrainBufferBuilder bufferBuilder : this.bufferBuilders) {
            bufferBuilder.clear();
        }
    }

    public void setBlockAttributes(BlockState blockState) {
    }

    public record DrawState(int vertexSize, int indexCount, VertexFormat.IndexType indexType,
                            boolean indexOnly, boolean sequentialIndex) {

        private int indexBufferSize() {
            return this.sequentialIndex ? 0 : this.indexCount * this.indexType.bytes;
        }

        public int indexCount() {
            return this.indexCount;
        }

        public VertexFormat.IndexType indexType() {
            return this.indexType;
        }

        public boolean indexOnly() {
            return this.indexOnly;
        }

        public boolean sequentialIndex() {
            return this.sequentialIndex;
        }
    }
}
