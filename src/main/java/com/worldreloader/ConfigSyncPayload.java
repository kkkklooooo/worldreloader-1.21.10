package com.worldreloader;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ConfigSyncPayload(String json) implements CustomPayload {
    public static final Id<ConfigSyncPayload> ID = new Id<>(Identifier.of(WorldReloader.MOD_ID, "config_sync"));
    public static final PacketCodec<RegistryByteBuf, ConfigSyncPayload> CODEC = CustomPayload.codecOf(ConfigSyncPayload::write, ConfigSyncPayload::new);

    public ConfigSyncPayload(RegistryByteBuf buf) {
        this(buf.readString(32767));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(json, 32767);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
