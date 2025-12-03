package com.worldreloader;

import com.worldreloader.entity.FakeBlockEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

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

        matrices.push();
        renderTrail(entity,matrices,vertexConsumers,tickDelta);
        matrices.pop();
    }



//    private void renderTrail(FakeBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
//        List<Vec3d> trail = entity.getTrailPositions().subList(0,Math.max(0,entity.getTrailPositions().size()-10));
//        if (trail.size() < 2) return;
//
//        // 使用 LINES 模式 (LineStrip 在某些版本很难直接获取，LINES 更通用)
//        // 注意：Minecraft 原版渲染线段通常使用 debug lines，宽度固定。
//        // 如果想要很酷的拖尾，通常需要画成面（Quad）而不是线。这里先修复线段的显示。
//        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
//        Matrix4f matrix = matrices.peek().getPositionMatrix();
//        Matrix3f normalMatrix = matrices.peek().getNormalMatrix(); // 获取法线矩阵
//
//        // 获取实体当前的插值位置 (用于将世界坐标转换为相对坐标)
//        double entityX = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
//        double entityY = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
//        double entityZ = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());
//
//        for (int i = 0; i < trail.size() - 1; i++) {
//            Vec3d startWorld = trail.get(i);
//            Vec3d endWorld = trail.get(i + 1);
//
//            // 关键步骤：转为局部坐标
//            float x1 = (float) (startWorld.x - entityX);
//            float y1 = (float) (startWorld.y - entityY);
//            float z1 = (float) (startWorld.z - entityZ);
//
//            float x2 = (float) (endWorld.x - entityX);
//            float y2 = (float) (endWorld.y - entityY);
//            float z2 = (float) (endWorld.z - entityZ);
//
//            float alpha = (float) i / trail.size();
//
//            // LINES buffer 只接受 Position, Color, Normal (通常)
//            // 必须成对绘制
//            vertexConsumer.vertex(matrix, x1, y1, z1)
//                    .color(1f, 0.8f, 0.2f, alpha)
//                    .normal(matrices.peek(), 0, 1, 0);
//
//            vertexConsumer.vertex(matrix, x2, y2, z2)
//                    .color(1f, 0.8f, 0.2f, alpha)
//                    .normal(matrices.peek(), 0, 1, 0);
//        }
//    }


    // 在你的 EntityRenderer 类中
    private void renderTrail(FakeBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        List<Vec3d> trail = entity.getTrailPositions();
        // 截取最近的 N 个点，或者全部，根据需求
        // List<Vec3d> trailSegment = trail.subList(0, Math.max(0, trail.size() - 10));
        // 如果 trail 太短，直接返回
        if (trail.size() < 2) return;

        // --- 配置区域 ---
        float width = 0.2f; // 拖尾宽度
        // 颜色配置 (例如：金黄色)
        float red = 1.0f;
        float green = 0.8f;
        float blue = 0.2f;
        float maxAlpha = 0.8f; // 头部最大透明度

        // --- 渲染层选择 ---
        // 1. 如果想要类似信标的光束效果（半透明+发光），推荐 use RenderLayer.getBeaconBeam(Identifier, boolean)
        // 2. 为了简单演示，这里使用 getLightning (闪电) 或 getEntityTranslucent (配合纯白纹理)
        // 注意：如果没有纹理，Lightning 是个不错的选择，因为它是纯色且发光的。
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getLightning());

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // 获取摄像机在世界坐标的位置 (用于计算面片朝向)
        Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

        // 实体插值位置 (用于将 trail 的世界坐标转为相对于当前矩阵的局部坐标)
        double entityX = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double entityY = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double entityZ = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());

        for (int i = 0; i < trail.size() - 1; i++) {
            Vec3d startWorld = trail.get(i);
            Vec3d endWorld = trail.get(i + 1);

            // 1. 计算当前片段的方向
            Vec3d dir = endWorld.subtract(startWorld);
            if (dir.lengthSquared() < 0.0001) continue; // 防止重合点导致错误

            // 2. 计算从片段起点到摄像机的向量
            Vec3d toCamera = cameraPos.subtract(startWorld);

            // 3. 计算“右方”向量 (Billboarding 核心)
            // 通过 视线 x 路径方向，得到垂直于两者的向量，这就是面片的宽度方向
            Vec3d right = dir.crossProduct(toCamera).normalize().multiply(width / 2.0);

            // 4. 计算四个顶点的世界坐标
            Vec3d v1 = startWorld.subtract(right); // 起点左
            Vec3d v2 = startWorld.add(right);      // 起点右
            Vec3d v3 = endWorld.add(right);        // 终点右
            Vec3d v4 = endWorld.subtract(right);   // 终点左

            // 5. 转为局部坐标 (相对于当前 Entity 渲染矩阵)
            // 因为 matrices 已经被推到了 Entity 的位置，所以我们需要减去 Entity 的插值位置
            float x1 = (float) (v1.x - entityX);
            float y1 = (float) (v1.y - entityY);
            float z1 = (float) (v1.z - entityZ);

            float x2 = (float) (v2.x - entityX);
            float y2 = (float) (v2.y - entityY);
            float z2 = (float) (v2.z - entityZ);

            float x3 = (float) (v3.x - entityX);
            float y3 = (float) (v3.y - entityY);
            float z3 = (float) (v3.z - entityZ);

            float x4 = (float) (v4.x - entityX);
            float y4 = (float) (v4.y - entityY);
            float z4 = (float) (v4.z - entityZ);

            // 6. 计算渐变 Alpha
            // 头部(索引小)透明度高，尾部(索引大)透明度低，或者反过来，取决于你的列表顺序
            // 假设 list 0 是旧的尾巴，size-1 是新的头部：
            float alphaProgressCurrent = (float) i / trail.size();
            float alphaProgressNext = (float) (i + 1) / trail.size();

            float alpha1 = maxAlpha * alphaProgressCurrent;
            float alpha2 = maxAlpha * alphaProgressNext;

            // 7. 绘制四边形 (Quad)
            // 必须按逆时针或顺时针顺序绘制
            addVertex(buffer, matrix, x1, y1, z1, red, green, blue, alpha1);
            addVertex(buffer, matrix, x2, y2, z2, red, green, blue, alpha1);
            addVertex(buffer, matrix, x3, y3, z3, red, green, blue, alpha2);
            addVertex(buffer, matrix, x4, y4, z4, red, green, blue, alpha2);
        }
    }

    // 辅助方法：添加单个顶点
    private void addVertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float r, float g, float b, float a) {
        buffer.vertex(matrix, x, y, z)
                .color(r, g, b, a)
                 .texture(0, 0) // 如果使用带纹理的 RenderLayer，这里需要 UV 坐标
                 .overlay(OverlayTexture.DEFAULT_UV) // 部分 Layer 需要 overlay
                 .light(LightmapTextureManager.MAX_LIGHT_COORDINATE) // 关键：全亮 (0xF000F0)
                 //RenderLayer.getLightning() //不需要 overlay/light/normal，它会自动发光
                // 如果换成 RenderLayer.getEntityTranslucent，则必须取消下面两行的注释：
                 .overlay(OverlayTexture.DEFAULT_UV)
                 .light(0xF000F0);
    }


    @Override
    public Identifier getTexture(FakeBlockEntity entity) {
        // 返回一个默认纹理，用于能量场效果
        return Identifier.of("textures/entity/beacon_beam.png");
    }
}