package net.vulkanmod.render.vertex;

import org.lwjgl.system.MemoryUtil;

public interface VertexBuilder {
    void vertex(long ptr, float x, float y, float z, int color, float u, float v, int light, int packedNormal);

    void position(long ptr, float x, float y, float z);

    void color(long ptr, int color);

    void uv(long ptr, float u, float v);

    void light(long ptr, int light);

    void normal(long ptr, int normal);

    int getStride();

    class DefaultVertexBuilder implements VertexBuilder {
        private static final int VERTEX_SIZE = 32;

        public void vertex(long ptr, float x, float y, float z, int color, float u, float v, int light, int packedNormal) {
            MemoryUtil.memPutFloat(ptr + 0, x);
            MemoryUtil.memPutFloat(ptr + 4, y);
            MemoryUtil.memPutFloat(ptr + 8, z);

            MemoryUtil.memPutInt(ptr + 12, color);

            MemoryUtil.memPutFloat(ptr + 16, u);
            MemoryUtil.memPutFloat(ptr + 20, v);

            MemoryUtil.memPutShort(ptr + 24, (short) (light & '\uffff'));
            MemoryUtil.memPutShort(ptr + 26, (short) (light >> 16 & '\uffff'));

            MemoryUtil.memPutInt(ptr + 28, packedNormal);
        }

        // TODO
        @Override
        public void position(long ptr, float x, float y, float z) {

        }

        @Override
        public void color(long ptr, int color) {

        }

        @Override
        public void uv(long ptr, float u, float v) {

        }

        @Override
        public void light(long ptr, int light) {

        }

        @Override
        public void normal(long ptr, int normal) {

        }

        @Override
        public int getStride() {
            return VERTEX_SIZE;
        }
    }

    class CompressedVertexBuilder implements VertexBuilder {
        private static final int VERTEX_SIZE = 20;

        public static final float POS_CONV_MUL = 2048.0f;
        public static final float POS_OFFSET = -4.0f;
        public static final float POS_OFFSET_CONV = POS_OFFSET * POS_CONV_MUL;

        public static final float UV_CONV_MUL = 32768.0f;

        public void vertex(long ptr, float x, float y, float z, int color, float u, float v, int light, int packedNormal) {
            final short sX = (short) (x * POS_CONV_MUL + POS_OFFSET_CONV);
            final short sY = (short) (y * POS_CONV_MUL + POS_OFFSET_CONV);
            final short sZ = (short) (z * POS_CONV_MUL + POS_OFFSET_CONV);

            MemoryUtil.memPutShort(ptr + 0, sX);
            MemoryUtil.memPutShort(ptr + 2, sY);
            MemoryUtil.memPutShort(ptr + 4, sZ);

            final short l = (short) (((light >>> 8) & 0xFF00) | (light & 0xFF));
            MemoryUtil.memPutShort(ptr + 6, l);

            MemoryUtil.memPutInt(ptr + 8, color);

            MemoryUtil.memPutShort(ptr + 12, (short) (u * UV_CONV_MUL));
            MemoryUtil.memPutShort(ptr + 14, (short) (v * UV_CONV_MUL));
        }

        @Override
        public void position(long ptr, float x, float y, float z) {
            final short sX = (short) (x * POS_CONV_MUL + POS_OFFSET_CONV);
            final short sY = (short) (y * POS_CONV_MUL + POS_OFFSET_CONV);
            final short sZ = (short) (z * POS_CONV_MUL + POS_OFFSET_CONV);

            MemoryUtil.memPutShort(ptr + 0, sX);
            MemoryUtil.memPutShort(ptr + 2, sY);
            MemoryUtil.memPutShort(ptr + 4, sZ);
        }

        @Override
        public void color(long ptr, int color) {
            MemoryUtil.memPutInt(ptr + 8, color);
        }

        @Override
        public void uv(long ptr, float u, float v) {
            MemoryUtil.memPutShort(ptr + 12, (short) (u * UV_CONV_MUL));
            MemoryUtil.memPutShort(ptr + 14, (short) (v * UV_CONV_MUL));
        }

        @Override
        public void light(long ptr, int light) {
            final short l = (short) (((light >>> 8) & 0xFF00) | (light & 0xFF));
            MemoryUtil.memPutShort(ptr + 6, l);
        }

        @Override
        public void normal(long ptr, int normal) {

        }

        @Override
        public int getStride() {
            return VERTEX_SIZE;
        }
    }

}