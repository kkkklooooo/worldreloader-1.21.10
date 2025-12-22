package com.worldreloader;

import com.worldreloader.blocks.FrozenDaisyBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class FrozenDaisyItem extends BlockItem {

    public FrozenDaisyItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public ActionResult place(ItemPlacementContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        Direction side = context.getSide();

        // 确保只能放在顶部
        if (side != Direction.UP) {
            return ActionResult.FAIL;
        }

        // 检查是否可以放置
        BlockState placementState = this.getBlock().getPlacementState(context);
        if (placementState == null || !placementState.canPlaceAt(world, pos)) {
            return ActionResult.FAIL;
        }

        // 放置方块
        ActionResult result = super.place(context);

        // 放置成功时播放效果
        if (result.isAccepted()) {
            // 播放放置音效
            world.playSound(null, pos, SoundEvents.BLOCK_SNOW_PLACE,
                    SoundCategory.BLOCKS, 0.8F, 1.2F);

            // 生成放置粒子效果（只在客户端）
            if (world.isClient()) {
                for (int i = 0; i < 50; i++) {
                    double offsetX = (world.random.nextDouble() - 0.5) * 0.8;
                    double offsetY = world.random.nextDouble() * 0.5;
                    double offsetZ = (world.random.nextDouble() - 0.5) * 0.8;

                    world.addParticle(ParticleTypes.SNOWFLAKE,
                            pos.getX() + 0.5 + offsetX,
                            pos.getY() + offsetY,
                            pos.getZ() + 0.5 + offsetZ,
                            0.0, 0.0, 0.0);
                }

                // 冰晶闪光效果
                for (int i = 0; i < 50; i++) {
                    world.addParticle(ParticleTypes.GLOW,
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5,
                            (world.random.nextDouble() - 0.5) * 0.2,
                            0.05,
                            (world.random.nextDouble() - 0.5) * 0.2);
                }
            }
        }

        return result;
    }

}
