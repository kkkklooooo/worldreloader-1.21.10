package com.worldreloader.client;

import com.worldreloader.ModConfig;
import com.worldreloader.WorldReloader;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Renders the whitelist min/max bounding box in-world when Debug mode and Whitelist mode are enabled.
 */
public final class WhitelistDebugRenderer {
    private static boolean registered = false;

    private WhitelistDebugRenderer() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            ModConfig config = WorldReloader.config;
            if (config == null || !config.Debug || !config.UseWhitelist) {
                return;
            }

            VertexConsumerProvider consumers = context.consumers();
            if (consumers == null) {
                return;
            }

            int minX = Math.min(config.whitelistMinX, config.whitelistMaxX);
            int minY = Math.min(config.whitelistMinY, config.whitelistMaxY);
            int minZ = Math.min(config.whitelistMinZ, config.whitelistMaxZ);
            int maxX = Math.max(config.whitelistMinX, config.whitelistMaxX) + 1;
            int maxY = Math.max(config.whitelistMinY, config.whitelistMaxY) + 1;
            int maxZ = Math.max(config.whitelistMinZ, config.whitelistMaxZ) + 1;

            MatrixStack matrices = context.matrixStack();
            Vec3d camPos = context.camera().getPos();

            matrices.push();
            matrices.translate(-camPos.x, -camPos.y, -camPos.z);

            VertexConsumer lineConsumer = consumers.getBuffer(RenderLayer.getLines());
            float r = config.whitelistMapBlocks ? 1.0f : 0.1f;
            float g = config.whitelistMapBlocks ? 0.5f : 0.95f;
            float b = config.whitelistMapBlocks ? 0.1f : 0.55f;

            WorldRenderer.drawBox(matrices, lineConsumer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 1.0f);

            // Highlight exact min (green) and max (red) corner blocks.
            WorldRenderer.drawBox(matrices, lineConsumer,
                    config.whitelistMinX, config.whitelistMinY, config.whitelistMinZ,
                    config.whitelistMinX + 1, config.whitelistMinY + 1, config.whitelistMinZ + 1,
                    0.2f, 1.0f, 0.2f, 1.0f);
            WorldRenderer.drawBox(matrices, lineConsumer,
                    config.whitelistMaxX, config.whitelistMaxY, config.whitelistMaxZ,
                    config.whitelistMaxX + 1, config.whitelistMaxY + 1, config.whitelistMaxZ + 1,
                    1.0f, 0.2f, 0.2f, 1.0f);

            matrices.pop();
        });
    }
}
