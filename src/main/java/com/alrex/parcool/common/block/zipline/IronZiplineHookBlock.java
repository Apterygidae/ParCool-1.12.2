package com.alrex.parcool.common.block.zipline;

import com.alrex.parcool.common.block.BlockStateProperties;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 铁制滑索钩 ✓ —— 1.12.2 版。比木钩多一个「**正交朝向**」状态 ✓。
 *
 * <h3>★ 为什么需要 {@code ORTHOGONAL}</h3>
 * <p>铁钩是个「**U 形环**」✓：贴在天花板/地面时，环本身还有**横着还是竖着**的区别 ✓。
 * 上游按玩家**视线方向**自动决定 ✓（见 {@link #getStateForPlacement} ✓），
 * 让钩子看起来顺着玩家面朝的方向 ✓。</p>
 *
 * <h3>🚨 1.12.2 的两个改写</h3>
 * <ol>
 *   <li><b>{@code VoxelShape} 不存在</b> ✗ → {@link AxisAlignedBB} ✓（单个盒 ✓，
 *       本类的六向形状本来就各是**一个**盒 ✓，所以是<b>精确</b>的 ✓，
 *       与木钩要用并集外框 ✗ 不同 ✓）</li>
 *   <li><b>方块状态要编进 meta</b> ✗ —— 1.16 用 {@code StateContainer} 自动处理 ✗；
 *       1.12.2 的 meta 是 **4 bit** ✓，本方块有两个属性 ✗ →
 *       自己定编码 ✓：<b>低 3 位 = 朝向 ✓，bit 3 = 正交 ✓</b>
 *       （6 朝向 × 2 = 12 种 ✓，装得下 ✓）</li>
 * </ol>
 *
 * <h3>★ 碰撞：**无**</h3>
 * <p>上游是 {@code .noCollission()} ✓ —— 1.12.2 没有这个 setter ✗，
 * 改为 {@link #getCollisionBoundingBox} 返回 {@code NULL_AABB} ✓。</p>
 */
public class IronZiplineHookBlock extends ZiplineHookBlock {

    /** 正交朝向 ✓ —— 与 {@code BlockStateProperties.ORTHOGONAL} 共享同一实例 ✓ */
    public static final PropertyBool ORTHOGONAL = BlockStateProperties.ORTHOGONAL;

    /** meta 里「正交」那一位的掩码 ✓（低 3 位留给朝向 ✓）。 */
    private static final int ORTHOGONAL_MASK = 8;

    /** 按 {@code EnumFacing.ordinal()} 索引 ✓ —— 每向**一个**盒 ✓（无并集损失 ✓）。 */
    protected static final AxisAlignedBB[] SHAPES = new AxisAlignedBB[EnumFacing.values().length];

    static {
        SHAPES[EnumFacing.DOWN.ordinal()] = new AxisAlignedBB(6 / 16d, 11 / 16d, 6 / 16d, 10 / 16d, 1d, 10 / 16d);
        SHAPES[EnumFacing.UP.ordinal()] = new AxisAlignedBB(6 / 16d, 0, 6 / 16d, 10 / 16d, 5 / 16d, 10 / 16d);
        SHAPES[EnumFacing.SOUTH.ordinal()] = new AxisAlignedBB(6 / 16d, 6 / 16d, 0, 10 / 16d, 10 / 16d, 5 / 16d);
        SHAPES[EnumFacing.NORTH.ordinal()] = new AxisAlignedBB(6 / 16d, 6 / 16d, 11 / 16d, 10 / 16d, 10 / 16d, 1d);
        SHAPES[EnumFacing.WEST.ordinal()] = new AxisAlignedBB(11 / 16d, 6 / 16d, 6 / 16d, 1d, 10 / 16d, 10 / 16d);
        SHAPES[EnumFacing.EAST.ordinal()] = new AxisAlignedBB(0, 6 / 16d, 6 / 16d, 5 / 16d, 10 / 16d, 10 / 16d);
    }

    public IronZiplineHookBlock(Material material) {
        super(material);
        // ⚠️★ 这里【绝对不能】调 setSoundType(chain()) ✗★
        //    —— 实测崩溃（2026-10-11 11:46）：
        //    java.lang.IllegalStateException: RegistryRef has not been populated yet.
        //        at ParCoolSoundTypes.chain
        //        at IronZiplineHookBlock.<init>
        //        at Blocks.registerAll → RegistryHandler.onRegisterBlocks
        //    原因：**Forge 并不保证 Register<SoundEvent> 早于 Register<Block>** ✗
        //    （我原先在注释里那样断言是错的 ✓）。
        //    → 改用运行时求值的 getSoundType() ✓（见下 ✓）
        setSoundType(net.minecraft.block.SoundType.METAL); // 临时值，运行时会被覆写掉 ✓
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.UP)
                .withProperty(ORTHOGONAL, false));
    }

    // ── ★ 锁链音效：**运行时**取，不能在建方块时取 ✗ ────────────────────
    //
    // SoundType 需要 SoundEvent 实例 ✗，而它只有注册后有值 ✓。
    // 方块构造发生在 Register<Block> ✓，那个时点 SoundEvent 可能还没注册 ✗
    // → 必须延迟到「渲染/交互」阶段（此时两类注册都已完成 ✓）。

    /** 惰性缓存 ✓ —— 避免每帧 new 一个 SoundType ✗。 */
    private net.minecraft.block.SoundType cachedChainSound = null;

    private net.minecraft.block.SoundType chainSound() {
        if (cachedChainSound == null) {
            cachedChainSound = com.alrex.parcool.common.block.ParCoolSoundTypes.chain();
        }
        return cachedChainSound;
    }

    /** 无参版 ✓（部分渲染路径走这个 ✓）。 */
    @Override
    public net.minecraft.block.SoundType getSoundType() {
        return chainSound();
    }

    /** 4 参版 ✓（放置/破坏/踩踏等交互走这个 ✓）。 */
    @Override
    public net.minecraft.block.SoundType getSoundType(IBlockState state, net.minecraft.world.World world,
                                                     BlockPos pos, net.minecraft.entity.Entity entity) {
        return chainSound();
    }

    // ── 形状 ──────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        AxisAlignedBB box = SHAPES[state.getValue(FACING).ordinal()];
        return box == null ? FULL_BLOCK_AABB : box;
    }

    /** **不参与碰撞** ✓（上游 {@code .noCollission()} ✗ → 这里返回空盒 ✓）。 */
    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return NULL_AABB;
    }

    // ── 状态 ↔ meta（两属性自定编码 ✗）────────────────────────────────────

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, ORTHOGONAL);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
                .withProperty(FACING, EnumFacing.byIndex(meta & 7))
                .withProperty(ORTHOGONAL, (meta & ORTHOGONAL_MASK) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex()
                | (state.getValue(ORTHOGONAL) ? ORTHOGONAL_MASK : 0);
    }

    // ── 挂点 / 放置 ───────────────────────────────────────────────────────

    /**
     * 铁钩的挂点在**钩环里侧** ✓ —— 所以要从方块中心往**朝向的反方向**退 0.2 ✓
     * （上游同此 ✓，视觉上索是拴在环里而不是穿模 ✗）。
     */
    @Override
    public Vec3d getActualZiplinePoint(BlockPos pos, IBlockState state) {
        EnumFacing direction = state.getValue(FACING);
        // ⚠️ 1.12.2 的 MCP 名是 getXOffset/YOffset/ZOffset ✗
        //    （1.16 叫 getStepX ✗，老版 MCP 叫 getFrontOffsetX ✗ —— 都不是本工程的映射名 ✓）
        return new Vec3d(
                pos.getX() + 0.5 - direction.getXOffset() * 0.2,
                pos.getY() + 0.5 - direction.getYOffset() * 0.2,
                pos.getZ() + 0.5 - direction.getZOffset() * 0.2
        );
    }

    /**
     * 放置时算出「正交」✓ —— 按玩家**视线**在「该面的平面内」更偏向哪个轴 ✓。
     *
     * <p>例：贴在南北面（XZ 平面的一侧 ✓）时，看<code>|look.y| &lt; |look.x|</code> ✓ ——
     * 即玩家更偏左右看 → 钩子横过来 ✓。照搬上游 ✓。</p>
     */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        IBlockState state = super.getStateForPlacement(world, pos, facing, hitX, hitY, hitZ, meta, placer);

        boolean orthogonal;
        if (!(placer instanceof EntityPlayer)) {
            orthogonal = false;
        } else {
            Vec3d lookVec = placer.getLookVec();
            switch (facing) {
                case NORTH:
                case SOUTH:
                    orthogonal = Math.abs(lookVec.y) < Math.abs(lookVec.x);
                    break;
                case EAST:
                case WEST:
                    orthogonal = Math.abs(lookVec.y) < Math.abs(lookVec.z);
                    break;
                case UP:
                case DOWN:
                default:
                    orthogonal = Math.abs(lookVec.z) < Math.abs(lookVec.x);
                    break;
            }
        }
        return state.withProperty(ORTHOGONAL, orthogonal);
    }
}
