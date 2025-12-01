package com.worldreloader.client;

import com.worldreloader.FakeBlockEntityRenderer;
import com.worldreloader.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

// 在客户端初始化代码中
@Environment(EnvType.CLIENT)
public class WorldReloaderClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.FAKE_BLOCK, FakeBlockEntityRenderer::new);
    }
}
