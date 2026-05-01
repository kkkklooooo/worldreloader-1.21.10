package com.worldreloader;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;

public class WorldReloaderClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyBindings.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KeyBindings.openConfigKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new ConfigScreen(client.currentScreen));
                }
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) {
                syncConfigToServer();
            }
            return ActionResult.PASS;
        });
    }

    public static void syncConfigToServer() {
        try {
            if (ClientPlayNetworking.canSend(ConfigSyncPayload.ID)) {
                ClientPlayNetworking.send(new ConfigSyncPayload(WorldReloader.config.toJson()));
            }
        } catch (Exception e) {
            WorldReloader.LOGGER.warn("无法同步 World Reloader 配置到服务器", e);
        }
    }
}
