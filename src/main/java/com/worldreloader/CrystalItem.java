package com.worldreloader;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Objects;

public class CrystalItem extends Item {
    public CrystalItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient()) {
            // 只在服务器端执行
            Objects.requireNonNull(world.getServer()).execute(() -> {
                // 开始地形转换
                WorldReloader.instance.startTerrainTransformation((ServerWorld) world, user.getBlockPos(), user);

                // 移除物品（除非是创造模式）
                if (!user.isCreative()) {
                    stack.decrement(1);
                }

                // 添加粒子效果和声音
                addTransformationEffects((ServerWorld) world, user.getPos());

            });
        }

        // 在客户端也播放声音（如果希望在客户端立即有反馈）
        if (world.isClient()) {
            world.playSound(user, user.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                    SoundCategory.PLAYERS, 1.0F, 0.8F + world.random.nextFloat() * 0.4F);
        }

        return TypedActionResult.success(stack, world.isClient());
    }


    private void addTransformationEffects(ServerWorld world, Vec3d position) {
        BlockPos pos = new BlockPos((int)position.x, (int)position.y, (int)position.z);

        // 播放声音
        world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS, 1.2F, 0.9F);
        world.playSound(null, pos, SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL,
                SoundCategory.PLAYERS, 0.8F, 1.2F);

        // 生成粒子效果
        for (int i = 0; i < 400; i++) {
            double offsetX = world.random.nextDouble() * 4.0 - 2.0;
            double offsetY = world.random.nextDouble() * 4.0;
            double offsetZ = world.random.nextDouble() * 4.0 - 2.0;

            // 多种粒子效果组合
            if (i % 4 == 0) {
                // 紫水晶粒子
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        position.x + offsetX, position.y + offsetY, position.z + offsetZ,
                        5, 0.2, 0.2, 0.2, 0.05);
            } else if (i % 4 == 1) {
                // 魔法粒子
                world.spawnParticles(ParticleTypes.ENCHANT,
                        position.x + offsetX, position.y + offsetY, position.z + offsetZ,
                        3, 0.3, 0.3, 0.3, 0.1);
            } else if (i % 4 == 2) {
                // 紫色颗粒
                world.spawnParticles(ParticleTypes.WITCH,
                        position.x + offsetX, position.y + offsetY, position.z + offsetZ,
                        2, 0.25, 0.25, 0.25, 0.02);
            } else {
                // 发光粒子
                world.spawnParticles(ParticleTypes.GLOW,
                        position.x + offsetX, position.y + offsetY, position.z + offsetZ,
                        1, 0.1, 0.1, 0.1, 0.0);
            }
        }

    }
}