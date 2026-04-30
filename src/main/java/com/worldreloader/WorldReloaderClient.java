package com.worldreloader;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

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
    }
}
