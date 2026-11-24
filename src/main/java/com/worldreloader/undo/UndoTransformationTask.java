package com.worldreloader.undo;

import com.worldreloader.WorldReloader;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Replays a {@link TransformationRecord} in reverse step order, one snapshot per server tick.
 */
public final class UndoTransformationTask {
    private final MinecraftServer server;
    private final TransformationRecord record;
    private final PlayerEntity initiator;
    private final List<ChunkPos> forcedChunks = new ArrayList<>();

    private int nextStepIndex;
    private boolean chunksForced = false;
    private boolean active = true;

    public UndoTransformationTask(MinecraftServer server, TransformationRecord record, PlayerEntity initiator) {
        this.server = server;
        this.record = record;
        this.initiator = initiator;
        this.nextStepIndex = record.getStepCount() - 1;
    }

    public boolean isActive() {
        return active;
    }

    public void tick() {
        if (!active) {
            return;
        }

        ServerWorld world = server.getWorld(record.getDimensionKey());
        if (world == null) {
            active = false;
            return;
        }

        if (!chunksForced) {
            LongIterator it = record.getAffectedChunks().iterator();
            while (it.hasNext()) {
                ChunkPos chunkPos = new ChunkPos(it.nextLong());
                world.setChunkForced(chunkPos.x, chunkPos.z, true);
                forcedChunks.add(chunkPos);
            }
            chunksForced = true;
        }

        if (nextStepIndex < 0) {
            cleanupChunks(world);
            active = false;
            if (initiator != null) {
                WorldReloader.logChat("§a地形变更撤回完成！");
            }
            return;
        }

        StepSnapshot step = record.getSteps().get(nextStepIndex);
        step.restore(world);
        if (WorldReloader.config != null && WorldReloader.config.Debug && initiator != null) {
            int done = record.getStepCount() - nextStepIndex;
            WorldReloader.logChat("§e撤回进度: " + done + "/" + record.getStepCount());
        }
        nextStepIndex--;
    }

    private void cleanupChunks(ServerWorld world) {
        for (ChunkPos chunkPos : forcedChunks) {
            world.setChunkForced(chunkPos.x, chunkPos.z, false);
        }
        forcedChunks.clear();
        chunksForced = false;
    }
}
