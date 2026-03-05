package com.maxsters.coldspawncontrol.client.particle;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nonnull;
import java.util.Collections;

import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.ArrayList;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import net.minecraft.util.Mth;

@SuppressWarnings("null")
public class SnowClusterParticle extends TextureSheetParticle {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final List<SnowClusterParticle> PARTICLES = Collections.synchronizedList(new ArrayList<>());

    private static final ThreadLocal<com.mojang.math.Vector3f[]> RENDER_CORNERS = ThreadLocal
            .withInitial(() -> new com.mojang.math.Vector3f[] {
                    new com.mojang.math.Vector3f(), new com.mojang.math.Vector3f(), new com.mojang.math.Vector3f(),
                    new com.mojang.math.Vector3f()
            });
    private static final ResourceLocation SNOW_TEXTURE = new ResourceLocation("minecraft",
            "textures/environment/snow.png");

    // MethodHandle for faster field access (vs reflection) - "stoppedByCollision"
    // field in Particle
    private static MethodHandle STOPPED_BY_COLLISION_HANDLE;

    static {
        try {
            // Parchment: "stoppedByCollision" (obfuscated: f_107205_) - boolean field in
            // Particle
            Field field = Particle.class.getDeclaredField("stoppedByCollision");
            field.setAccessible(true);
            STOPPED_BY_COLLISION_HANDLE = MethodHandles.lookup().unreflectSetter(field);
        } catch (Exception e) {
            // Fallback to obfuscated name for production environments
            try {
                Field field = Particle.class.getDeclaredField("f_107205_");
                field.setAccessible(true);
                STOPPED_BY_COLLISION_HANDLE = MethodHandles.lookup().unreflectSetter(field);
            } catch (Exception e2) {
                LOGGER.warn("Could not acquire stoppedByCollision field handle - particle respawn may have issues");
            }
        }
    }

    public final int id = ThreadLocalRandom.current().nextInt(100000);

    private boolean instantPop;
    private float rotSpeed;
    // Reusable mutable position for collision checks - reduces GC pressure
    private final BlockPos.MutableBlockPos collisionPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();

    protected SnowClusterParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);

        PARTICLES.add(this);

        this.gravity = 0.027F; // Vanilla-like gravity
        this.friction = 0.98F;

        this.instantPop = xSpeed == 1.0;
        this.xd = 0;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.quadSize = 0.5F;
        this.hasPhysics = true;
        this.alpha = 0.0F;

        this.lifetime = 600; // Fallback lifetime

        this.roll = (float) Math.random() * ((float) Math.PI * 2.0F);
        this.oRoll = this.roll;
        this.rotSpeed = ((float) Math.random() - 0.5F) * 0.05F; // Small random spin speed
    }

    public int getAge() {
        return this.age;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public int getLifetime() {
        return this.lifetime;
    }

    public Vec3 getPos() {
        return new Vec3(this.x, this.y, this.z);
    }

    @Override
    public boolean isAlive() {
        // Prevent engine from culling us based on age. We recycle manually.
        return !this.removed;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (!this.onGround) {
            this.roll += this.rotSpeed; // Apply spin speed only if in air
        }

        this.age++; // Increment age for handler checks

        // Simple Gravity
        this.yd -= 0.04D * (double) this.gravity;

        // Wind Effect - Using ThreadLocalRandom (Lock-free)
        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.xd += 0.002D + (random.nextDouble() - 0.5) * 0.004D; // Slight drift X
        this.zd += 0.002D + (random.nextDouble() - 0.5) * 0.004D; // Slight drift Z

        // Capture velocity before move to detect wall collisions
        double origXd = this.xd;
        double origZd = this.zd;

        // Optimized Physics: Decoupled X/Z and Y checks to prevent wall-snapping

        // 1. Horizontal Move
        double nextX = this.x + this.xd;
        double nextZ = this.z + this.zd;

        // Check horizontal collision at current Y (mid-body check roughly, or just
        // base)
        checkPos.set(nextX, this.y, nextZ);
        BlockState stateH = this.level.getBlockState(checkPos);

        boolean isHorizontalPowderSnow = stateH.is(Blocks.POWDER_SNOW);
        if (isHorizontalPowderSnow || (!stateH.isAir() && !stateH.getCollisionShape(this.level, checkPos).isEmpty())) {
            // Check if we are actually inside the shape horizontally
            // Simple approach: If it's solid or powder snow, stop horizontal movement.
            // We don't snap X/Z, we just don't move.
            this.xd = 0;
            this.zd = 0;
            // Keep x, z as is (don't apply nextX/nextZ)
            if (isHorizontalPowderSnow) {
                // Powder snow has no collision shape but should kill snow particles
                this.age = this.lifetime;
            }
        } else {
            this.x = nextX;
            this.z = nextZ;
        }

        // 2. Vertical Move (at new X/Z)
        double nextY = this.y + this.yd;
        checkPos.set(this.x, nextY, this.z);
        BlockState stateY = this.level.getBlockState(checkPos);

        if (!stateY.isAir()) {
            // Powder snow has no collision shape but should stop and kill snow particles
            if (stateY.is(Blocks.POWDER_SNOW)) {
                this.y = checkPos.getY() + 1.0; // Snap to top of powder snow block
                this.yd = 0;
                this.onGround = true;
                this.age = this.lifetime; // Kill the particle immediately
            } else {
                VoxelShape shape = stateY.getCollisionShape(this.level, checkPos);
                if (!shape.isEmpty()) {
                    double maxY = checkPos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
                    // Only snap if we are falling onto it (current Y is above or near top)
                    // And nextY is below it.
                    if (this.y >= maxY - 0.01 && nextY < maxY) {
                        this.y = maxY;
                        this.yd = 0;
                        this.onGround = true;
                    } else {
                        // We are inside/below, just move (or we hit a ceiling, but snow only falls
                        // down)
                        this.y = nextY;
                    }
                } else {
                    this.y = nextY;
                }
            }
        } else {
            this.y = nextY;
        }

        // CRITICAL FIX: Update BoundingBox!
        this.setPos(this.x, this.y, this.z);

        boolean hitWall = (origXd != 0 && this.xd == 0) || (origZd != 0 && this.zd == 0);
        // Use reusable mutable position instead of allocating new BlockPos
        collisionPos.set(this.x, this.y, this.z);
        boolean hitLiquid = !this.level.getFluidState(collisionPos).isEmpty();

        // Ground/Wall/Liquid check for interaction
        if (this.onGround || hitWall || hitLiquid) {
            this.xd = 0;
            this.zd = 0;
            // Immediate recycling: Set age to lifetime so the handler picks it up
            this.age = this.lifetime;
        }

        this.zd *= (double) this.friction;

    }

    public void respawn(double x, double y, double z) {
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;

        // Reset historical rotation to prevent spinning from old state
        this.oRoll = this.roll;

        // IMPORTANT: Reset Velocity so it doesn't freeze or keep falling fast
        this.yd = -0.0675; // Initial spawn velocity
        this.xd = 0;
        this.zd = 0;

        this.age = 0;
        this.removed = false;

        this.alpha = 0.0F;
        this.onGround = false;
        this.rotSpeed = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 0.05F; // Re-randomize spin on respawn

        // Reset collision flag using fast MethodHandle (vs slow reflection)
        if (STOPPED_BY_COLLISION_HANDLE != null) {
            try {
                STOPPED_BY_COLLISION_HANDLE.invoke(this, false);
            } catch (Throwable ignored) {
                // Silently ignore - particle may still work without this
            }
        }

    }

    public net.minecraft.world.level.Level getLevel() {
        return this.level;
    }

    @Override
    public void remove() {
        // Do NOT remove from PARTICLES set here to avoid concurrent modification in
        // handler.
        super.remove();
    }

    @Override
    public void render(@Nonnull com.mojang.blaze3d.vertex.VertexConsumer buffer,
            @Nonnull net.minecraft.client.Camera renderInfo,
            float partialTicks) {
        // Fade-in logic
        if (this.instantPop) {
            this.alpha = 1.0F;
        } else {
            float fadeProgress = Math.min(1.0F, ((float) this.age + partialTicks) / 20.0F);
            this.alpha = fadeProgress;
        }

        double interpX = net.minecraft.util.Mth.lerp(partialTicks, this.xo, this.x);
        double interpY = net.minecraft.util.Mth.lerp(partialTicks, this.yo, this.y);
        double interpZ = net.minecraft.util.Mth.lerp(partialTicks, this.zo, this.z);

        net.minecraft.world.phys.Vec3 vec3 = renderInfo.getPosition();
        float f = (float) (interpX - vec3.x());
        float f1 = (float) (interpY - vec3.y());
        float f2 = (float) (interpZ - vec3.z());

        com.mojang.math.Quaternion quaternion = renderInfo.rotation();

        float quadSize = this.getQuadSize(partialTicks);
        float cos = 1.0F;
        float sin = 0.0F;
        if (this.roll != 0.0F) {
            float lerpedRoll = Mth.lerp(partialTicks, this.oRoll, this.roll);
            cos = Mth.cos(lerpedRoll);
            sin = Mth.sin(lerpedRoll);
        }

        com.mojang.math.Vector3f[] avector3f = RENDER_CORNERS.get();
        // Calculate rotated corners manually to avoid object churn
        // Corner 1: -1, -1
        avector3f[0].set(-cos + sin, -sin - cos, 0.0F);
        // Corner 2: -1, 1
        avector3f[1].set(-cos - sin, -sin + cos, 0.0F);
        // Corner 3: 1, 1
        avector3f[2].set(cos - sin, sin + cos, 0.0F);
        // Corner 4: 1, -1
        avector3f[3].set(cos + sin, sin - cos, 0.0F);

        for (int i = 0; i < 4; ++i) {
            com.mojang.math.Vector3f vector3f = avector3f[i];
            vector3f.transform(quaternion);
            vector3f.mul(quadSize);
            vector3f.add(f, f1, f2);
        }

        float minU = 0.0F;
        float maxU = 1.0F;
        float minV = 0.0F;
        float maxV = 1.0F;

        int j = this.getLightColor(partialTicks);

        buffer.vertex((double) avector3f[0].x(), (double) avector3f[0].y(), (double) avector3f[0].z()).uv(maxU, maxV)
                .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(j).endVertex();
        buffer.vertex((double) avector3f[1].x(), (double) avector3f[1].y(), (double) avector3f[1].z()).uv(maxU, minV)
                .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(j).endVertex();
        buffer.vertex((double) avector3f[2].x(), (double) avector3f[2].y(), (double) avector3f[2].z()).uv(minU, minV)
                .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(j).endVertex();
        buffer.vertex((double) avector3f[3].x(), (double) avector3f[3].y(), (double) avector3f[3].z()).uv(minU, maxV)
                .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(j).endVertex();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return SNOW_RENDER_TYPE;
    }

    public static final ParticleRenderType SNOW_RENDER_TYPE = new ParticleRenderType() {
        @Override
        public void begin(@Nonnull BufferBuilder buffer, @Nonnull TextureManager textureManager) {
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getParticleShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.setShaderTexture(0, SNOW_TEXTURE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(@Nonnull Tesselator tesselator) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            tesselator.end();
        }

        @Override
        public String toString() {
            return "SNOW_RENDER_TYPE";
        }
    };

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        public Provider() {
        }

        public Provider(SpriteSet sprites) {
        }

        @Override
        public @Nonnull Particle createParticle(@Nonnull SimpleParticleType type, @Nonnull ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {

            return new SnowClusterParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}