package com.worldreloader.blocks;


import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

public class FrozenDaisyBlock extends PlantBlock {
    protected static final VoxelShape SHAPE = Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 10.0, 14.0);

    public static final MapCodec<FrozenDaisyBlock> CODEC = createCodec(FrozenDaisyBlock::new);
    public FrozenDaisyBlock(Settings settings) {
        super(settings
                .sounds(BlockSoundGroup.GRASS)
                .strength(0.0f)
                .noCollision()
                .nonOpaque()
                .offset(OffsetType.XZ)
                .solid()


        );
    }

    @Override
    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    protected MapCodec<? extends PlantBlock> getCodec() {
        return CODEC;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos downPos = pos.down();
        return this.canPlantOnTop(world.getBlockState(downPos), world, downPos);
    }

    @Override
    protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
        return floor.isOf(Blocks.GRASS_BLOCK) ||
                floor.isOf(Blocks.DIRT) ||
                floor.isOf(Blocks.COARSE_DIRT) ||
                floor.isOf(Blocks.PODZOL) ||
                floor.isOf(Blocks.FARMLAND) ||
                floor.isOf(Blocks.SNOW_BLOCK) ||
                floor.isOf(Blocks.ICE) ||
                floor.isOf(Blocks.PACKED_ICE);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        super.randomDisplayTick(state, world, pos, random);

        // 只在客户端生成粒子效果
        if (world.isClient()) {
            for(int i=0;i<30;i++) {
                double x = pos.getX() + (random.nextDouble()-0.5);
                double y = pos.getY() + 0.2 + random.nextDouble() * 2.5;
                double z = pos.getZ() + (random.nextDouble()-0.5);

                world.addParticle(ParticleTypes.SNOWFLAKE,
                        x, y, z,
                        0.3*(random.nextDouble()-0.5), 0.0, 0.3*(random.nextDouble()-0.5));


                double x1 = pos.getX() + (random.nextDouble()-0.5);
                double y1 = pos.getY()+0.1 + random.nextDouble() * 3;
                double z1 = pos.getZ() + (random.nextDouble()-0.5);

                world.addParticle(ParticleTypes.GLOW,
                        x1, y1, z1,
                        0.3*(random.nextDouble()-0.5), 0.1, 0.3*(random.nextDouble()-0.5));


                double x2 = pos.getX() + 0.5;
                double y2 = pos.getY() + 0.8;
                double z2 = pos.getZ() + 0.5;

                    double offsetX = (random.nextDouble() - 0.5) * 0.5;
                    double offsetZ = (random.nextDouble() - 0.5) * 0.5;

                    world.addParticle(ParticleTypes.END_ROD,
                            x2 + offsetX, y2, z2 + offsetZ,
                            offsetX, 0.2, offsetZ);
            }


        }
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
