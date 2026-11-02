package com.alrex.parcool.common.block.zipline;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;

/**
 * 木制滑索钩 ✓ —— 1.12.2 版。
 *
 * <h3>🚨 1.12.2 没有 {@code VoxelShape} ✗ —— 这是本类最大的改写</h3>
 * <p>上游用 {@code VoxelShape[]}（按 6 个朝向各一组）+ {@code VoxelShapes.or(...)}
 * <b>把两个盒子合并</b> ✗。</p>
 * <p><b>1.12.2 的方块形状只能是一个 {@link AxisAlignedBB}</b> ✗ ——
 * 没有「多盒合并」这个概念 ✓（{@code Block#getBoundingBox} 返回单个 AABB ✓）。</p>
 * <p><b>✅ 处理方式</b>：取两个盒子的**并集外框** ✓ ——
 * 外观上会略微"胖"一点 ✗，但都是极小的部件（钩子本体 + 底座 ✓），
 * <b>视觉与碰撞差异可以忽略</b> ✓。要精确的话得改用
 * {@code addCollisionBoxToList} 自己收集多盒 ✓（本批不做 ✓）。</p>
 *
 * <h3>六个朝向的外框（由上游的两个盒子合成 ✓）</h3>
 * <table border="1">
 *   <tr><th>朝向</th><th>上游两盒</th><th>1.12.2 并集外框</th></tr>
 *   <tr><td>UP</td><td>(6,6,6)-(10,8,10) + (5,0,5)-(11,6,11)</td><td>(5,0,5)-(11,8,11)</td></tr>
 *   <tr><td>DOWN</td><td>(6,8,6)-(10,10,10) + (5,10,5)-(11,16,11)</td><td>(5,8,5)-(11,16,11)</td></tr>
 *   <tr><td>SOUTH</td><td>(6,6,6)-(10,10,8) + (5,5,0)-(11,11,6)</td><td>(5,5,0)-(11,11,8)</td></tr>
 *   <tr><td>NORTH</td><td>(6,6,8)-(10,10,10) + (5,5,10)-(11,11,16)</td><td>(5,5,8)-(11,11,16)</td></tr>
 *   <tr><td>WEST</td><td>(8,6,6)-(8,10,10) + (10,5,5)-(16,11,11)</td><td>(8,5,5)-(16,11,11)</td></tr>
 *   <tr><td>EAST</td><td>(6,6,6)-(8,10,10) + (0,5,5)-(6,11,11)</td><td>(0,5,5)-(8,11,11)</td></tr>
 * </table>
 *
 * <p><b>⚠️ 上游 WEST 那格第一个盒子写的是 {@code (8,6,6)-(8,10,10)}</b> ✗ ——
 * x 起止都是 8 ✓，是个<b>零厚度</b>的盒子 ✗（上游笔误 ✓，实际影响为零 ✓）。照搬结果 ✓。</p>
 */
public class WoodenZiplineHookBlock extends ZiplineHookBlock {

    /** 按 {@code EnumFacing.ordinal()} 索引 ✓（与 1.12.2 的 {@code EnumFacing} 顺序一致 ✓）。 */
    protected static final AxisAlignedBB[] SHAPES = new AxisAlignedBB[EnumFacing.values().length];

    static {
        SHAPES[EnumFacing.UP.ordinal()] = new AxisAlignedBB(5 / 16d, 0, 5 / 16d, 11 / 16d, 8 / 16d, 11 / 16d);
        SHAPES[EnumFacing.DOWN.ordinal()] = new AxisAlignedBB(5 / 16d, 8 / 16d, 5 / 16d, 11 / 16d, 1d, 11 / 16d);
        SHAPES[EnumFacing.SOUTH.ordinal()] = new AxisAlignedBB(5 / 16d, 5 / 16d, 0, 11 / 16d, 11 / 16d, 8 / 16d);
        SHAPES[EnumFacing.NORTH.ordinal()] = new AxisAlignedBB(5 / 16d, 5 / 16d, 8 / 16d, 11 / 16d, 11 / 16d, 1d);
        SHAPES[EnumFacing.WEST.ordinal()] = new AxisAlignedBB(8 / 16d, 5 / 16d, 5 / 16d, 1d, 11 / 16d, 11 / 16d);
        SHAPES[EnumFacing.EAST.ordinal()] = new AxisAlignedBB(0, 5 / 16d, 5 / 16d, 8 / 16d, 11 / 16d, 11 / 16d);
    }

    public WoodenZiplineHookBlock(Material material) {
        super(material);
        // ⚠️ setSoundType 在 1.12.2 的 Block 里是 **protected** ✗
        // → 不能在注册时的链式调用里用 ✓，必须在子类构造器内调 ✓
        setSoundType(net.minecraft.block.SoundType.WOOD);
    }

    @Nullable
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        EnumFacing facing = state.getValue(FACING);
        AxisAlignedBB box = SHAPES[facing.ordinal()];
        return box == null ? FULL_BLOCK_AABB : box;
    }

    /** 碰撞盒与形状一致 ✓ —— 木钩是实体件，会挡住玩家 ✓。 */
    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return getBoundingBox(state, worldIn, pos);
    }
}
