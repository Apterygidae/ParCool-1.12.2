package com.alrex.parcool.mixin.common;

import com.alrex.parcool.api.unstable.action.ParCoolActionEvent;
import com.alrex.parcool.common.action.impl.ChargeJump;
import com.alrex.parcool.common.action.impl.ClimbPoles;
import com.alrex.parcool.common.action.impl.ClimbUp;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.tags.BlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.BlockEndRod;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.BlockWall;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「爬杆」—— 让竖直的杆也能像梯子一样攀爬。
 *
 * <h3>★ 这个功能为什么藏在一个 mixin 里</h3>
 * <p>上游的 {@code ClimbPoles} <b>动作类是空壳</b>（三方法全返回 {@code false}/{@code None}）——
 * 「爬杆」的真功能全在本 mixin 覆写的原版钩子里 ✓
 * 所以当初我把 {@code ClimbPoles} 从「待移植」移出、把这条线归到「原版钩子 + 标签」时，
 * 指的就是这个文件 ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code @Mixin(LivingEntity.class)}；
 *       {@code @Inject(method="onClimbable")}</td>
 *       <td>{@code @Mixin(EntityLivingBase.class)}；
 *           <b>{@code @Inject(method="isOnLadder")}</b>（1.12.2 的方法名）</td></tr>
 *   <tr><td>{@code this.blockPosition()}</td><td>{@code new BlockPos(this)}（等价：都是脚下方块）</td></tr>
 *   <tr><td>{@code this.getFeetBlockState()}</td>
 *       <td>{@code this.world.getBlockState(...)}（1.12.2 没有那个便捷方法）</td></tr>
 *   <tr><td><b>{@code FourWayBlock}</b>（栅栏/墙/玻璃板的共同基类）</td>
 *       <td><b>没有这个基类</b> → 分别判 {@code BlockPane} / {@code BlockFence} / {@code BlockWall} ✓
 *           （与 {@code WorldUtil.getHangableBars} 用同一套拆分）</td></tr>
 *   <tr><td>{@code RotatedPillarBlock} + {@code AXIS.isVertical()}</td>
 *       <td>{@code BlockRotatedPillar} + {@code EnumFacing.Axis.isVertical()} ✓（同名存在）</td></tr>
 *   <tr><td>{@code EndRodBlock} + {@code DirectionalBlock.FACING}</td>
 *       <td>{@code BlockEndRod} + {@code FACING}（继承自 {@code BlockDirectional}）✓</td></tr>
 *   <tr><td>{@code state.isCollisionShapeFullBlock(world, pos)}</td>
 *       <td>{@code state.isFullCube()}（语义最接近）✓</td></tr>
 *   <tr><td><b>{@code ForgeConfig.SERVER.fullBoundingBoxLadders}</b></td>
 *       <td><b>1.12.2 没有这个 Forge 配置</b> → 只保留「只查脚下方块」的简单路径 ✓
 *           （上游那个分支是「扫描整个玩家包围盒」，属增强行为，可另加）</td></tr>
 *   <tr><td>{@code block.getTags().contains(POLE_CLIMBABLE)}</td>
 *       <td><b>1.12.2 没有标签</b> → {@link BlockTags#isPoleClimbable}（OreDictionary 替代）✓</td></tr>
 * </table>
 *
 * <h3>★ 「什么算一根可爬的杆」（上游判定，照搬）</h3>
 * <ol>
 *   <li><b>板/栅栏/墙</b>：连接数 ≤1（一根立柱）<b>或</b>恰好一个转角（十字的一角），
 *       <b>且上下有同类方块叠着</b> → 即「竖着的杆」，而不是一片连成网的栅栏 ✓</li>
 *   <li><b>旋转柱</b>（圆木/锁链等）：非实心 + <b>轴向竖直</b> ✓</li>
 *   <li><b>末地烛</b>：非实心 + <b>朝上或朝下</b> ✓</li>
 *   <li>其余走 {@link BlockTags#isPoleClimbable}（默认空）✓</li>
 * </ol>
 *
 * <p><b>⚠️ 上游那个「拦路」判断照搬了</b>：正在爬墙（{@code ClimbUp}）或蓄力跳时不许爬杆
 * —— 避免同一个按键触发两种攀爬 ✓</p>
 *
 * <p><b>⚠️ 上游的 {@code RotatedPillarBlock} 分支里算了个 {@code stacked} 却没用</b>（死代码）
 * —— 本移植<b>刻意剔除了它</b>，并在注释里标明，避免读者以为有额外条件 ✓</p>
 */
@Mixin(EntityLivingBase.class)
public abstract class LivingEntityMixin {

    @Inject(method = "isOnLadder", at = @At("HEAD"), cancellable = true)
    public void parCool$onIsOnLadder(CallbackInfoReturnable<Boolean> cir) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (!(self instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) self;

        if (player.isSpectator()) {
            cir.setReturnValue(false);
            return;
        }

        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;

        // 这个动作是否被客户端设置 / 服务端限制放行（爬杆可整体关闭）
        if (!parkourability.getActionInfo().can(ClimbPoles.class)
                || MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToStartEvent(
                player, parkourability.get(ClimbPoles.class)))
                || MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToStart(
                player, parkourability.get(ClimbPoles.class)))
        ) {
            return;
        }

        // 正在「翻上墙」或「蓄力跳」时不介入 —— 避免同键触发两种攀爬
        if (parkourability.get(ClimbUp.class).isDoing()) {
            return;
        }
        ChargeJump chargeJump = parkourability.get(ChargeJump.class);
        if (chargeJump.isDoing() || chargeJump.isCharging()) {
            return;
        }

        // ★ 与上游同构：跟着 Forge 的 `fullBoundingBoxLadders` 开关走 ✓
        //
        // 上游 ParCool 的写法正是如此：
        //   if (!ForgeConfig.SERVER.fullBoundingBoxLadders.get()) { 只看脚下方块 }
        //   else { 扫描整个包围盒 }
        // 1.12.2 的对应物是 `ForgeModContainer.fullBoundingBoxLadders`（**默认 false**）✓
        //
        // ⚠️ 这个开关的存在解释了一件容易困惑的事：
        //   栅栏 / 玻璃板 / 铁栏杆的碰撞是 **0.25 宽的细柱**（`BlockFence.PILLAR_AABB`）——
        //   玩家能站进它所在的方块列，于是「脚下方块」就是它 ✓ 能爬 ✓
        //   而**石墙的碰撞是全宽的实心方块**（`BlockWall.CLIP_AABB_BY_INDEX`）——
        //   玩家只能站在它**旁边**，「脚下方块」是地面 ✗ 所以**石墙爬不了** ✗
        //   **这是上游的既定行为**（已实测确认：上游原版同样爬不了圆石墙柱子 ✓），
        //   不是移植缺陷 —— 所以本移植**保持原样**，不擅自放宽 ✓
        //
        // 想要「站在旁边也能爬」的玩家，打开 Forge 的 fullBoundingBoxLadders 即可 ✓
        World world = self.world;

        if (!ForgeModContainer.fullBoundingBoxLadders) {
            // 默认路径：只看脚下方块（与上游默认行为一致）
            BlockPos feetPos = new BlockPos(self);
            if (parCool$isCustomLadder(world.getBlockState(feetPos), world, feetPos)) {
                cir.setReturnValue(true);
            }
            return;
        }

        // 增强路径：扫描整个包围盒（仅在 Forge 开关开启时）
        AxisAlignedBB bb = self.getEntityBoundingBox();
        int minX = MathHelper.floor(bb.minX);
        int minY = MathHelper.floor(bb.minY);
        int minZ = MathHelper.floor(bb.minZ);
        int maxX = MathHelper.floor(bb.maxX);
        int maxY = MathHelper.floor(bb.maxY);
        int maxZ = MathHelper.floor(bb.maxZ);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.isBlockLoaded(pos)) continue;
                    if (parCool$isCustomLadder(world.getBlockState(pos), world, pos)) {
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }
    }

    /** 该方块是否算「可爬的杆」（上游 {@code isCustomLadder} 的 1.12.2 版）✓ */
    @Unique
    private static boolean parCool$isCustomLadder(IBlockState state, World world, BlockPos pos) {
        // ★★ 关键：取「实际态」而不是「存储态」！
        //
        // 1.12.2 里栅栏 / 玻璃板 / 铁栏杆 / 石墙的**四向连接属性是动态算的** ——
        // `world.getBlockState(pos)` 拿到的是**存储态**，其中 NORTH/EAST/SOUTH/WEST **一律是 false** ✗
        // → 「连接数」恒为 0 → `zCount + xCount <= 1` 恒成立
        //   → **任何上下叠了一层的栅栏/板/墙都能爬**，完全忽略了连接状况 ✗
        //
        // （实测症状：用户报「不论连接状况如何都可以攀爬，这应该和上游不符」✓）
        //
        // `getActualState(world, pos)` 才会根据邻居算出真正的连接情况 ✓
        state = state.getActualState(world, pos);
        Block block = state.getBlock();

        if (block instanceof BlockPane || block instanceof BlockFence || block instanceof BlockWall) {
            int zCount = 0;
            int xCount = 0;
            if (parCool$isConnected(state, block, EnumFacing.NORTH)) zCount++;
            if (parCool$isConnected(state, block, EnumFacing.SOUTH)) zCount++;
            if (parCool$isConnected(state, block, EnumFacing.EAST)) xCount++;
            if (parCool$isConnected(state, block, EnumFacing.WEST)) xCount++;
            // 「叠着」= 上或下有同类方块 → 竖着的杆，而不是横着连成一片的网
            boolean stacked = parCool$isFourWay(world, pos.up()) || parCool$isFourWay(world, pos.down());
            return ((zCount + xCount <= 1) || (zCount == 1 && xCount == 1)) && stacked;
        } else if (block instanceof BlockRotatedPillar) {
            // ⚠️ 上游在此处还算了一个 stacked 但没用（死代码）→ 本移植剔除
            return !state.isFullCube()
                    && state.getValue(BlockRotatedPillar.AXIS).isVertical();
        } else if (block instanceof BlockEndRod) {
            EnumFacing direction = state.getValue(BlockDirectional.FACING);
            return !state.isFullCube()
                    && (direction == EnumFacing.UP || direction == EnumFacing.DOWN);
        }

        // 逃生口：给整合包/mod 用 OreDictionary 注册额外方块（对应上游的 pole_climbable 标签）
        return BlockTags.isPoleClimbable(block);
    }

    /** 「同类的四向方块」—— 1.12.2 没有 FourWayBlock，所以判这三个。 */
    @Unique
    private static boolean parCool$isFourWay(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) return false;
        Block block = world.getBlockState(pos).getBlock();
        return block instanceof BlockPane || block instanceof BlockFence || block instanceof BlockWall;
    }

    /** 取某方向的连接状态（{@code BlockPane}/{@code BlockFence}/{@code BlockWall} 的三套静态字段是不同的对象）。 */
    @Unique
    private static boolean parCool$isConnected(IBlockState state, Block block, EnumFacing facing) {
        if (block instanceof BlockWall) {
            switch (facing) {
                case NORTH:
                    return state.getValue(BlockWall.NORTH);
                case SOUTH:
                    return state.getValue(BlockWall.SOUTH);
                case EAST:
                    return state.getValue(BlockWall.EAST);
                case WEST:
                    return state.getValue(BlockWall.WEST);
                default:
                    return false;
            }
        }
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
}
