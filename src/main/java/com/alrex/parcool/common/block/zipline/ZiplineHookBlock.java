package com.alrex.parcool.common.block.zipline;

import com.alrex.parcool.api.SoundEvents;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 滑索**挂钩方块**的基类 ✓ —— 1.12.2 版。
 *
 * <p>挂钩有朝向（贴在方块的哪一面上 ✓），两个挂钩之间可以连一条滑索 ✓。
 * 用**剪刀**右键可以把已连的滑索拆下来 ✓。</p>
 *
 * <h3>★ 1.16 → 1.12.2 的替换（本类是本工程<b>方块移植</b>里差异最密集的一处 ✗）</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code extends DirectionalBlock}</td>
 *       <td><b>没有这个基类</b> ✗ → {@code extends Block} + 自建
 *           {@code PropertyEnum<EnumFacing> FACING} ✓</td></tr>
 *   <tr><td>{@code Properties}（构建器 ✗）</td>
 *       <td>构造器里 {@code setHardness/setSoundType} 等 setter ✓</td></tr>
 *   <tr><td>{@code registerDefaultState}</td><td>{@code setDefaultState} ✓</td></tr>
 *   <tr><td>{@code createBlockStateDefinition(StateContainer.Builder)}</td>
 *       <td>{@code createBlockState()} 返回 {@code BlockStateContainer} ✗</td></tr>
 *   <tr><td>{@code BlockState}</td><td>{@link IBlockState} ✗</td></tr>
 *   <tr><td>{@code getStateForPlacement(BlockItemUseContext)}</td>
 *       <td>{@code getStateForPlacement(World, BlockPos, EnumFacing, float,float,float, int, EntityLivingBase)} ✗</td></tr>
 *   <tr><td>{@code getPistonPushReaction} → {@code PushReaction.DESTROY}</td>
 *       <td>{@code getMobilityFlag} → {@code EnumPushReaction.DESTROY} ✗</td></tr>
 *   <tr><td>{@code onRemove(...)}</td><td>{@code breakBlock(...)} ✗</td></tr>
 *   <tr><td>{@code updateShape(...)}</td><td>{@code neighborChanged(...)} ✗（1.12.2 没有 shape 更新回调 ✗）</td></tr>
 *   <tr><td>{@code canSurvive(state, world, pos)}</td>
 *       <td>{@code canPlaceBlockAt(world, pos)}（自己从 world 取 state ✗）✓</td></tr>
 *   <tr><td>{@code hasTileEntity(state)} + {@code createTileEntity(state, world)}</td>
 *       <td><b>{@code ITileEntityProvider} + {@code createNewTileEntity(World, int)}</b> ✗</td></tr>
 *   <tr><td>{@code use(...)} → {@code ActionResultType}</td>
 *       <td>{@code onBlockActivated(...)} → {@code boolean} ✗</td></tr>
 *   <tr><td>{@code ShearsItem}</td><td>{@link ItemShears} ✗</td></tr>
 *   <tr><td>{@code InventoryHelper.dropItemStack(world,x,y,z,stack)}</td>
 *       <td>{@code Block.spawnAsEntity(world, pos, stack)} ✓</td></tr>
 *   <tr><td>{@code stack.isDamageableItem()/hurtAndBreak(...)}</td>
 *       <td>{@code stack.isItemStackDamageable()/damageItem(1, player)} ✗</td></tr>
 *   <tr><td>{@code world.isClientSide()}</td><td>{@code world.isRemote} ✓</td></tr>
 *   <tr><td>{@code Blocks.AIR.defaultBlockState()}</td><td>{@code Blocks.AIR.getDefaultState()} ✓</td></tr>
 *   <tr><td>{@code Vector3d}</td><td>{@link Vec3d} ✓</td></tr>
 * </table>
 *
 * <p><b>⚠️ 上游的 {@code isPathfindable} 未移植</b> ✗ —— 1.12.2 的 {@code Block} 没有对应钩子 ✓
 * （它在 1.13+ 才引入 ✓）。影响仅是「寻路 AI 可能认为挂钩可通行」✗，很轻微 ✓。</p>
 */
public class ZiplineHookBlock extends Block implements ITileEntityProvider {

    /** 挂钩贴在哪个面 ✓ —— 1.16 由 {@code DirectionalBlock} 提供 ✓，1.12.2 要自建 ✗ */
    public static final PropertyEnum<EnumFacing> FACING = PropertyEnum.create("facing", EnumFacing.class);

    /**
     * 1.12.2 的方块建造：材质由构造参数给 ✓（1.16 走 {@code Properties} ✗）。
     *
     * <p><b>⚠️ 全限定名调用</b>：1.12.2 的 {@code Block} <b>没有</b> {@code (Material, Material)}
     * 那种双材质重载 ✗，用 {@code (Material)} 即可 ✓。</p>
     */
    public ZiplineHookBlock(Material material) {
        super(material);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }

    /** 滑索在**世界空间**里的实际挂点 ✓ —— 默认取方块中心 ✓（子类会偏到钩子上 ✓）。 */
    public Vec3d getActualZiplinePoint(BlockPos pos, IBlockState state) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    // ── 方块状态 ──────────────────────────────────────────────────────────

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        // 1.12.2 是 byIndex（1.16 叫 getFront ✗）
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta % 6));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    /** 被活塞推时直接**破坏**（不掉落成物品 ✓）。 */
    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.DESTROY;
    }

    // ── ★ 非实心方块：三个「整格」判定必须全部返回 false ✗ ──────────────
    //
    // 1.12.2 里这三个的默认实现<b>全是 {@code true}</b> ✗（反编译源码：
    // isOpaqueCube `Block.java:666` / isFullCube `:405` / isFullBlock `:170`）。
    //
    // ⚠️ 实测症状（2026-10-11，Kiwi 上游对比确认）：
    //   ① 钩子**相邻方块的面被剔除** → 看见方块内部 = 透视 ✗
    //      （相邻方块以为「整格被挡住了，不用画那一面」✗）
    //   ② **玩家走进钩子所在格时被贴图糊满屏幕** ✗
    //      （引擎按实心格渲染，玩家在格子内部时看见内壁 ✓）
    // 两者同一根因 ✓ —— 钩子的模型只占一小块，但方块声明自己「占满整格」✗。
    //
    // ⭐ 上游那侧没这个问题，是因为 1.16 的 {@code BlockBehaviour.Properties}
    //    默认 {@code isViewBlocking = false} ✗（1.12.2 没有这套属性 ✗，只能手写 ✓）。

    /** **不挡光、不遮挡** ✓ —— 相邻方块因此会正常绘制朝向钩子的那一面 ✓。 */
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    /** **不是完整立方体** ✓ —— 模型只有一小块几何 ✓。 */
    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    /** 与 {@link #isFullCube} 同义的另一入口 ✓（部分渲染路径走这个 ✓）。 */
    @Override
    public boolean isFullBlock(IBlockState state) {
        return false;
    }

    /** 不是「常规立方体」✓ —— 红石/光照/寻路把它当非实心处理 ✓。 */
    @Override
    public boolean isNormalCube(IBlockState state, net.minecraft.world.IBlockAccess world, BlockPos pos) {
        return false;
    }

    /** 侧面不是实心面 ✓（活塞/红石连接的判定 ✓）。 */
    @Override
    public boolean isSideSolid(IBlockState base_state, net.minecraft.world.IBlockAccess world, BlockPos pos, EnumFacing side) {
        return false;
    }

    /** 放置时朝向 = **被点击的那个面** ✓ */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, facing);
    }

    /**
     * 支撑面还在不在 ✓ —— 挂钩贴的那一面必须是**实心面** ✓，否则挂钩掉落 ✓。
     *
     * <p>⚠️ 1.12.2 没有 {@code canSurvive(state,...)} ✗ → 自己从 world 取 state ✓</p>
     */
    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != this) {
            // 放置前的预检：拿不到 FACING，只能先放行（放置后再由 neighborChanged 检查 ✓）
            return super.canPlaceBlockAt(world, pos);
        }
        EnumFacing facing = state.getValue(FACING);
        BlockPos supportingBlock = pos.offset(facing.getOpposite());
        // isSideSolid 判定「那一面是否实心」✓
        return world.getBlockState(supportingBlock).isSideSolid(world, supportingBlock, facing);
    }

    /** 邻居变化时重新检查支撑 ✓ —— 1.12.2 用它替代 1.16 的 {@code updateShape} ✓ */
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (!canPlaceBlockAt(world, pos)) {
            // 支撑没了 → 挂钩掉落 ✓
            dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    /** 被破坏时：把这条钩子上的**所有连接**都拆下来掉出来 ✓ */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity instanceof ZiplineHookTileEntity) {
                List<ItemStack> itemStacks = ((ZiplineHookTileEntity) tileEntity).removeAllConnection();
                for (ItemStack stack : itemStacks) {
                    spawnAsEntity(world, pos, stack);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    /**
     * 剪刀右键 → 拆下这条钩子上的滑索 ✓
     *
     * <p>⚠️ 返回 {@code boolean}（1.12.2 ✗）而非 {@code ActionResultType}（1.16 ✗）✓</p>
     */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (!(stack.getItem() instanceof ItemShears)) return false;

        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof ZiplineHookTileEntity)) return false;

        ZiplineHookTileEntity hook = (ZiplineHookTileEntity) tileEntity;
        if (hook.getConnectionPoints().isEmpty()) return false;

        List<ItemStack> itemStacks = hook.removeAllConnection();
        if (!itemStacks.isEmpty()) {
            player.playSound(SoundEvents.ZIPLINE_REMOVE.get(), 1, 1);
        }
        if (world.isRemote) {
            return true;
        }
        for (ItemStack it : itemStacks) {
            spawnAsEntity(world, pos, it);
        }
        if (!itemStacks.isEmpty() && stack.isItemStackDamageable()) {
            // 1.12.2 的两参版本（1.16 是三参带回调 ✗）
            stack.damageItem(1, player);
        }
        return true;
    }

    // ── 挂钩总是带一个 TileEntity（存连接表 ✓）──────────────────────────────

    @Nullable
    @Override
    public TileEntity createNewTileEntity(@Nonnull World worldIn, int meta) {
        return new ZiplineHookTileEntity();
    }
}
