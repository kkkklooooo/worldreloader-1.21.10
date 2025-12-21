package com.worldreloader;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Objects;

public class CrystalItem extends Item {
    public CrystalItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        BlockPos pos = null;
        
        if( world.getServer()!=null){
            BlockPos playerPos = user.getBlockPos();
            int range = 100;
            
            // 从玩家位置向外扩展搜索
            for (int r = 0; r <= range && pos == null; r++) {
                for (int x = -r; x <= r && pos == null; x++) {
                    for (int y = -r; y <= r && pos == null; y++) {
                        for (int z = -r; z <= r && pos == null; z++) {
                            // 只检查边缘位置
                            if (Math.abs(x) == r || Math.abs(y) == r || Math.abs(z) == r) {
                                BlockPos checkPos = playerPos.add(x, y, z);
                                if (world.getBlockState(checkPos).getBlock() == Blocks.BEACON) {
                                    pos = checkPos;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

           //if(pos==null) user.sendMessage(Text.literal("未找到信标方块"));
           BlockPos finalPos = pos;
           Objects.requireNonNull(world.getServer()).execute(() -> {
               WorldReloader.instance.startTerrainTransformation((ServerWorld) world, user.getBlockPos(), user);
           });
       }
        return super.use(world, user, hand);
    }

}
