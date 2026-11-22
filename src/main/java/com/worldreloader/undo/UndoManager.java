package com.worldreloader.undo;

import com.worldreloader.WorldReloader;
import com.worldreloader.transformationtasks.BaseTransformationTask;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Coordinates transformation undo history, active task interruption, and reverse playback tasks.
 */
public final class UndoManager {
    private static final Deque<TransformationRecord> HISTORY = new ArrayDeque<>();
    private static final List<BaseTransformationTask> ACTIVE_FORWARD_TASKS = new ArrayList<>();
    private static UndoTransformationTask activeUndoTask = null;
    private static boolean initialized = false;

    private UndoManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (activeUndoTask != null) {
                activeUndoTask.tick();
                if (!activeUndoTask.isActive()) {
                    activeUndoTask = null;
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    public static void registerActiveTask(BaseTransformationTask task) {
        if (task != null && !ACTIVE_FORWARD_TASKS.contains(task)) {
            ACTIVE_FORWARD_TASKS.add(task);
        }
    }

    public static void onTaskFinished(BaseTransformationTask task, TransformationRecord record) {
        ACTIVE_FORWARD_TASKS.remove(task);
        int maxHistory = WorldReloader.config != null ? WorldReloader.config.maxUndoHistory : 5;
        if (maxHistory <= 0 || record == null || record.isEmpty()) {
            return;
        }
        HISTORY.push(record);
        while (HISTORY.size() > maxHistory) {
            HISTORY.removeLast();
        }
    }

    public static boolean undoLast(MinecraftServer server, PlayerEntity player) {
        return undoLast(server, player, null);
    }

    public static boolean undoLast(MinecraftServer server, PlayerEntity player, ServerCommandSource source) {
        if (activeUndoTask != null && activeUndoTask.isActive()) {
            sendFeedback(player, source, "§e正在撤回上一次地形变更，请等待完成...");
            return false;
        }

        boolean interruptedActive = false;
        while (!ACTIVE_FORWARD_TASKS.isEmpty()) {
            BaseTransformationTask task = ACTIVE_FORWARD_TASKS.get(ACTIVE_FORWARD_TASKS.size() - 1);
            if (task.isActive()) {
                task.cancelAndCommitForUndo();
                interruptedActive = true;
                break;
            } else {
                ACTIVE_FORWARD_TASKS.remove(ACTIVE_FORWARD_TASKS.size() - 1);
            }
        }

        TransformationRecord record = HISTORY.pollFirst();
        if (record == null || record.isEmpty()) {
            sendFeedback(player, source, "§7没有可撤回的地形变更记录");
            return false;
        }

        activeUndoTask = new UndoTransformationTask(server, record, player);
        if (interruptedActive) {
            sendFeedback(player, source, "§6已中断当前地形改造，开始反向撤回（共 " + record.getStepCount() + " 步）...");
        } else {
            sendFeedback(player, source, "§a开始撤回上一次地形变更（共 " + record.getStepCount() + " 步）...");
        }
        return true;
    }

    public static void clear() {
        HISTORY.clear();
        ACTIVE_FORWARD_TASKS.clear();
        activeUndoTask = null;
    }

    private static void sendFeedback(PlayerEntity player, ServerCommandSource source, String message) {
        Text text = Text.literal(message);
        if (source != null) {
            source.sendFeedback(() -> text, false);
        } else if (player != null) {
            player.sendMessage(text, false);
        }
    }
}
