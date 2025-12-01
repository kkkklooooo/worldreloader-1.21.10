package com.worldreloader;

import com.worldreloader.entity.FakeBlockEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class FakeBlockEntityRenderer extends EntityRenderer<FakeBlockEntity> {
    public FakeBlockEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(FakeBlockEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        BlockState blockState = entity.getBlockState();
        if (blockState == null || blockState.getRenderType() != BlockRenderType.MODEL) {
            return;
        }

        matrices.push();

        // 计算实体插值位置
        double x = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double z = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());

        // 移动到实体位置
        matrices.translate(x - this.dispatcher.camera.getPos().x,
                y - this.dispatcher.camera.getPos().y,
                z - this.dispatcher.camera.getPos().z);

        // 应用旋转动画
        float rotation = entity.getRotation(tickDelta);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));

        // 添加轻微的上下浮动效果
        float floatOffset = (float) Math.sin((entity.age + tickDelta) * 0.2) * 0.05f;
        matrices.translate(0, floatOffset, 0);

        // 应用缩放（可选：轻微的大小变化）
        float scale = 0.9f + (float) Math.sin((entity.age + tickDelta) * 0.3) * 0.1f;
        matrices.scale(scale, scale, scale);

        // 方块渲染需要以方块中心为原点
        matrices.translate(-0.5, 0, -0.5);

        // 获取透明度
        float alpha = entity.getAlpha(tickDelta);

        // 渲染方块
        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();

        // 使用半透明渲染层（如果方块支持透明）
        if (alpha < 1.0f && !blockState.isOpaque()) {
            VertexConsumer transparentConsumer = vertexConsumers.getBuffer(
                    RenderLayer.getTranslucent()
            );
            // 注意：原版方块渲染器不支持直接设置透明度
            // 我们需要使用一个自定义的渲染方法或着色器
            // 这里使用简化方案：只在不透明时渲染
            blockRenderManager.renderBlockAsEntity(blockState, matrices,
                    vertexConsumers, light, OverlayTexture.DEFAULT_UV);
        } else {
            blockRenderManager.renderBlockAsEntity(blockState, matrices,
                    vertexConsumers, light, OverlayTexture.DEFAULT_UV);
        }



        matrices.pop();
    }



    @Override
    public Identifier getTexture(FakeBlockEntity entity) {
        // 返回一个默认纹理，用于能量场效果
        return Identifier.of("textures/entity/beacon_beam.png");
    }
}