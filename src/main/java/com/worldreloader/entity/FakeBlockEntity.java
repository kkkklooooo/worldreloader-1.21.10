package com.worldreloader.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class FakeBlockEntity extends Entity {
    // 数据跟踪器，用于同步方块状态到客户端
    private static final TrackedData<Integer> BLOCK_STATE_ID =
            DataTracker.registerData(FakeBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final int MAX_TRAIL_LENGTH = 20;

    private final List<Vec3d> trailPositions = new ArrayList<>();
    // 配置参数
    private int maxAge = 400; // 5秒生命周期 (20 tick/秒)
    private float rotation = 0.0f;
    private float rotationSpeed;
    private float floatSpeed;
    private float fadeStart = 0.7f; // 在生命周期70%时开始淡出
    private Vec3d startPos;
    private int fadeAge; // 开始淡出的年龄

    public FakeBlockEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        // 随机旋转速度和上升速度
        this.rotationSpeed = (world.random.nextFloat() - 0.5f) * 6.0f;
        this.floatSpeed = 0.03f + world.random.nextFloat() * 0.02f;
        this.fadeAge = (int)(maxAge * fadeStart);
    }

    public FakeBlockEntity(World world, BlockPos pos, BlockState blockState) {
        this(ModEntities.FAKE_BLOCK, world);
        this.setPosition(Vec3d.ofCenter(pos));
        this.startPos = this.getPos();
        this.setBlockState(blockState);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(BLOCK_STATE_ID, 0); // 默认空气方块ID
    }
    public List<Vec3d> getTrailPositions() {
        return trailPositions;
    }
    // 基于速度预测轨迹
    public List<Vec3d> getPredictedTrail(float deltaTime, int steps) {
        List<Vec3d> predicted = new ArrayList<>();
        Vec3d currentPos = this.getPos();
        Vec3d velocity = this.getVelocity();

        for (int i = 0; i < steps; i++) {
            float time = i * deltaTime;
            Vec3d predictedPos = currentPos.add(velocity.multiply(time));
            predicted.add(predictedPos);
        }

        return predicted;
    }

    private void updateTrail() {
        trailPositions.add(this.getPos());
        if (trailPositions.size() > MAX_TRAIL_LENGTH) {
            trailPositions.remove(0);
        }
    }
    @Override
    public void tick() {
        super.tick();

        // 更新旋转
        rotation += rotationSpeed;

        // 计算上升速度（逐渐减慢）
        float progress = (float) age / maxAge;
        float currentSpeed = floatSpeed * (1.0f - progress * 0.5f);

        // 添加轻微的左右摆动效果
        double swingX = Math.sin(age * 0.1) * 0.01;
        double swingZ = Math.cos(age * 0.1) * 0.01;

        // 设置速度
        this.setVelocity(new Vec3d(0, currentSpeed, 0));
        this.move(MovementType.SELF, this.getVelocity());

        // 检查是否应该消失
        if (this.age > maxAge) {
            this.discard();
            return;
        }
        // Update trail and scale less frequently to improve performance
        if (this.age % 2 == 0) {
            updateTrail();
        }

        // 检查是否飞得太高
        if (this.getY() > 320) {
            this.discard();
        }
    }

    // 设置方块状态
    public void setBlockState(BlockState state) {
        int id = net.minecraft.block.Block.getRawIdFromState(state);
        this.dataTracker.set(BLOCK_STATE_ID, id);
    }

    // 获取方块状态
    public BlockState getBlockState() {
        int id = this.dataTracker.get(BLOCK_STATE_ID);
        return net.minecraft.block.Block.getStateFromRawId(id);
    }

    // 获取旋转角度（用于渲染）
    public float getRotation(float tickDelta) {
        return rotation + rotationSpeed * tickDelta;
    }

    // 获取透明度（用于淡出效果）
    public float getAlpha(float tickDelta) {
        int currentAge = age + (tickDelta > 0 ? 1 : 0);
        if (currentAge < fadeAge) {
            return 1.0f;
        }
        float fadeProgress = (float)(currentAge - fadeAge) / (maxAge - fadeAge);
        return 1.0f - fadeProgress;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("BlockState")) {
            this.dataTracker.set(BLOCK_STATE_ID, nbt.getInt("BlockState"));
        }
        if (nbt.contains("MaxAge")) {
            this.maxAge = nbt.getInt("MaxAge");
        }
        if (nbt.contains("Rotation")) {
            this.rotation = nbt.getFloat("Rotation");
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("BlockState", this.dataTracker.get(BLOCK_STATE_ID));
        nbt.putInt("MaxAge", maxAge);
        nbt.putFloat("Rotation", rotation);
    }


    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean doesNotCollide(double offsetX, double offsetY, double offsetZ) {
        return true;
    }

    @Override
    public boolean isFireImmune() {
        return true;
    }

    @Override
    public boolean hasNoGravity() {
        return true;
    }
}
