package com.worldreloader.client;

import com.worldreloader.FakeBlockEntityRenderer;
import com.worldreloader.WorldReloader;
import com.worldreloader.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

// 在客户端初始化代码中
@Environment(EnvType.CLIENT)
public class WorldReloaderClient implements ClientModInitializer {
    public static ShaderProgram trailGlowShader;
    @Override
    public void onInitializeClient() {

        CoreShaderRegistrationCallback.EVENT.register(context -> {

            context.register(
                    Identifier.of(WorldReloader.MOD_ID, "trail_glow"), // JSON 文件名 (不带后缀)
                    VertexFormats.POSITION_TEXTURE_COLOR,        // 顶点格式，必须与 JSON 里的 attributes 对应
                    program -> trailGlowShader = program         // 回调：加载成功后赋值给静态变量
            );
        });
        EntityRendererRegistry.register(ModEntities.FAKE_BLOCK, FakeBlockEntityRenderer::new);


    }

    public static ShaderProgram getTrailGlowShader() {
        return trailGlowShader;
    }
}
