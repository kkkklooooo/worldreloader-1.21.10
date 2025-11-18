package com.worldreloader;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

public class LineTransformationTask extends BaseTransformationTask {
    private final BlockPos startPos;
    private final BlockPos endPos;
    private final BlockPos referenceStart;
    private final BlockPos referenceEnd;
    private final int lineWidth;
    private final int yMin;
    private final int yMax;

    // 用于Bresenham算法计算线段上的点
    private List<BlockPos> linePoints;
    private int currentLineIndex;

    public LineTransformationTask(ServerWorld world, BlockPos startPos, BlockPos endPos,
                                  BlockPos referenceStart, BlockPos referenceEnd,
                                  PlayerEntity player) {
        super(world, calculateCenter(startPos, endPos), calculateCenter(referenceStart, referenceEnd),
                player, calculateRadius(startPos, endPos),
                WorldReloader.config.totalSteps2,
                WorldReloader.config.itemCleanupInterval);

        this.startPos = startPos;
        this.endPos = endPos;
        this.referenceStart = referenceStart;
        this.referenceEnd = referenceEnd;
        this.lineWidth = WorldReloader.config.lineWidth; // 需要配置中添加线段宽度
        this.yMin = WorldReloader.config.yMin;
        this.yMax = WorldReloader.config.yMaxThanSurface;

        // 预计算线段上的所有点
        this.linePoints = calculateLinePoints(startPos, endPos);
        this.currentLineIndex = 0;
    }

    // 计算线段中心点
    private static BlockPos calculateCenter(BlockPos start, BlockPos end) {
        return new BlockPos(
                (start.getX() + end.getX()) / 2,
                (start.getY() + end.getY()) / 2,
                (start.getZ() + end.getZ()) / 2
        );
    }

    // 计算线段半径（最大距离的一半）
    private static int calculateRadius(BlockPos start, BlockPos end) {
        int dx = Math.abs(end.getX() - start.getX());
        int dz = Math.abs(end.getZ() - start.getZ());
        return (int) (Math.sqrt(dx * dx + dz * dz) / 2) + WorldReloader.config.lineWidth;
    }

    protected List<BlockPos> generatePositions() {
        List<BlockPos> allPositions = new ArrayList<>();

        // 为线段上的每个点生成周围区域
        for (BlockPos linePoint : linePoints) {
            addPositionsAroundPoint(linePoint, allPositions);
        }

        return allPositions;
    }

    // 为线段上的一个点生成周围区域
    private void addPositionsAroundPoint(BlockPos center, List<BlockPos> positions) {
        for (int x = -lineWidth; x <= lineWidth; x++) {
            for (int z = -lineWidth; z <= lineWidth; z++) {
                // 圆形区域检查，避免方形角落
                if (x * x + z * z <= lineWidth * lineWidth) {
                    BlockPos pos = new BlockPos(center.getX() + x, center.getY(), center.getZ() + z);
                    positions.add(pos);
                }
            }
        }
    }

    @Override
    protected void processPosition(BlockPos targetPos) {
        int targetX = targetPos.getX();
        int targetZ = targetPos.getZ();

        if (!world.isChunkLoaded(targetX >> 4, targetZ >> 4)) {
            return;
        }

        // 找到目标点在线段上的最近点
        BlockPos nearestOnLine = findNearestPointOnLine(targetPos, startPos, endPos);
        if (nearestOnLine == null) return;

        // 计算对应的参考点
        BlockPos referencePos = calculateReferencePoint(nearestOnLine);
        if (!world.isChunkLoaded(referencePos.getX() >> 4, referencePos.getZ() >> 4)) {
            return;
        }

        int originalSurfaceY = world.getChunk(targetX >> 4, targetZ >> 4)
                .getHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES)
                .get(targetX & 15, targetZ & 15);

        destroyAtPosition(targetX, targetZ);
        ReferenceTerrainInfo referenceInfo = getReferenceTerrainInfo(referencePos.getX(), referencePos.getZ());
        if (referenceInfo != null) {
            copyFromReference(targetX, targetZ, referenceInfo, originalSurfaceY);
        }
    }

    // 找到目标点在线段上的最近点
    private BlockPos findNearestPointOnLine(BlockPos point, BlockPos lineStart, BlockPos lineEnd) {
        Vec3d A = new Vec3d(lineStart.getX(), lineStart.getY(), lineStart.getZ());
        Vec3d B = new Vec3d(lineEnd.getX(), lineEnd.getY(), lineEnd.getZ());
        Vec3d P = new Vec3d(point.getX(), point.getY(), point.getZ());

        Vec3d AB = B.subtract(A);
        Vec3d AP = P.subtract(A);

        double magnitudeAB = AB.lengthSquared();
        if (magnitudeAB == 0) return lineStart;

        double t = AP.dotProduct(AB) / magnitudeAB;
        t = Math.max(0, Math.min(1, t)); // 限制在线段范围内

        Vec3d projection = A.add(AB.multiply(t));
        return new BlockPos((int)Math.round(projection.x), (int)Math.round(projection.y), (int)Math.round(projection.z));
    }

    // 计算对应的参考点
    private BlockPos calculateReferencePoint(BlockPos targetPoint) {
        // 计算目标点在线段上的比例
        double targetRatio = calculatePointRatio(targetPoint, startPos, endPos);

        // 在参考线段上应用相同的比例
        return interpolatePoint(referenceStart, referenceEnd, targetRatio);
    }

    // 计算点在线段上的比例
    private double calculatePointRatio(BlockPos point, BlockPos start, BlockPos end) {
        double lineLength = Math.sqrt(
                Math.pow(end.getX() - start.getX(), 2) +
                        Math.pow(end.getZ() - start.getZ(), 2)
        );

        if (lineLength == 0) return 0;

        double pointDistance = Math.sqrt(
                Math.pow(point.getX() - start.getX(), 2) +
                        Math.pow(point.getZ() - start.getZ(), 2)
        );

        return pointDistance / lineLength;
    }

    // 在线段上插值点
    private BlockPos interpolatePoint(BlockPos start, BlockPos end, double ratio) {
        int x = (int) (start.getX() + (end.getX() - start.getX()) * ratio);
        int z = (int) (start.getZ() + (end.getZ() - start.getZ()) * ratio);
        int y = (int) (start.getY() + (end.getY() - start.getY()) * ratio);

        return new BlockPos(x, y, z);
    }

    // 使用Bresenham算法计算线段上的所有点
    private List<BlockPos> calculateLinePoints(BlockPos start, BlockPos end) {
        List<BlockPos> points = new ArrayList<>();
        int x1 = start.getX(), y1 = start.getY(), z1 = start.getZ();
        int x2 = end.getX(), y2 = end.getY(), z2 = end.getZ();

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);

        int xs = x2 > x1 ? 1 : -1;
        int ys = y2 > y1 ? 1 : -1;
        int zs = z2 > z1 ? 1 : -1;

        // 驱动轴是变化最大的轴
        if (dx >= dy && dx >= dz) {
            int p1 = 2 * dy - dx;
            int p2 = 2 * dz - dx;
            while (x1 != x2) {
                points.add(new BlockPos(x1, y1, z1));
                x1 += xs;
                if (p1 >= 0) {
                    y1 += ys;
                    p1 -= 2 * dx;
                }
                if (p2 >= 0) {
                    z1 += zs;
                    p2 -= 2 * dx;
                }
                p1 += 2 * dy;
                p2 += 2 * dz;
            }
        } else if (dy >= dx && dy >= dz) {
            int p1 = 2 * dx - dy;
            int p2 = 2 * dz - dy;
            while (y1 != y2) {
                points.add(new BlockPos(x1, y1, z1));
                y1 += ys;
                if (p1 >= 0) {
                    x1 += xs;
                    p1 -= 2 * dy;
                }
                if (p2 >= 0) {
                    z1 += zs;
                    p2 -= 2 * dy;
                }
                p1 += 2 * dx;
                p2 += 2 * dz;
            }
        } else {
            int p1 = 2 * dy - dz;
            int p2 = 2 * dx - dz;
            while (z1 != z2) {
                points.add(new BlockPos(x1, y1, z1));
                z1 += zs;
                if (p1 >= 0) {
                    y1 += ys;
                    p1 -= 2 * dz;
                }
                if (p2 >= 0) {
                    x1 += xs;
                    p2 -= 2 * dz;
                }
                p1 += 2 * dy;
                p2 += 2 * dx;
            }
        }
        points.add(new BlockPos(x2, y2, z2));

        return points;
    }

    // 重写基类方法 - 复用圆形改造中的地形处理逻辑
    @Override
    protected ReferenceTerrainInfo getReferenceTerrainInfo(int referenceX, int referenceZ) {
        // 复用基类或圆形改造的逻辑
        if (!world.isChunkLoaded(referenceX >> 4, referenceZ >> 4)) {
            return null;
        }

        int referenceSurfaceY = world.getChunk(referenceX >> 4, referenceZ >> 4)
                .getHeightmap(Heightmap.Type.MOTION_BLOCKING)
                .get(referenceX & 15, referenceZ & 15);

        if (referenceSurfaceY < yMin - 1) {
            return null;
        }

        referenceSurfaceY = validateAndAdjustReferenceHeight(referenceX, referenceZ, referenceSurfaceY);
        return analyzeReferenceTerrain(referenceX, referenceZ, referenceSurfaceY);
    }

    @Override
    protected void copyFromReference(int targetX, int targetZ, ReferenceTerrainInfo referenceInfo) {
        copyFromReference(targetX, targetZ, referenceInfo, 0);
    }

    protected void copyFromReference(int targetX, int targetZ, ReferenceTerrainInfo referenceInfo, int originalSurfaceY) {
        copyTerrainStructure(targetX, targetZ, referenceInfo, originalSurfaceY);
    }

    @Override
    protected boolean shouldSkipProcessing(int referenceSurfaceYAtTarget, int originalSurfaceY) {
        return false;
    }

    // 复用圆形改造中的辅助方法
    private void destroyAtPosition(int targetX, int targetZ) {
        int surfaceY = world.getChunk(targetX >> 4, targetZ >> 4)
                .getHeightmap(Heightmap.Type.WORLD_SURFACE)
                .get(targetX & 15, targetZ & 15);

        if (surfaceY < yMin - 1) {
            return;
        }

        for (int y = yMin; y <= surfaceY + yMax; y++) {
            BlockPos targetPos = new BlockPos(targetX, y, targetZ);
            BlockState currentState = world.getBlockState(targetPos);
            if (!currentState.isAir()) {
                world.setBlockState(targetPos, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }

    private int validateAndAdjustReferenceHeight(int x, int z, int initialHeight) {
        int currentY = initialHeight;
        while (currentY > yMin + 10) {
            BlockPos pos = new BlockPos(x, currentY, z);
            BlockState state = world.getBlockState(pos);
            if (isSolidBlock(world, state)) {
                return currentY;
            }
            currentY--;
        }
        return initialHeight;
    }

    private ReferenceTerrainInfo analyzeReferenceTerrain(int x, int z, int surfaceY) {
        ReferenceTerrainInfo info = new ReferenceTerrainInfo();
        info.surfaceY = surfaceY;

        List<BlockState> blocks = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();

        for (int y = yMin; y <= surfaceY + yMax; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() || y <= surfaceY) {
                blocks.add(state);
                heights.add(y);
            }
        }

        info.blocks = blocks.toArray(new BlockState[0]);
        info.heights = heights.stream().mapToInt(Integer::intValue).toArray();

        BlockPos abovePos = new BlockPos(x, surfaceY + 1, z);
        BlockState aboveState = world.getBlockState(abovePos);
        if (!aboveState.isAir()) {
            info.aboveSurfaceBlocks = new BlockState[]{aboveState};
            info.aboveSurfaceHeights = new int[]{surfaceY + 1};
        }

        return info;
    }

    private void copyTerrainStructure(int targetX, int targetZ, ReferenceTerrainInfo reference, int originalSurfaceY) {
        if (reference.blocks != null && reference.heights.length != 0) {
            copyWithoutPreservation(targetX, targetZ, reference);
        }
    }

    private void copyWithoutPreservation(int targetX, int targetZ, ReferenceTerrainInfo reference) {
        for (int i = 0; i < reference.blocks.length; i++) {
            int targetY = reference.heights[i];
            BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
            BlockState referenceState = reference.blocks[i];

            if (!referenceState.isAir()) {
                BlockState currentState = world.getBlockState(targetPos);
                if (!currentState.equals(referenceState)) {
                    world.setBlockState(targetPos, referenceState, 3);
                }
            }
        }
    }

    // 判断是否为固体方块（需要从基类或工具类中获取）
    private boolean isSolidBlock(ServerWorld world, BlockState state) {
        // 这里需要根据你的具体实现来定义
        return state.isOpaque() && state.getBlock() != Blocks.AIR;
    }
}
