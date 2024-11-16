package net.vulkanmod.mixin.render.block;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.vulkanmod.render.chunk.build.frapi.helper.NormalHelper;
import net.vulkanmod.render.chunk.cull.QuadFacing;
import net.vulkanmod.render.model.quad.ModelQuadView;
import net.vulkanmod.render.model.quad.ModelQuadFlags;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.vulkanmod.render.model.quad.ModelQuad.VERTEX_SIZE;

@Mixin(BakedQuad.class)
public class BakedQuadM implements ModelQuadView {

    @Shadow @Final protected int[] vertices;
    @Shadow @Final protected Direction direction;
    @Shadow @Final protected int tintIndex;

    private int flags;
    private int normal;
    private QuadFacing facing;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(int[] vertices, int tintIndex, Direction face, TextureAtlasSprite textureAtlasSprite, boolean shade, CallbackInfo ci) {
        this.flags = ModelQuadFlags.getQuadFlags(this, face);

        int packedNormal = NormalHelper.computePackedNormal(this);
        this.normal = packedNormal;
        this.facing = QuadFacing.fromNormal(packedNormal);
    }

    @Override
    public int getFlags() {
        return flags;
    }

    @Override
    public float getX(int idx) {
        return Float.intBitsToFloat(this.vertices[vertexOffset(idx) + 0]);
    }

    @Override
    public float getY(int idx) {
        return Float.intBitsToFloat(this.vertices[vertexOffset(idx) + 1]);
    }

    @Override
    public float getZ(int idx) {
        return Float.intBitsToFloat(this.vertices[vertexOffset(idx) + 2]);
    }

    @Override
    public int getColor(int idx) {
        return this.vertices[vertexOffset(idx) + 3];
    }

    @Override
    public float getU(int idx) {
        return Float.intBitsToFloat(this.vertices[vertexOffset(idx) + 4]);
    }

    @Override
    public float getV(int idx) {
        return Float.intBitsToFloat(this.vertices[vertexOffset(idx) + 5]);
    }

    @Override
    public int getColorIndex() {
        return this.tintIndex;
    }

    @Override
    public Direction lightFace() {
        return this.direction;
    }

    @Override
    public Direction getFacingDirection() {
        return this.direction;
    }

    @Override
    public QuadFacing getQuadFacing() {
        return this.facing;
    }

    @Override
    public int getNormal() {
        return this.normal;
    }

    @Override
    public boolean isTinted() {
        return this.tintIndex != -1;
    }

    private static int vertexOffset(int vertexIndex) {
        return vertexIndex * VERTEX_SIZE;
    }
}
