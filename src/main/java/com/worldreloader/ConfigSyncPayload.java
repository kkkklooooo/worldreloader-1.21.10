package com.worldreloader;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class ConfigSyncPayload {
    private final String json;

    public ConfigSyncPayload(String json) {
        this.json = json;
    }

    public ConfigSyncPayload(PacketByteBuf buf) {
        this.json = buf.readString(32767);
    }

    public void write(PacketByteBuf buf) {
        buf.writeString(json, 32767);
    }

    public String getJson() {
        return json;
    }

    public static Identifier getChannel() {
        return new Identifier(WorldReloader.MOD_ID, "config_sync");
    }
}