package com.worldreloader.mixin;

import com.worldreloader.WorldReloader;
import com.worldreloader.client.NetherAtmosphereHelper;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises ambient light in non-Nether dimensions while inside a Nether biome so transformed
 * Nether areas retain Nether-like ambient brightness.
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
    @Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true)
    private static void worldreloader$applyNetherAmbientLight(DimensionType type, int lightLevel,
                                                              CallbackInfoReturnable<Float> cir) {
        if (WorldReloader.config == null || !WorldReloader.config.netherAtmosphere) {
            return;
        }
        float targetAmbient = MathHelper.clamp(WorldReloader.config.netherAmbientLight, 0.0f, 1.0f);
        float progress = NetherAtmosphereHelper.getNetherFogProgress();
        if (progress <= 0.001f || targetAmbient <= type.ambientLight()) {
            return;
        }

        float f = lightLevel / 15.0F;
        float g = f / (4.0F - 3.0F * f);
        float effectiveAmbient = MathHelper.lerp(progress, type.ambientLight(), targetAmbient);
        cir.setReturnValue(MathHelper.lerp(effectiveAmbient, g, 1.0F));
    }
}
