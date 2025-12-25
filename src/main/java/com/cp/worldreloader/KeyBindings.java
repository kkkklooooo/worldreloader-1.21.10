package com.cp.worldreloader;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = WorldReloader.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBindings {
    public static KeyMapping openConfigKey;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        openConfigKey = new KeyMapping(
                "key.worldreloader.open_config", // 翻译键
                InputConstants.Type.KEYSYM,      // 按键类型
                GLFW.GLFW_KEY_Y,                 // 默认按键Y
                "category.worldreloader.general" // 分类键
        );
        event.register(openConfigKey);
    }

    // 可选：添加一个便捷方法来检查按键是否被按下
    public static boolean isOpenConfigKeyDown() {
        return openConfigKey.isDown();
    }

    // 可选：添加一个方法来检查按键是否刚被按下（只触发一次）
    public static boolean wasOpenConfigKeyPressed() {
        return openConfigKey.consumeClick();
    }
}