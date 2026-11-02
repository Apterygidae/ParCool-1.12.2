package com.alrex.parcool.utilities;

import com.alrex.parcool.common.action.impl.HangDown;
import com.alrex.parcool.common.tags.BlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.BlockEndRod;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWall;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

/**
 * 世界查询工具（1.12.2 版）—— 上游 {@code WorldUtil} 的<b>部分移植</b>。
 *
 * <h3>⚠️ 为什么是「部分」而不是全部</h3>
 * <p>上游 {@code WorldUtil} 有 534 行、被 10 个文件引用。但其中一部分<b>无法机械移植</b>：</p>
 * <ul>
 *   <li>{@code isHideAbleBlock} / {@code getHideAbleSpace} 依赖
 *       {@code BlockTags.HIDE_ABLE} —— 那是 <b>1.16 的方形标签系统</b>，
 *       1.12.2 根本没有（1.13+ 才引入）。<b>需要重新设计成显式方块集合</b>，
 *       属于设计工作而不是翻译工作 → 留到实现 {@code HideInBlock} 时一起做。</li>
 *   <li>{@code getHangableBars} 的返回类型是 {@code HangDown.BarAxis} ——
 *       那个枚举在 1.12.2 侧还不存在（{@code HangDown} 仍是骨架）
 *       → 留到实现 {@code HangDown} 时一起做。</li>
 * </ul>
 *
 * <p><b>本文件目前只包含 {@code Vault} 需要的三个函数</b>：
 * {@link #getVaultableStep}、{@link #getWallHeight(EntityLivingBase)}、
 * {@link #getWall(EntityLivingBase)}（后者的私有辅助）。
 * 其余按「服务于哪个动作」分批补齐 —— 它们各自的服务对象是：
 * {@code getRunnableWall}→WallRun 系、{@code getGrabbableWall}→ClingToCliff、
 * {@code existsSpaceBelow}/{@code existsDivableSpace}→Dive/SkyDive、
 * {@code getHangableBars}→HangDown、{@code getHideAbleSpace}→HideInBlock。</p>
 *
 * <h3>1.12.2 的 API 替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code entity.level}</td><td>{@code entity.world}</td></tr>
 *   <tr><td>{@code entity.getBbWidth() / getBbHeight()}</td>
 *       <td><b>{@code entity.width / entity.height}</b>（公开字段，1.12.2 没有 getter）</td></tr>
 *   <tr><td>{@code entity.position()}</td><td>{@code entity.getPositionVector()}</td></tr>
 *   <tr><td>{@code entity.getEyeHeight()}</td><td>同名 ✓</td></tr>
 *   <tr><td>{@code LivingEntity}</td><td>{@link EntityLivingBase}</td></tr>
 *   <tr><td>{@code Vector3d} / {@code vec.x()}</td><td>{@link Vec3d} / {@code vec.x}</td></tr>
 *   <tr><td><b>{@code world.noCollision(aabb)}</b></td>
 *       <td><b>{@code world.getCollisionBoxes(null, aabb).isEmpty()}</b><br>
 *           1.12.2 没有 {@code noCollision}；传 {@code null} 表示「只查方块碰撞、不含实体」，
 *           与上游语义一致 ✓</td></tr>
 *   <tr><td>{@code aabb.expandTowards(x,y,z)}</td><td>{@code aabb.expand(x,y,z)}</td></tr>
 *   <tr><td>{@code world.isLoaded(pos)}</td><td>{@code world.isBlockLoaded(pos)}</td></tr>
 *   <tr><td>{@code StairsBlock} / {@code state.getValue(StairsBlock.HALF)}</td>
 *       <td>{@link BlockStairs} / {@code state.getValue(BlockStairs.HALF)}</td></tr>
 *   <tr><td>{@code Half.BOTTOM} / {@code Direction.SOUTH}</td>
 *       <td>{@code BlockStairs.EnumHalf.BOTTOM} / {@link EnumFacing}{@code .SOUTH}</td></tr>
 * </table>
 */
public class WorldUtil {

    private WorldUtil() {
    }

    // ────────────────────────────────────────────────────────────────────────
    // 墙检测（Vault 与后续的 WallRun 系动作共用）
    // ────────────────────────────────────────────────────────────────────────

    @Nullable
    public static Vec3d getWall(EntityLivingBase entity) {
        return getWall(entity, entity.width * 0.5);
    }

    @Nullable
    public static Vec3d getWall(EntityLivingBase entity, double range) {
        final double width = entity.width * 0.49;
        double wallX = 0;
        double wallZ = 0;
        Vec3d pos = entity.getPositionVector();

        List<AxisAlignedBB> boxes = new LinkedList<>();
        final int division = 2;
        double singleYHeight = entity.height / division;
        for (int i = 0; i < division; i++) {
            boxes.add(new AxisAlignedBB(
                    pos.x - width,
                    pos.y + singleYHeight * i,
                    pos.z - width,
                    pos.x + width,
                    pos.y + singleYHeight * (i + 1),
                    pos.z + width
            ));
        }

        if (boxes.stream().noneMatch(box -> noCollision(entity, box.expand(range, 0, 0)))) {
            wallX++;
        }
        if (boxes.stream().noneMatch(box -> noCollision(entity, box.expand(-range, 0, 0)))) {
            wallX--;
        }
        if (boxes.stream().noneMatch(box -> noCollision(entity, box.expand(0, 0, range)))) {
            wallZ++;
        }
        if (boxes.stream().noneMatch(box -> noCollision(entity, box.expand(0, 0, -range)))) {
            wallZ--;
        }
        if (wallX == 0 && wallZ == 0) return null;

        return new Vec3d(wallX, 0, wallZ);
    }

    /** 对应 1.16 的 {@code world.noCollision(aabb)}：只查方块碰撞（不含实体）。 */
    private static boolean noCollision(EntityLivingBase entity, AxisAlignedBB box) {
        return entity.world.getCollisionBoxes(null, box).isEmpty();
    }

    /** 沿给定方向扫描墙的高度（上游的通用版本）。 */
    public static double getWallHeight(EntityLivingBase entity, Vec3d direction, double maxHeight, double accuracy) {
        final double d = entity.width * 0.49;
        direction = direction.normalize();
        Vec3d pos = entity.getPositionVector();
        boolean canReturn = false;
        for (double height = 0; height < maxHeight; height += accuracy) {
            AxisAlignedBB box = new AxisAlignedBB(
                    pos.x + d + (direction.x > 0 ? 1 : 0),
                    pos.y + height,
                    pos.z + d + (direction.z > 0 ? 1 : 0),
                    pos.x - d + (direction.x < 0 ? -1 : 0),
                    pos.y + height + accuracy,
                    pos.z - d + (direction.z < 0 ? -1 : 0)
            );
            if (!noCollision(entity, box)) {
                canReturn = true;
            } else {
                if (canReturn) {
                    return height;
                }
            }
        }
        return maxHeight;
    }

    /** 玩家正前方那堵墙的高度（上游的主力版本）。 */
    public static double getWallHeight(EntityLivingBase entity) {
        Vec3d wall = getWall(entity);
        if (wall == null) return 0;
        final double accuracy = entity.height / 18; // normally about 0.1
        final double d = entity.width * 0.5;
        int loopNum = (int) Math.round(entity.height / accuracy);
        Vec3d pos = entity.getPositionVector();
        boolean canReturn = false;
        for (int i = 0; i < loopNum; i++) {
            AxisAlignedBB box = new AxisAlignedBB(
                    pos.x + d + (wall.x > 0 ? 1 : 0),
                    pos.y + accuracy * i,
                    pos.z + d + (wall.z > 0 ? 1 : 0),
                    pos.x - d + (wall.x < 0 ? -1 : 0),
                    pos.y + accuracy * (i + 1),
                    pos.z - d + (wall.z < 0 ? -1 : 0)
            );

            if (!noCollision(entity, box)) {
                canReturn = true;
            } else {
                if (canReturn) return accuracy * i;
            }
        }
        return entity.height;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 翻越目标检测（Vault 专用）
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 找到玩家面前「可以翻越的台阶」的方向。
     *
     * <p>逻辑：下方有阻挡、上方足够高可容身 —— 那就是一个可翻越的台阶。
     * 另外对<b>楼梯</b>做了朝向特判（上行的楼梯不算可翻越，因为那本来就能走上去）。</p>
     */
    @Nullable
    public static Vec3d getVaultableStep(EntityLivingBase entity) {
        final double d = entity.width * 0.5;
        World world = entity.world;
        double distance = entity.width / 2;
        double baseLine = Math.min(entity.height * 0.86, getWallHeight(entity));
        double stepX = 0;
        double stepZ = 0;
        Vec3d pos = entity.getPositionVector();

        AxisAlignedBB baseBoxBottom = new AxisAlignedBB(
                pos.x - d,
                pos.y,
                pos.z - d,
                pos.x + d,
                pos.y + baseLine,
                pos.z + d
        );
        AxisAlignedBB baseBoxTop = new AxisAlignedBB(
                pos.x - d,
                pos.y + baseLine,
                pos.z - d,
                pos.x + d,
                pos.y + baseLine + entity.height,
                pos.z + d
        );
        if (!noCollision(entity, baseBoxBottom.expand(distance, 0, 0))
                && noCollision(entity, baseBoxTop.expand(distance + 1.8, 0, 0))) {
            stepX++;
        }
        if (!noCollision(entity, baseBoxBottom.expand(-distance, 0, 0))
                && noCollision(entity, baseBoxTop.expand(-(distance + 1.8), 0, 0))) {
            stepX--;
        }
        if (!noCollision(entity, baseBoxBottom.expand(0, 0, distance))
                && noCollision(entity, baseBoxTop.expand(0, 0, distance + 1.8))) {
            stepZ++;
        }
        if (!noCollision(entity, baseBoxBottom.expand(0, 0, -distance))
                && noCollision(entity, baseBoxTop.expand(0, 0, -(distance + 1.8)))) {
            stepZ--;
        }
        if (stepX == 0 && stepZ == 0) return null;

        if (stepX == 0 || stepZ == 0) {
            Vec3d result = new Vec3d(stepX, 0, stepZ);
            BlockPos target = new BlockPos(entity.getPositionVector().add(result).add(0, 0.5, 0));
            if (!world.isBlockLoaded(target)) return null;
            IBlockState state = world.getBlockState(target);
            if (state.getBlock() instanceof BlockStairs) {
                BlockStairs.EnumHalf half = state.getValue(BlockStairs.HALF);
                if (half != BlockStairs.EnumHalf.BOTTOM) return result;
                EnumFacing direction = state.getValue(BlockStairs.FACING);
                if (stepZ > 0 && direction == EnumFacing.SOUTH) return null;
                if (stepZ < 0 && direction == EnumFacing.NORTH) return null;
                if (stepX > 0 && direction == EnumFacing.EAST) return null;
                if (stepX < 0 && direction == EnumFacing.WEST) return null;
            }
        }

        return new Vec3d(stepX, 0, stepZ);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 「可抓握的墙面」检测（ClingToCliff / HangDown 系动作共用）
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 找到玩家侧面「可以抓住的墙」的方向。
     *
     * <p>分两次探测：先在<b>眼高与顶之间的中线</b>试，不行再在<b>头顶再往上一段</b>试 ——
     * 后者对应「翻上边缘后还能抓住」的情况 ✓</p>
     */
    @Nullable
    public static Vec3d getGrabbableWall(EntityLivingBase entity) {
        double distance = entity.width / 2;
        double baseLine1 = entity.getEyeHeight() + (entity.height - entity.getEyeHeight()) / 2;
        double baseLine2 = entity.height + (entity.height - entity.getEyeHeight()) / 2;
        Vec3d wall1 = getGrabbableWall(entity, distance, baseLine1);
        if (wall1 != null) return wall1;
        return getGrabbableWall(entity, distance, baseLine2);
    }

    /**
     * 单次探测。
     *
     * <p>要点：侧面的「下方可撞、上方要通」才算可抓 —— 上方要通是因为玩家得能把手搭上去。
     * 另外还会查方块<b>光滑度</b>（> 0.9 的滑面抓不住，比如冰）✓</p>
     */
    @Nullable
    private static Vec3d getGrabbableWall(EntityLivingBase entity, double distance, double baseLine) {
        final double d = entity.width * 0.49;
        World world = entity.world;
        Vec3d pos = entity.getPositionVector();
        AxisAlignedBB baseBoxSide = new AxisAlignedBB(
                pos.x - d,
                pos.y + baseLine - entity.height / 6,
                pos.z - d,
                pos.x + d,
                pos.y + baseLine,
                pos.z + d
        );
        AxisAlignedBB baseBoxTop = new AxisAlignedBB(
                pos.x - d,
                pos.y + baseLine,
                pos.z - d,
                pos.x + d,
                pos.y + entity.height,
                pos.z + d
        );
        int xDirection = 0;
        int zDirection = 0;

        if (!noCollision(entity, baseBoxSide.expand(distance, 0, 0))
                && noCollision(entity, baseBoxTop.expand(distance, 0, 0))) {
            xDirection++;
        }
        if (!noCollision(entity, baseBoxSide.expand(-distance, 0, 0))
                && noCollision(entity, baseBoxTop.expand(-distance, 0, 0))) {
            xDirection--;
        }
        if (!noCollision(entity, baseBoxSide.expand(0, 0, distance))
                && noCollision(entity, baseBoxTop.expand(0, 0, distance))) {
            zDirection++;
        }
        if (!noCollision(entity, baseBoxSide.expand(0, 0, -distance))
                && noCollision(entity, baseBoxTop.expand(0, 0, -distance))) {
            zDirection--;
        }
        if (xDirection == 0 && zDirection == 0) {
            return null;
        }
        float slipperiness;
        if (xDirection != 0 && zDirection != 0) {
            // 斜角：取两个方向上更滑的那个
            BlockPos blockPos1 = new BlockPos(
                    entity.posX + xDirection,
                    entity.getEntityBoundingBox().minY + baseLine - 0.3,
                    entity.posZ
            );
            BlockPos blockPos2 = new BlockPos(
                    entity.posX,
                    entity.getEntityBoundingBox().minY + baseLine - 0.3,
                    entity.posZ + zDirection
            );
            if (!world.isBlockLoaded(blockPos1)) return null;
            if (!world.isBlockLoaded(blockPos2)) return null;
            slipperiness = Math.min(
                    getSlipperiness(world, blockPos1, entity),
                    getSlipperiness(world, blockPos2, entity)
            );
        } else {
            double blockX = entity.posX + xDirection, blockZ = entity.posZ + zDirection;
            BlockPos blockPos = new BlockPos(
                    blockX, entity.getEntityBoundingBox().minY + baseLine - 0.3, blockZ
            );
            if (!world.isBlockLoaded(blockPos)) return null;
            // 正好落在方块边界上时往外偏半格，避免取到相邻的空方块
            if (world.getBlockState(blockPos).getBlock() == Blocks.AIR) {
                if (xDirection != 0) {
                    blockZ = blockZ + Math.signum((blockZ - Math.floor(blockZ)) - 0.5);
                } else {
                    blockX = blockX + Math.signum((blockX - Math.floor(blockX)) - 0.5);
                }
                blockPos = new BlockPos(
                        blockX, entity.getEntityBoundingBox().minY + baseLine - 0.3, blockZ
                );
            }
            slipperiness = getSlipperiness(world, blockPos, entity);
        }
        return slipperiness <= 0.9 ? new Vec3d(xDirection, 0, zDirection) : null;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 「可跑墙」检测（HorizontalWallRun 专用）
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 找到玩家侧面「可以横向跑」的墙的方向。
     *
     * <p>与 {@link #getWall} 的区别：这里取<b>两个</b>高度带 ——
     * 低位（0~30% 身高）与高位（85%~100% 身高）——
     * 并要求<b>两者都撞到方块</b>才算可跑墙 ✓
     * 用意是排除「只有膝盖高的一层台阶」那种：能踩上去，但没有可攀附的连续墙面 ✓</p>
     *
     * <p><b>读法提醒</b>：{@code noneMatch(noCollision(...))} 看起来绕，实际是
     * 「<b>没有任何一个高度带是空的</b>」= 「全都撞到东西」= 有墙 ✓ 照搬上游写法。</p>
     *
     * @param range 探测伸出的距离
     */
    @Nullable
    public static Vec3d getRunnableWall(EntityLivingBase entity, double range) {
        double width = entity.width * 0.4f;
        double wallX = 0;
        double wallZ = 0;
        Vec3d pos = entity.getPositionVector();

        List<AxisAlignedBB> boxes = new LinkedList<>();
        boxes.add(new AxisAlignedBB(
                pos.x - width,
                pos.y,
                pos.z - width,
                pos.x + width,
                pos.y + entity.height * 0.3,
                pos.z + width
        ));
        boxes.add(new AxisAlignedBB(
                pos.x - width,
                pos.y + entity.height * 0.85,
                pos.z - width,
                pos.x + width,
                pos.y + entity.height,
                pos.z + width
        ));

        if (boxes.stream().noneMatch(box -> noCollision(entity, box.expand(range, 0, 0)))) {
            wallX++;
        }
        if (boxes.stream().noneMatch(box -> noCollision(entity, box.expand(-range, 0, 0)))) {
            wallX--;
        }
        if (boxes.stream().noneMatch(box -> noCollision(entity, box.expand(0, 0, range)))) {
            wallZ++;
        }
        if (boxes.stream().noneMatch(box -> noCollision(entity, box.expand(0, 0, -range)))) {
            wallZ--;
        }
        if (wallX == 0 && wallZ == 0) return null;

        return new Vec3d(wallX, 0, wallZ);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 「可悬挂的横杆」检测（HangDown 专用）
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 找到头顶「可以挂上去的横杆」及其轴向。
     *
     * <p>流程：先在头顶上方一小段测一个包围盒 —— <b>必须撞到东西</b>（说明头顶有东西可抓）✓
     * 然后取头顶上方 0.4 格处的方块，按类型判断它的「杆」是沿 X 还是沿 Z ✓</p>
     *
     * <h3>⚠️ 1.12.2 的方块类型对应（本方法的移植难点）</h3>
     * <table border="1">
     *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
     *   <tr><td>{@code RotatedPillarBlock} + {@code AXIS}</td>
     *       <td>{@code BlockRotatedPillar} + {@code AXIS} ✓</td></tr>
     *   <tr><td>{@code EndRodBlock} + {@code DirectionalBlock.FACING}</td>
     *       <td>{@code BlockEndRod} + {@code FACING}（继承自 {@code BlockDirectional}）✓</td></tr>
     *   <tr><td><b>{@code FourWayBlock}</b></td>
     *       <td><b>1.12.2 没有这个基类</b> → 要分别判 {@code BlockPane}（玻璃板/铁栏杆）
     *           与 {@code BlockFence}（栅栏），<b>两者的四向属性都是 {@code PropertyBool}</b> ✓</td></tr>
     *   <tr><td>{@code WallBlock} + {@code WallHeight} 枚举</td>
     *       <td>{@code BlockWall}，<b>四向也是 {@code PropertyBool}</b>（1.12.2 没有 {@code WallHeight}）✓</td></tr>
     *   <tr><td><b>{@code state.isCollisionShapeFullBlock(level, pos)}</b></td>
     *       <td><b>{@code state.isFullCube()}</b> —— 语义最接近（「这个方块是否占满整格」），
     *           用途是「实心整格方块不算横杆」✓</td></tr>
     * </table>
     */
    @Nullable
    public static HangDown.BarAxis getHangableBars(EntityLivingBase entity) {
        final double bbWidth = entity.width / 4;
        final double bbHeight = 0.35;
        AxisAlignedBB bb = new AxisAlignedBB(
                entity.posX - bbWidth,
                entity.posY + entity.height,
                entity.posZ - bbWidth,
                entity.posX + bbWidth,
                entity.posY + entity.height + bbHeight,
                entity.posZ + bbWidth
        );
        // 头顶没撞到东西 → 没得挂
        if (noCollision(entity, bb)) return null;

        // ★ 扫描「探测框覆盖的那几层」，取第一个算得出轴向的方块。
        //
        // 上游只查单点 `py + height + 0.4`（即「脚上方第 2 格」那一层）——
        // 那只在「杆刚好位于脚上方 2 格」时才对 ✗
        // 而栅栏/石墙/玻璃板这类方块的**碰撞箱比 1 格高**（栅栏 1.5 格），
        // 它可以满足探测框的碰撞要求，但它所在的**方块层**未必是 py+2 ✗
        // → 实测症状：只有横置末地烛能挂，「其它方块无法触发」✗
        //
        // 扫描范围 = 探测框的 [py+height, py+height+0.35]，最多覆盖 2 层 ✓
        // 对「杆在 py+2」的标准情形，结果与上游完全一致 ✓
        final int minY = (int) Math.floor(entity.posY + entity.height);
        final int maxY = (int) Math.floor(entity.posY + entity.height + bbHeight);
        HangDown.BarAxis axis = null;
        for (int y = minY; y <= maxY && axis == null; y++) {
            BlockPos pos = new BlockPos(entity.posX, y, entity.posZ);
            if (!entity.world.isBlockLoaded(pos)) continue;
            axis = getBarAxisAt(entity.world, pos);
        }
        return axis;
    }

    /**
     * 单个方块算不算是「可悬挂的杆」（是则返回轴向，否则 {@code null}）。
     *
     * <p><b>⚠️ 必须传 {@code World + BlockPos} 而不是 {@code IBlockState} —— 见下方注释 ✓</b></p>
     */
    @Nullable
    private static HangDown.BarAxis getBarAxisAt(World world, BlockPos pos) {
        // ★★ 关键：取「实际态」而不是「存储态」！
        //
        // 1.12.2 里栅栏 / 玻璃板 / 铁栏杆 / 石墙的**四向连接属性是动态算的** ——
        // `getBlockState(pos)` 拿到的是**存储态**，其中 NORTH/EAST/SOUTH/WEST **一律是 false** ✗
        // 所以直接读存储态会让「连接数」恒为 0 → 永远算不出轴向 → 这些方块全被判为「不是杆」✗
        //
        // （实测症状：`HangDown` 只能挂在横置末地烛上 —— 因为末地烛用的是存在方块自身的
        //   `FACING`，不受这个影响 ✓）
        //
        // `getActualState(world, pos)` 才会根据邻居算出真正的连接情况 ✓
        IBlockState state = world.getBlockState(pos).getActualState(world, pos);
        Block block = state.getBlock();
        HangDown.BarAxis axis = null;

        if (block instanceof BlockRotatedPillar) {
            // 圆木这类「有轴向」的柱子 —— 实心整格的不算（那是柱子不是杆）
            if (state.isFullCube()) return null;
            EnumFacing.Axis pillarAxis = state.getValue(BlockRotatedPillar.AXIS);
            switch (pillarAxis) {
                case X:
                    axis = HangDown.BarAxis.X;
                    break;
                case Z:
                    axis = HangDown.BarAxis.Z;
                    break;
                default:
                    break;
            }
        } else if (block instanceof BlockEndRod) {
            // 末地烛：朝向决定轴向（朝上/朝下的竖杆不可悬挂，那是"爬"的用法）
            if (state.isFullCube()) return null;
            EnumFacing direction = state.getValue(BlockDirectional.FACING);
            switch (direction) {
                case EAST:
                case WEST:
                    axis = HangDown.BarAxis.X;
                    break;
                case NORTH:
                case SOUTH:
                    axis = HangDown.BarAxis.Z;
                    break;
                default:
                    break;
            }
        } else if (block instanceof BlockPane || block instanceof BlockFence) {
            // 玻璃板 / 铁栏杆 / 栅栏 —— 1.12.2 没有 FourWayBlock，这两类的四向属性都是 PropertyBool
            int zCount = 0;
            int xCount = 0;
            if (isConnected(state, block, EnumFacing.NORTH)) zCount++;
            if (isConnected(state, block, EnumFacing.SOUTH)) zCount++;
            if (isConnected(state, block, EnumFacing.EAST)) xCount++;
            if (isConnected(state, block, EnumFacing.WEST)) xCount++;
            if (zCount > 0 && xCount == 0) axis = HangDown.BarAxis.Z;
            if (xCount > 0 && zCount == 0) axis = HangDown.BarAxis.X;
        } else if (block instanceof BlockWall) {
            // 石墙：四向 PropertyBool
            int zCount = 0;
            int xCount = 0;
            if (state.getValue(BlockWall.NORTH)) zCount++;
            if (state.getValue(BlockWall.SOUTH)) zCount++;
            if (state.getValue(BlockWall.EAST)) xCount++;
            if (state.getValue(BlockWall.WEST)) xCount++;
            if (zCount > 0 && xCount == 0) axis = HangDown.BarAxis.Z;
            if (xCount > 0 && zCount == 0) axis = HangDown.BarAxis.X;
        }

        return axis;
    }

    /**
     * 取 {@code BlockPane} / {@code BlockFence} 的某一向连接属性。
     *
     * <p>两者都是 {@code PropertyBool}，但<b>是各自类上的静态字段</b>
     * （{@code BlockPane.NORTH} 与 {@code BlockFence.NORTH} 是两个不同的对象）——
     * 所以要按实际类型取 ✓</p>
     */
    private static boolean isConnected(IBlockState state, Block block, EnumFacing facing) {
        if (block instanceof BlockPane) {
            switch (facing) {
                case NORTH:
                    return state.getValue(BlockPane.NORTH);
                case SOUTH:
                    return state.getValue(BlockPane.SOUTH);
                case EAST:
                    return state.getValue(BlockPane.EAST);
                case WEST:
                    return state.getValue(BlockPane.WEST);
                default:
                    return false;
            }
        }
        switch (facing) {
            case NORTH:
                return state.getValue(BlockFence.NORTH);
            case SOUTH:
                return state.getValue(BlockFence.SOUTH);
            case EAST:
                return state.getValue(BlockFence.EAST);
            case WEST:
                return state.getValue(BlockFence.WEST);
            default:
                return false;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 「俯冲空间」检测（Dive 专用）
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 下方是否有足够的下落空间（空中起跳俯冲用）。
     *
     * <p>盒子 = 「中心 ± 2 倍宽，向下 9 格、向上 1.5 倍身高」✓
     * 空则说明「下面是空的，可以俯冲下去」✓</p>
     */
    public static boolean existsSpaceBelow(EntityLivingBase entity) {
        World world = entity.world;
        Vec3d center = entity.getPositionVector();
        if (!world.isBlockLoaded(new BlockPos(center))) return false;
        double height = entity.height * 1.5;
        double width = entity.width * 2;
        AxisAlignedBB boundingBox = new AxisAlignedBB(
                center.x - width,
                center.y - 9,
                center.z - width,
                center.x + width,
                center.y + height,
                center.z + width
        );
        return noCollision(entity, boundingBox);
    }

    /**
     * 前方是否有可以「俯冲进去」的空间。
     *
     * <p>分两段判：</p>
     * <ol>
     *   <li>沿<b>头部朝向</b>每 1.5 倍身宽取一个点（共 4 个），每处都要够空 ✓</li>
     *   <li>再往前 4 格，检查「前方下方是否有<b>一片水域</b>」——
     *       即从某层开始连续是可流动的液体 ✓（这样才叫「跳进水里」）</li>
     * </ol>
     *
     * <h3>⚠️ 1.12.2 的差异：水有两个方块</h3>
     * <p>1.16 只有 {@code Blocks.WATER}；<b>1.12.2 还分「静止水」和「流动水」</b>：
     * {@code Blocks.WATER} 与 {@code Blocks.FLOWING_WATER} ✗
     * 所以判定液体时必须<b>两个都认</b>，否则自然生成的水（多为流动水）会被判成「不是水」✗</p>
     */
    public static boolean existsDivableSpace(EntityLivingBase entity) {
        World world = entity.world;
        double width = entity.width * 1.5;
        double height = entity.height * 1.5;
        double wideWidth = entity.width * 2;
        Vec3d center = entity.getPositionVector();
        if (!world.isBlockLoaded(new BlockPos(center))) return false;
        Vec3d diveDirection = VectorUtil.fromYawDegree(entity.rotationYawHead);
        for (int i = 0; i < 4; i++) {
            Vec3d centerPoint = center.add(diveDirection.scale(width * i));
            AxisAlignedBB box = new AxisAlignedBB(
                    centerPoint.x - width,
                    centerPoint.y + 0.05,
                    centerPoint.z - width,
                    centerPoint.x + width,
                    centerPoint.y + height,
                    centerPoint.z + width
            );
            if (!noCollision(entity, box)) return false;
        }
        center = center.add(diveDirection.scale(4));
        AxisAlignedBB verticalWideBox = new AxisAlignedBB(
                center.x - wideWidth,
                center.y - 7,
                center.z - wideWidth,
                center.x + wideWidth,
                center.y + height,
                center.z + wideWidth
        );
        if (noCollision(entity, verticalWideBox)) return true;
        BlockPos centerBlockPos = new BlockPos(center.add(0, -0.5, 0));

        // 检查下方是不是一片水池
        if (!world.isBlockLoaded(centerBlockPos)) return false;
        verticalWideBox = new AxisAlignedBB(
                center.x - wideWidth,
                center.y - 2.9,
                center.z - wideWidth,
                center.x + wideWidth,
                center.y + height,
                center.z + wideWidth
        );
        int i = 0;
        int waterLevel = -1;
        for (; i < 6; i++) {
            Block block = world.getBlockState(centerBlockPos.down(i)).getBlock();
            if (block == Blocks.AIR) continue;
            if (isWaterBlock(block)) {
                waterLevel = i;
                break;
            }
            return false;
        }
        if (waterLevel == -1) return false;
        boolean filledWithWater = true;
        for (; i < waterLevel + 3; i++) {
            IBlockState state = world.getBlockState(centerBlockPos.down(i));
            if (!isWaterBlock(state.getBlock())) {
                filledWithWater = false;
                break;
            }
        }
        return filledWithWater && noCollision(entity, verticalWideBox);
    }

    /** ⚠️ 1.12.2 的水分「静止 / 流动」两个方块，必须都认（1.16 只有前者）✓ */
    private static boolean isWaterBlock(Block block) {
        return block == Blocks.WATER || block == Blocks.FLOWING_WATER;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 「能藏进去的方块」检测（HideInBlock 专用）
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 该方块是否能藏进去（草垛、树叶，或 OreDictionary 额外注册的）。
     *
     * <p>上游是 {@code state.getBlock().getTags().contains(BlockTags.HIDE_ABLE)} ——
     * 1.12.2 没有标签，改用 {@link BlockTags#isHideAble} ✓</p>
     */
    public static boolean isHideAbleBlock(IBlockState blockState) {
        return BlockTags.isHideAble(blockState.getBlock());
    }

    /** 「该位置是同种方块、且它上面是空气」—— 藏进去的前提 ✓ */
    private static boolean getHideAbleSpace$isHideAble(World world, Block block, BlockPos pos) {
        return world.isBlockLoaded(pos)
                && world.getBlockState(pos).getBlock() == block
                && world.getBlockState(pos.up()).getBlock() == Blocks.AIR;
    }

    /**
     * 找到「能藏进去的空间」—— 返回一对 {@code BlockPos} 表示占用范围。
     *
     * <h3>逻辑（照搬上游）</h3>
     * <ol>
     *   <li>基准方块必须是可藏的；它上面若<b>不是空气</b> → 只有「上面是同种可藏方块」才成立 ✓</li>
     *   <li>玩家太大（身高 ≥2 或宽 ≥1）→ 藏不进去 ✗</li>
     *   <li>身高 &lt;1 → 只占一格 ✓</li>
     *   <li>否则沿<b>视线方向</b>决定「往哪个方向再找一格」的优先级 ——
     *       先顺着看的方向，再垂直方向，最后反方向 ✓
     *       这样藏进去时「面朝哪边就往哪边扩展」，看起来自然 ✓</li>
     *   <li>都不行时，若<b>脚下方块是同种</b>且玩家离它很近（&lt;0.2）→ 用「下方 + 基准」两格 ✓</li>
     * </ol>
     *
     * <p>1.12.2 替换：{@code entity.level} → {@code entity.world}、
     * {@code base.above()/below()} → {@code up()/down()}、
     * {@code state.is(x)} → {@code getBlock() == x}、
     * {@code getBbWidth/getBbHeight} → {@code width/height}、
     * {@code getLookAngle()} → {@code getLookVec()}、{@code entity.getY()} → {@code posY} ✓</p>
     */
    @Nullable
    public static Tuple<BlockPos, BlockPos> getHideAbleSpace(Entity entity, BlockPos base) {
        World world = entity.world;
        if (!world.isBlockLoaded(base)) return null;
        IBlockState state = world.getBlockState(base);
        Block block = state.getBlock();
        if (!isHideAbleBlock(state)) return null;
        if (world.getBlockState(base.up()).getBlock() != Blocks.AIR) {
            if (getHideAbleSpace$isHideAble(world, block, base.up())) {
                return new Tuple<>(base, base.up());
            }
            return null;
        }
        double entityWidth = entity.width;
        double entityHeight = entity.height;
        if (entityHeight >= 2 || entityWidth >= 1) return null;
        if (entityHeight < 1) return new Tuple<>(base, base);
        Vec3d lookAngle = entity.getLookVec();
        if (Math.abs(lookAngle.z) > Math.abs(lookAngle.x)) {
            if (lookAngle.z > 0) {
                if (getHideAbleSpace$isHideAble(world, block, base.south())) return new Tuple<>(base, base.south());
                if (lookAngle.x > 0) {
                    if (getHideAbleSpace$isHideAble(world, block, base.east())) return new Tuple<>(base, base.east());
                    if (getHideAbleSpace$isHideAble(world, block, base.west())) return new Tuple<>(base, base.west());
                } else {
                    if (getHideAbleSpace$isHideAble(world, block, base.west())) return new Tuple<>(base, base.west());
                    if (getHideAbleSpace$isHideAble(world, block, base.east())) return new Tuple<>(base, base.east());
                }
                if (getHideAbleSpace$isHideAble(world, block, base.north())) return new Tuple<>(base, base.north());
            } else {
                if (getHideAbleSpace$isHideAble(world, block, base.north())) return new Tuple<>(base, base.north());
                if (lookAngle.x > 0) {
                    if (getHideAbleSpace$isHideAble(world, block, base.east())) return new Tuple<>(base, base.east());
                    if (getHideAbleSpace$isHideAble(world, block, base.west())) return new Tuple<>(base, base.west());
                } else {
                    if (getHideAbleSpace$isHideAble(world, block, base.west())) return new Tuple<>(base, base.west());
                    if (getHideAbleSpace$isHideAble(world, block, base.east())) return new Tuple<>(base, base.east());
                }
                if (getHideAbleSpace$isHideAble(world, block, base.south())) return new Tuple<>(base, base.south());
            }
        } else {
            if (lookAngle.x > 0) {
                if (getHideAbleSpace$isHideAble(world, block, base.east())) return new Tuple<>(base, base.east());
                if (lookAngle.z > 0) {
                    if (getHideAbleSpace$isHideAble(world, block, base.south())) return new Tuple<>(base, base.south());
                    if (getHideAbleSpace$isHideAble(world, block, base.north())) return new Tuple<>(base, base.north());
                } else {
                    if (getHideAbleSpace$isHideAble(world, block, base.north())) return new Tuple<>(base, base.north());
                    if (getHideAbleSpace$isHideAble(world, block, base.south())) return new Tuple<>(base, base.south());
                }
                if (getHideAbleSpace$isHideAble(world, block, base.west())) return new Tuple<>(base, base.west());
            } else {
                if (getHideAbleSpace$isHideAble(world, block, base.west())) return new Tuple<>(base, base.west());
                if (lookAngle.z > 0) {
                    if (getHideAbleSpace$isHideAble(world, block, base.south())) return new Tuple<>(base, base.south());
                    if (getHideAbleSpace$isHideAble(world, block, base.north())) return new Tuple<>(base, base.north());
                } else {
                    if (getHideAbleSpace$isHideAble(world, block, base.north())) return new Tuple<>(base, base.north());
                    if (getHideAbleSpace$isHideAble(world, block, base.south())) return new Tuple<>(base, base.south());
                }
                if (getHideAbleSpace$isHideAble(world, block, base.east())) return new Tuple<>(base, base.east());
            }
        }
        if (world.getBlockState(base.down()).getBlock() == block
                && Math.abs(entity.posY - base.down().getY()) < 0.2) {
            return new Tuple<>(base.down(), base);
        }
        return null;
    }

    /** 1.12.2 的 {@code getSlipperiness} 是 {@code Block} 上的四参方法（1.16 是 {@code BlockState} 上的三参）。 */
    private static float getSlipperiness(World world, BlockPos pos, EntityLivingBase entity) {
        IBlockState state = world.getBlockState(pos);
        return state.getBlock().getSlipperiness(state, world, pos, entity);
    }
}
