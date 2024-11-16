package net.vulkanmod.mixin.render;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.ShaderMixed;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.Uniform;
import net.vulkanmod.vulkan.shader.parser.GlslConverter;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

@Mixin(ShaderInstance.class)
public class ShaderInstanceM implements ShaderMixed {

    @Shadow @Final private Map<String, com.mojang.blaze3d.shaders.Uniform> uniformMap;
    @Shadow @Final private String name;

    @Shadow @Final @Nullable public com.mojang.blaze3d.shaders.Uniform MODEL_VIEW_MATRIX;
    @Shadow @Final @Nullable public com.mojang.blaze3d.shaders.Uniform PROJECTION_MATRIX;
    @Shadow @Final @Nullable public com.mojang.blaze3d.shaders.Uniform COLOR_MODULATOR;
    @Shadow @Final @Nullable public com.mojang.blaze3d.shaders.Uniform LINE_WIDTH;
    @Shadow @Final @Nullable public com.mojang.blaze3d.shaders.Uniform GLINT_ALPHA;
    @Shadow @Final @Nullable public com.mojang.blaze3d.shaders.Uniform FOG_START;
    @Shadow @Final @Nullable public com.mojang.blaze3d.shaders.Uniform FOG_END;
    @Shadow @Final @Nullable public com.mojang.blaze3d.shaders.Uniform FOG_COLOR;
    @Shadow @Final @Nullable public com.mojang.blaze3d.shaders.Uniform FOG_SHAPE;
    @Shadow @Final @Nullable public com.mojang.blaze3d.shaders.Uniform TEXTURE_MATRIX;
    @Shadow @Final @Nullable public com.mojang.blaze3d.shaders.Uniform GAME_TIME;
    @Shadow @Final @Nullable public com.mojang.blaze3d.shaders.Uniform SCREEN_SIZE;

    private String vsPath;
    private String fsName;

    private GraphicsPipeline pipeline;
    boolean doUniformUpdate = false;

    public GraphicsPipeline getPipeline() {
        return pipeline;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void create(ResourceProvider resourceProvider, String name, VertexFormat format, CallbackInfo ci) {
        String configName = name;
        JsonObject config = ShaderLoadUtil.getJsonConfig("core", configName);

        if (config == null) {
            createLegacyShader(resourceProvider, format);
            return;
        }

        Pipeline.Builder builder = new Pipeline.Builder(format, configName);
        builder.setUniformSupplierGetter(info -> this.getUniformSupplier(info.name));

        builder.parseBindings(config);

        ShaderLoadUtil.loadShaders(builder, config, configName, "core");

        GraphicsPipeline pipeline = builder.createGraphicsPipeline();
        this.pipeline = pipeline;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;getOrCreate(Lnet/minecraft/server/packs/resources/ResourceProvider;Lcom/mojang/blaze3d/shaders/Program$Type;Ljava/lang/String;)Lcom/mojang/blaze3d/shaders/Program;"))
    private Program loadNames(ResourceProvider resourceProvider, Program.Type type, String name) {
        String path;
        if (this.name.contains(String.valueOf(ResourceLocation.NAMESPACE_SEPARATOR))) {
            ResourceLocation location = ResourceLocation.tryParse(name);
            path = location.withPath("shaders/core/%s".formatted(location.getPath())).toString();
        } else {
            path = "shaders/core/%s".formatted(name);
        }

        switch (type) {
            case VERTEX -> this.vsPath = path;
            case FRAGMENT -> this.fsName = path;
        }

        return null;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;glBindAttribLocation(IILjava/lang/CharSequence;)V"))
    private void bindAttr(int program, int index, CharSequence name) {}

    /**
     * @author
     */
    @Overwrite
    public void close() {
        if (this.pipeline != null)
            this.pipeline.cleanUp();
    }

    /**
     * @author
     */
    @Overwrite
    public void apply() {
        if (!this.doUniformUpdate)
            return;

        if (this.MODEL_VIEW_MATRIX != null) {
            this.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        }

        if (this.PROJECTION_MATRIX != null) {
            this.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }

        if (this.COLOR_MODULATOR != null) {
            this.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }

        if (this.GLINT_ALPHA != null) {
            this.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        }

        if (this.FOG_START != null) {
            this.FOG_START.set(RenderSystem.getShaderFogStart());
        }

        if (this.FOG_END != null) {
            this.FOG_END.set(RenderSystem.getShaderFogEnd());
        }

        if (this.FOG_COLOR != null) {
            this.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }

        if (this.FOG_SHAPE != null) {
            this.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }

        if (this.TEXTURE_MATRIX != null) {
            this.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }

        if (this.GAME_TIME != null) {
            this.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }

        if (this.SCREEN_SIZE != null) {
            Window window = Minecraft.getInstance().getWindow();
            this.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
        }

        if (this.LINE_WIDTH != null) {
            this.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
        }
    }

    /**
     * @author
     */
    @Overwrite
    public void clear() {}

    public void setupUniformSuppliers(UBO ubo) {
        for (Uniform vUniform : ubo.getUniforms()) {
            com.mojang.blaze3d.shaders.Uniform uniform = this.uniformMap.get(vUniform.getName());

            if (uniform == null) {
                Initializer.LOGGER.error(String.format("Error: field %s not present in uniform map", vUniform.getName()));
                continue;
            }

            Supplier<MappedBuffer> supplier;
            ByteBuffer byteBuffer;

            if (uniform.getType() <= 3) {
                byteBuffer = MemoryUtil.memByteBuffer(uniform.getIntBuffer());
            } else if (uniform.getType() <= 10) {
                byteBuffer = MemoryUtil.memByteBuffer(uniform.getFloatBuffer());
            } else {
                throw new RuntimeException("out of bounds value for uniform " + uniform);
            }


            MappedBuffer mappedBuffer = MappedBuffer.createFromBuffer(byteBuffer);
            supplier = () -> mappedBuffer;

            vUniform.setSupplier(supplier);
        }

    }

    public Supplier<MappedBuffer> getUniformSupplier(String name) {
        com.mojang.blaze3d.shaders.Uniform uniform1 = this.uniformMap.get(name);

        if (uniform1 == null) {
            Initializer.LOGGER.error(String.format("Error: field %s not present in uniform map", name));
            return null;
        }

        Supplier<MappedBuffer> supplier;
        ByteBuffer byteBuffer;

        if (uniform1.getType() <= 3) {
            byteBuffer = MemoryUtil.memByteBuffer(uniform1.getIntBuffer());
        } else if (uniform1.getType() <= 10) {
            byteBuffer = MemoryUtil.memByteBuffer(uniform1.getFloatBuffer());
        } else {
            throw new RuntimeException("out of bounds value for uniform " + uniform1);
        }

        MappedBuffer mappedBuffer = MappedBuffer.createFromBuffer(byteBuffer);
        supplier = () -> mappedBuffer;

        return supplier;
    }

    @Override
    public void setDoUniformsUpdate() {
        this.doUniformUpdate = true;
    }

    @Override
    public void setPipeline(GraphicsPipeline graphicsPipeline) {
        this.pipeline = graphicsPipeline;
    }

    private void createLegacyShader(ResourceProvider resourceProvider, VertexFormat format) {
        try {
            String vertPath = this.vsPath + ".vsh";
            Resource resource = resourceProvider.getResourceOrThrow(ResourceLocation.tryParse(vertPath));
            InputStream inputStream = resource.open();
            String vshSrc = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

            String fragPath = this.fsName + ".fsh";
            resource = resourceProvider.getResourceOrThrow(ResourceLocation.tryParse(fragPath));
            inputStream = resource.open();
            String fshSrc = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

            GlslConverter converter = new GlslConverter();
            Pipeline.Builder builder = new Pipeline.Builder(format, this.name);

            converter.process(vshSrc, fshSrc);
            UBO ubo = converter.createUBO();
            this.setupUniformSuppliers(ubo);

            builder.setUniforms(Collections.singletonList(ubo), converter.getSamplerList());
            builder.compileShaders(this.name, converter.getVshConverted(), converter.getFshConverted());

            this.pipeline = builder.createGraphicsPipeline();
            this.doUniformUpdate = true;
        } catch (Exception e) {
            Initializer.LOGGER.error("Error on shader {} conversion/compilation", this.name);
            e.printStackTrace();
        }
    }
}

