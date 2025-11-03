package com.worldreloader;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static KeyBinding openConfigKey;

    public static void register() {
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.worldloader.open_config", // 翻译键
                InputUtil.Type.KEYSYM, // 按键类型
                GLFW.GLFW_KEY_R,
                KeyBinding.Category.GAMEPLAY
        ));
    }
}