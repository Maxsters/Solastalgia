package com.maxsters.coldspawncontrol.client.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nonnull;

/**
 * A small, glowing star particle that floats upward from ore blocks.
 * Renders at full brightness regardless of ambient lighting, making
 * ores visible in pitch-black caves. Uses a custom fullbright render
 * type with the vanilla glow particle texture.
 */
@SuppressWarnings("null")
public class OreGlintParticle extends TextureSheetParticle {

    private static final ResourceLocation GLOW_TEXTURE = new ResourceLocation("minecraft",
            "textures/particle/glow.png");

    private static final ThreadLocal<com.mojang.math.Vector3f[]> RENDER_CORNERS = ThreadLocal
            .withInitial(() -> new com.mojang.math.Vector3f[] {
                    new com.mojang.math.Vector3f(), new com.mojang.math.Vector3f(),
                    new com.mojang.math.Vector3f(), new com.mojang.math.Vector3f()
            });

    private final float baseQuadSize;
    private final float spinSpeed;

    protected OreGlintParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);

        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        this.quadSize = 0.12F + this.random.nextFloat() * 0.08F;
        this.baseQuadSize = this.quadSize;

        this.lifetime = 40 + this.random.nextInt(40);

        this.hasPhysics = false;
        this.gravity = 0F;

        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.alpha = 0.0F;

        this.roll = (float) (Math.random() * Math.PI * 2.0);
        this.oRoll = this.roll;
        this.spinSpeed = ((float) Math.random() - 0.5F) * 0.3F;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        this.x += this.xd;
        this.y += this.yd;
        this.z += this.zd;

        this.xd *= 0.85;
        this.yd *= 0.85;
        this.zd *= 0.85;

        this.roll += this.spinSpeed;

        float lifeRatio = (float) this.age / (float) this.lifetime;
        if (lifeRatio < 0.25F) {
            this.alpha = lifeRatio / 0.25F;
        } else if (lifeRatio > 0.75F) {
            this.alpha = (1.0F - lifeRatio) / 0.25F;
        } else {
            this.alpha = 1.0F;
        }

        float pulse = 1.0F + 0.15F * Mth.sin(this.age * 0.3F);
        this.quadSize = this.baseQuadSize * pulse;
    }

    @Override
    public void render(@Nonnull com.mojang.blaze3d.vertex.VertexConsumer buffer,
            @Nonnull net.minecraft.client.Camera renderInfo,
            float partialTicks) {

        double interpX = Mth.lerp(partialTicks, this.xo, this.x);
        double interpY = Mth.lerp(partialTicks, this.yo, this.y);
        double interpZ = Mth.lerp(partialTicks, this.zo, this.z);

        net.minecraft.world.phys.Vec3 cam = renderInfo.getPosition();
        float fx = (float) (interpX - cam.x());
        float fy = (float) (interpY - cam.y());
        float fz = (float) (interpZ - cam.z());

        com.mojang.math.Quaternion quaternion = renderInfo.rotation();

        float quadSize = this.getQuadSize(partialTicks);
        float cos = 1.0F;
        float sin = 0.0F;
        if (this.roll != 0.0F) {
            float lerpedRoll = Mth.lerp(partialTicks, this.oRoll, this.roll);
            cos = Mth.cos(lerpedRoll);
            sin = Mth.sin(lerpedRoll);
        }

        com.mojang.math.Vector3f[] corners = RENDER_CORNERS.get();
        corners[0].set(-cos + sin, -sin - cos, 0.0F);
        corners[1].set(-cos - sin, -sin + cos, 0.0F);
        corners[2].set(cos - sin, sin + cos, 0.0F);
        corners[3].set(cos + sin, sin - cos, 0.0F);

        for (int i = 0; i < 4; ++i) {
            com.mojang.math.Vector3f v = corners[i];
            v.transform(quaternion);
            v.mul(quadSize);
            v.add(fx, fy, fz);
        }

        // Fullbright: 15 block light, 15 sky light
        int light = 0xF000F0;

        buffer.vertex(corners[0].x(), corners[0].y(), corners[0].z())
                .uv(1.0F, 1.0F)
                .color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light).endVertex();
        buffer.vertex(corners[1].x(), corners[1].y(), corners[1].z())
                .uv(1.0F, 0.0F)
                .color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light).endVertex();
        buffer.vertex(corners[2].x(), corners[2].y(), corners[2].z())
                .uv(0.0F, 0.0F)
                .color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light).endVertex();
        buffer.vertex(corners[3].x(), corners[3].y(), corners[3].z())
                .uv(0.0F, 1.0F)
                .color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light).endVertex();
    }

    @Override
    @Nonnull
    public ParticleRenderType getRenderType() {
        return ORE_GLINT_RENDER_TYPE;
    }

    /**
     * Custom render type that binds the glow texture and enables
     * additive-ish blending for a bright star effect in darkness.
     */
    public static final ParticleRenderType ORE_GLINT_RENDER_TYPE = new ParticleRenderType() {
        @Override
        public void begin(@Nonnull BufferBuilder buffer, @Nonnull TextureManager textureManager) {
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(@Nonnull Tesselator tesselator) {
            tesselator.end();
        }

        @Override
        public String toString() {
            return "ORE_GLINT_RENDER_TYPE";
        }
    };

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        public Provider() {
        }

        public Provider(SpriteSet sprites) {
        }

        @Override
        @Nonnull
        public Particle createParticle(@Nonnull SimpleParticleType type, @Nonnull ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {
            return new OreGlintParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}
