package com.cp.worldreloader;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = WorldReloader.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEvents {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (KeyBindings.openConfigKey.consumeClick()) {
            // 打开配置界面
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.setScreen(AutoConfig.getConfigScreen(ModConfig.class, client.screen).get());
            }
        }
    }

    // 如果需要处理鼠标按键
    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton event) {
        // 可以在这里处理鼠标事件
    }
}
