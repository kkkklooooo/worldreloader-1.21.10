package com.worldreloader;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class CommandHandler {

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registrationEnvironment,environment) -> {
            registerStartCommand(dispatcher);
        });
    }

    private static void registerStartCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager
                .literal("start")
                .executes(CommandHandler::executeStartCommand)
        );
    }

    private static int executeStartCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        try {
            // Get player position as the beacon position since beacon is no longer used
            BlockPos playerPos = source.getPlayer().getBlockPos();

            // Start terrain transformation using the player's current position
            source.getServer().execute(() -> {
                // Use the static instance to call the private method
                WorldReloader.instance.startTerrainTransformation(
                    (net.minecraft.server.world.ServerWorld) source.getWorld(),
                    playerPos,
                    source.getPlayer()
                );
            });
        } catch (Exception e) {
            source.getPlayer().sendMessage(Text.literal("§c执行地形改造命令时出错: " + e.getMessage()), false);
        }

        return 1; // Command executed successfully
    }
}