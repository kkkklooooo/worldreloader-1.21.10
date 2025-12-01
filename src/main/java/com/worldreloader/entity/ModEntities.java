package com.worldreloader.entity;

import com.worldreloader.WorldReloader;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class ModEntities {
    public static final EntityType<FakeBlockEntity> FAKE_BLOCK =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    Identifier.of(WorldReloader.MOD_ID, "fake_block"),
                    EntityType.Builder.<FakeBlockEntity>create(FakeBlockEntity::new,SpawnGroup.MISC)
                            .dimensions(0.5f,0.5f)
                            .maxTrackingRange(64)
                            .trackingTickInterval(3)
                            .alwaysUpdateVelocity(true)
                            .build()
            );

    public static void initialize() {
        WorldReloader.LOGGER.info("注册假方块实体");
    }
}
