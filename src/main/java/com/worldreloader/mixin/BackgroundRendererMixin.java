package com.worldreloader.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.worldreloader.WorldReloader;
import com.worldreloader.client.NetherAtmosphereHelper;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies Nether-style dense biome fog color and shorter fog distance when standing inside a
 * Nether biome in an Overworld-like dimension.
 */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Shadow
    private static float red;

    @Shadow
    private static float green;

    @Shadow
    private static float blue;

    @Inject(method = "render", at = @At("TAIL"))
    private static void worldreloader$applyNetherFogColor(Camera camera, float tickDelta, ClientWorld world,
                                                          int viewDistance, float skyDarkness, CallbackInfo ci) {
        if (world == null || WorldReloader.config == null || !WorldReloader.config.netherAtmosphere) {
            return;
        }
        if (world.getDimensionEffects().useThickFog(0, 0)
                || camera.getSubmersionType() != CameraSubmersionType.NONE
                || worldreloader$hasFogStatusEffect(camera.getFocusedEntity())) {
            return;
        }

        float progress = NetherAtmosphereHelper.getNetherFogProgress();
        if (progress <= 0.001f) {
            return;
        }

        int fogColor = NetherAtmosphereHelper.getFogColor();
        float targetR = ((fogColor >> 16) & 0xFF) / 255.0f;
        float targetG = ((fogColor >> 8) & 0xFF) / 255.0f;
        float targetB = (fogColor & 0xFF) / 255.0f;

        red = MathHelper.lerp(progress, red, targetR);
        green = MathHelper.lerp(progress, green, targetG);
        blue = MathHelper.lerp(progress, blue, targetB);
        RenderSystem.clearColor(red, green, blue, 0.0F);
    }

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void worldreloader$applyNetherThickFog(Camera camera, BackgroundRenderer.FogType fogType,
                                                          float viewDistance, boolean thickFog, float tickDelta,
                                                          CallbackInfo ci) {
        if (thickFog || WorldReloader.config == null || !WorldReloader.config.netherAtmosphere) {
            return;
        }
        if (camera.getSubmersionType() != CameraSubmersionType.NONE
                || worldreloader$hasFogStatusEffect(camera.getFocusedEntity())) {
            return;
        }

        float progress = NetherAtmosphereHelper.getNetherFogProgress();
        if (progress <= 0.001f) {
            return;
        }

        float netherStart = viewDistance * 0.05f;
        float netherEnd = Math.min(viewDistance, 192.0f) * 0.5f;

        RenderSystem.setShaderFogStart(MathHelper.lerp(progress, RenderSystem.getShaderFogStart(), netherStart));
        RenderSystem.setShaderFogEnd(MathHelper.lerp(progress, RenderSystem.getShaderFogEnd(), netherEnd));
        if (progress > 0.5f && fogType == BackgroundRenderer.FogType.FOG_TERRAIN) {
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
        }
    }

    private static boolean worldreloader$hasFogStatusEffect(Entity entity) {
        return entity instanceof LivingEntity living
                && (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS));
    }
}
