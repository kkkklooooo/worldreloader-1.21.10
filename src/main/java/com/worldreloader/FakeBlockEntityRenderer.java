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
import org.joml.Quaternionf;

import java.util.List;

public class FakeBlockEntityRenderer extends EntityRenderer<FakeBlockEntity> {
    public FakeBlockEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }


    private static final Identifier TRAIL_TEXTURE = Identifier.of("textures/entity/beacon_beam.png");
    private static final float TRAIL_WIDTH = 0.3f; // 拖尾宽度
    private static final int MAX_TRAIL_LENGTH = 50; // 最大拖尾长度

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
        renderTrail3(entity,matrices,vertexConsumers,tickDelta);
        //renderTrail2(entity, matrices, vertexConsumers, tickDelta,10,new float[]{1,1,1},true);
        matrices.pop();
    }

    private void renderTrail3(FakeBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        List<Vec3d> trail = entity.getTrailPositions();
        if (trail.size() < 2) return;

        // 使用我们自定义的 Layer
        VertexConsumer buffer = vertexConsumers.getBuffer(MyRenderLayers.TRAIL_GLOW);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

        // 拖尾配置
        float width = 0.4f; // 稍微宽一点，因为边缘会淡出

        // 颜色 (RGBA) - 比如青色荧光
        float r = 0.2f;
        float g = 0.8f;
        float b = 1.0f;
        float maxAlpha = 1.0f; // Shader 会处理边缘透明，这里设为 1 即可

        // 插值位置
        double entityX = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double entityY = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double entityZ = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());

        for (int i = 0; i < trail.size() - 1; i++) {
            Vec3d startWorld = trail.get(i);
            Vec3d endWorld = trail.get(i + 1);
            Vec3d dir = endWorld.subtract(startWorld);
            if (dir.lengthSquared() < 0.0001) continue;

            // Billboarding 计算
            Vec3d toCamera = cameraPos.subtract(startWorld);
            Vec3d right = dir.crossProduct(toCamera).normalize().multiply(width / 2.0);

            // 顶点计算
            Vec3d v1 = startWorld.subtract(right);
            Vec3d v2 = startWorld.add(right);
            Vec3d v3 = endWorld.add(right);
            Vec3d v4 = endWorld.subtract(right);

            // 相对坐标
            float x1 = (float) (v1.x - entityX); float y1 = (float) (v1.y - entityY); float z1 = (float) (v1.z - entityZ);
            float x2 = (float) (v2.x - entityX); float y2 = (float) (v2.y - entityY); float z2 = (float) (v2.z - entityZ);
            float x3 = (float) (v3.x - entityX); float y3 = (float) (v3.y - entityY); float z3 = (float) (v3.z - entityZ);
            float x4 = (float) (v4.x - entityX); float y4 = (float) (v4.y - entityY); float z4 = (float) (v4.z - entityZ);

            // 沿路径长度的纹理坐标 U (可选，如果 Shader 不需要沿长度变化，可以忽略)
            // 这里重点是 V 坐标：边缘是 0 和 1，Shader 里会处理成透明

            // 头部渐隐 Alpha
            float alpha = maxAlpha * ((float) i / trail.size());

            // 绘制顶点
            // 关键点：
            // 1. .texture(u, v) -> v 必须是 0 和 1，用来在 shader 里计算距离中心的距离
            // 2. .color() -> 传入基础颜色
            // 3. 不再需要 .light()，因为 RenderLayer 设置了不写入深度且 Shader 忽略光照

            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, alpha).texture(0f, 0f); // V=0 边缘
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, alpha).texture(0f, 1f); // V=1 边缘
            buffer.vertex(matrix, x3, y3, z3).color(r, g, b, alpha).texture(1f, 1f); // V=1 边缘
            buffer.vertex(matrix, x4, y4, z4).color(r, g, b, alpha).texture(1f, 0f); // V=0 边缘
        }
    }

    private void renderTrail1(FakeBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        List<Vec3d> trail = entity.getTrailPositions();
        if (trail.size() < 2) return;

        // 限制拖尾长度
        int startIndex = Math.max(0, trail.size() - MAX_TRAIL_LENGTH);
        trail = trail.subList(startIndex, trail.size());

        // 获取当前摄像机位置和方向
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();
        Quaternionf cameraRotation = camera.getRotation();

        // 计算实体插值位置
        double entityX = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double entityY = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double entityZ = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());

        // 使用发光纹理渲染层
        RenderLayer renderLayer = RenderLayer.getEntityTranslucentEmissive(TRAIL_TEXTURE);
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(renderLayer);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

        // 自定义颜色和亮度 (可以传入参数或从entity获取)
        float red = 1.0f;   // R
        float green = 0.5f; // G
        float blue = 0.0f;  // B
        float baseAlpha = 0.8f; // 基础透明度
        int brightness = 0xF000F0; // 发光亮度 (0xF000F0是最大发光值)

        // 为每个线段生成四边形面片
        for (int i = 0; i < trail.size() - 1; i++) {
            Vec3d current = trail.get(i);
            Vec3d next = trail.get(i + 1);

            // 计算线段方向
            Vec3d segmentDir = next.subtract(current).normalize();

            // 计算面向摄像机的方向 (与视线垂直)
            Vec3d toCamera = new Vec3d(
                    cameraPos.x - (current.x + next.x) * 0.5,
                    cameraPos.y - (current.y + next.y) * 0.5,
                    cameraPos.z - (current.z + next.z) * 0.5
            ).normalize();

            // 计算四边形扩展方向 (垂直于线段和摄像机方向)
            Vec3d right = segmentDir.crossProduct(toCamera).normalize().multiply(TRAIL_WIDTH * 0.5f);
            Vec3d up = toCamera.crossProduct(right).normalize().multiply(TRAIL_WIDTH * 0.25f); // 稍微小一点的高度

            // 计算四个顶点 (形成面向摄像机的四边形)
            Vec3d[] vertices = new Vec3d[4];
            vertices[0] = current.add(right).add(up); // 右上
            vertices[1] = current.subtract(right).add(up); // 左上
            vertices[2] = next.subtract(right).subtract(up); // 左下
            vertices[3] = next.add(right).subtract(up); // 右下

            // 计算透明度渐变 (尾部更透明)
            float alpha = baseAlpha * (1.0f - (float)i / trail.size());

            // 转换到局部坐标
            for (int j = 0; j < vertices.length; j++) {
                vertices[j] = vertices[j].subtract(entityX, entityY, entityZ);
            }

            // 计算四边形法线 (面向摄像机)
            Vec3d normalVec = toCamera;

            // 渲染四边形 (两个三角形)
            renderQuad(vertexConsumer, matrix, matrices, vertices,
                    red, green, blue, alpha, brightness, normalVec, i);
        }
    }

    private void renderQuad(VertexConsumer vertexConsumer, Matrix4f matrix, MatrixStack normalMatrix,
                            Vec3d[] vertices, float red, float green, float blue, float alpha,
                            int brightness, Vec3d normalVec, int segmentIndex) {

        if (vertices.length != 4) return;

        // 将法线转换为float
        float nx = (float) normalVec.x;
        float ny = (float) normalVec.y;
        float nz = (float) normalVec.z;

        // 计算UV坐标 (根据段索引创建渐变效果)
        float u1 = 0.0f;
        float u2 = 1.0f;
        float v1 = (float) segmentIndex / MAX_TRAIL_LENGTH;
        float v2 = (float) (segmentIndex + 1) / MAX_TRAIL_LENGTH;

        // 顶点1
        vertexConsumer.vertex(matrix,
                        (float) vertices[0].x, (float) vertices[0].y, (float) vertices[0].z)
                .color(red, green, blue, alpha)
                .texture(u2, v1)
                .overlay(0) // 通常为0
                .light(brightness) // 使用发光亮度
                .normal(normalMatrix.peek(), nx, ny, nz);

        // 顶点2
        vertexConsumer.vertex(matrix,
                        (float) vertices[1].x, (float) vertices[1].y, (float) vertices[1].z)
                .color(red, green, blue, alpha)
                .texture(u1, v1)
                .overlay(0)
                .light(brightness)
                .normal(normalMatrix.peek(), nx, ny, nz);

        // 顶点3
        vertexConsumer.vertex(matrix,
                        (float) vertices[2].x, (float) vertices[2].y, (float) vertices[2].z)
                .color(red, green, blue, alpha * 0.7f) // 尾部更透明
                .texture(u1, v2)
                .overlay(0)
                .light(brightness)
                .normal(normalMatrix.peek(), nx, ny, nz);

        // 顶点4
        vertexConsumer.vertex(matrix,
                        (float) vertices[3].x, (float) vertices[3].y, (float) vertices[3].z)
                .color(red, green, blue, alpha * 0.7f) // 尾部更透明
                .texture(u2, v2)
                .overlay(0)
                .light(brightness)
                .normal(normalMatrix.peek(), nx, ny, nz);
        // 绘制第二个三角形来完成四边形
        vertexConsumer.vertex(matrix,
                        (float) vertices[0].x, (float) vertices[0].y, (float) vertices[0].z)
                .color(red, green, blue, alpha)
                .texture(u2, v1)
                .overlay(0)
                .light(brightness)
                .normal(normalMatrix.peek(), nx, ny, nz);

        vertexConsumer.vertex(matrix,
                        (float) vertices[2].x, (float) vertices[2].y, (float) vertices[2].z)
                .color(red, green, blue, alpha * 0.7f)
                .texture(u1, v2)
                .overlay(0)
                .light(brightness)
                .normal(normalMatrix.peek(), nx, ny, nz);

        vertexConsumer.vertex(matrix,
                        (float) vertices[3].x, (float) vertices[3].y, (float) vertices[3].z)
                .color(red, green, blue, alpha * 0.7f)
                .texture(u2, v2)
                .overlay(0)
                .light(brightness)
                .normal(normalMatrix.peek(), nx, ny, nz);
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


    private void renderTrail2(FakeBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta, float brightness, float[] color, boolean useQuads) {
        List<Vec3d> trail = entity.getTrailPositions().subList(0, Math.max(0, entity.getTrailPositions().size() - 10));
        if (trail.size() < 2) return;

        if (useQuads) {
            // 渲染为面片，正对摄像机
            renderTrailAsQuads(entity, matrices, vertexConsumers, tickDelta, brightness, color);
        } else {
            // 渲染为线段
            renderTrailAsLines(entity, matrices, vertexConsumers, tickDelta, brightness, color);
        }
    }

    private void renderTrailAsQuads(FakeBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta, float brightness, float[] color) {
        List<Vec3d> trail = entity.getTrailPositions().subList(0, Math.max(0, entity.getTrailPositions().size() - 10));
        if (trail.size() < 2) return;

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentCull(TRAIL_TEXTURE));
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

        // 获取实体当前的插值位置
        double entityX = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double entityY = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double entityZ = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());

        // 获取摄像机方向用于面片朝向
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();

        float quadWidth = 0.1f; // 面片宽度

        for (int i = 0; i < trail.size() - 1; i++) {
            Vec3d startWorld = trail.get(i);
            Vec3d endWorld = trail.get(i + 1);

            // 转为局部坐标
            float x1 = (float) (startWorld.x - entityX);
            float y1 = (float) (startWorld.y - entityY);
            float z1 = (float) (startWorld.z - entityZ);

            float x2 = (float) (endWorld.x - entityX);
            float y2 = (float) (endWorld.y - entityY);
            float z2 = (float) (endWorld.z - entityZ);

            float alpha = (float) i / trail.size();
            float segmentBrightness = brightness * alpha;

            // 计算线段中点作为面片中心
            float centerX = (x1 + x2) * 0.5f;
            float centerY = (y1 + y2) * 0.5f;
            float centerZ = (z1 + z2) * 0.5f;

            // 计算线段方向向量
            Vec3d segmentDir = new Vec3d(x2 - x1, y2 - y1, z2 - z1).normalize();
            Vec3d segmentCenter = new Vec3d(centerX, centerY, centerZ);

            // 计算垂直于线段和摄像机方向的向量（用于面片宽度）
            Vec3d toCamera = new Vec3d(
                    cameraPos.x - (entityX + centerX),
                    cameraPos.y - (entityY + centerY),
                    cameraPos.z - (entityZ + centerZ)
            ).normalize();

            Vec3d cross = segmentDir.crossProduct(toCamera).normalize();
            Vec3d offset = cross.multiply(quadWidth * 0.5f);

            // 四个顶点坐标
            float xLeft1 = x1 - (float) offset.x;
            float yLeft1 = y1 - (float) offset.y;
            float zLeft1 = z1 - (float) offset.z;

            float xRight1 = x1 + (float) offset.x;
            float yRight1 = y1 + (float) offset.y;
            float zRight1 = z1 + (float) offset.z;

            float xLeft2 = x2 - (float) offset.x;
            float yLeft2 = y2 - (float) offset.y;
            float zLeft2 = z2 - (float) offset.z;

            float xRight2 = x2 + (float) offset.x;
            float yRight2 = y2 + (float) offset.y;
            float zRight2 = z2 + (float) offset.z;

            // 第一个三角形 (左上, 右上, 左下)
            addVertex(vertexConsumer, matrix, xLeft1, yLeft1, zLeft1, color[0], color[1], color[2], segmentBrightness, alpha, 0, 0);
            addVertex(vertexConsumer, matrix, xRight1, yRight1, zRight1, color[0], color[1], color[2], segmentBrightness, alpha, 1, 0);
            addVertex(vertexConsumer, matrix, xLeft2, yLeft2, zLeft2, color[0], color[1], color[2], segmentBrightness, alpha, 0, 1);

            // 第二个三角形 (右上, 右下, 左下)
            addVertex(vertexConsumer, matrix, xRight1, yRight1, zRight1, color[0], color[1], color[2], segmentBrightness, alpha, 1, 0);
            addVertex(vertexConsumer, matrix, xRight2, yRight2, zRight2, color[0], color[1], color[2], segmentBrightness, alpha, 1, 1);
            addVertex(vertexConsumer, matrix, xLeft2, yLeft2, zLeft2, color[0], color[1], color[2], segmentBrightness, alpha, 0, 1);
        }
    }

    private void renderTrailAsLines(FakeBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta, float brightness, float[] color) {
        List<Vec3d> trail = entity.getTrailPositions().subList(0, Math.max(0, entity.getTrailPositions().size() - 10));
        if (trail.size() < 2) return;

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // 获取实体当前的插值位置
        double entityX = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double entityY = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double entityZ = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());

        for (int i = 0; i < trail.size() - 1; i++) {
            Vec3d startWorld = trail.get(i);
            Vec3d endWorld = trail.get(i + 1);

            // 转为局部坐标
            float x1 = (float) (startWorld.x - entityX);
            float y1 = (float) (startWorld.y - entityY);
            float z1 = (float) (startWorld.z - entityZ);

            float x2 = (float) (endWorld.x - entityX);
            float y2 = (float) (endWorld.y - entityY);
            float z2 = (float) (endWorld.z - entityZ);

            float alpha = (float) i / trail.size();
            float segmentBrightness = brightness * alpha;

            // 设置颜色和亮度
            int light = 0xF000F0; // 最大亮度
            vertexConsumer.vertex(matrix, x1, y1, z1)
                    .color(color[0], color[1], color[2], alpha)
                    .light(light)
                    .normal(matrices.peek(), 0, 1, 0);

            vertexConsumer.vertex(matrix, x2, y2, z2)
                    .color(color[0], color[1], color[2], alpha)
                    .light(light)
                    .normal(matrices.peek(), 0, 1, 0);
        }
    }

    private void addVertex(VertexConsumer vertexConsumer, Matrix4f matrix,
                           float x, float y, float z,
                           float r, float g, float b,
                           float brightness, float alpha,
                           float u, float v) {
        // 使用自定义亮度和颜色
        vertexConsumer.vertex(matrix, x, y, z)
                .color(r * brightness, g * brightness, b * brightness, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0) // 发光效果
                .normal(0, 1, 0);
    }

    @Override
    public Identifier getTexture(FakeBlockEntity entity) {
        // 返回一个默认纹理，用于能量场效果
        return Identifier.of("textures/entity/beacon_beam.png");
    }
}