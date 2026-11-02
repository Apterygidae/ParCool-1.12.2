package com.alrex.parcool.common.item.zipline;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.common.block.zipline.ZiplineHookBlock;
import com.alrex.parcool.common.block.zipline.ZiplineHookTileEntity;
import com.alrex.parcool.common.block.zipline.ZiplineInfo;
import com.alrex.parcool.common.zipline.Zipline;
import com.alrex.parcool.common.zipline.ZiplineType;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * **绳索物品** —— 用来连滑索 ✓ —— 1.12.2 版。
 *
 * <h3>★ 使用流程</h3>
 * <ol>
 *   <li>右键**第一个**挂钩 → 记下坐标 ✓（聊天栏提示「已绑定点」✓）</li>
 *   <li>右键**第二个**挂钩 → 校验距离/坡度/中间有无障碍 ✓ → 建立连接 ✓ 消耗一个物品 ✓</li>
 * </ol>
 * <p>潜行右键可以：未绑定时**切换松紧档位** ✓；已绑定时**清除绑定** ✓</p>
 *
 * <h3>★ {@link #INSTANCE} 是为谁加的</h3>
 * <p>1.12.2 没有 1.16 那种「注册表 supplier」写法 ✗（{@code Items.ZIPLINE_ROPE::get} ✗）
 * → 挂钩 TileEntity 要造绳索物品时拿不到工厂 ✗
 * → 改为**注册时给 {@link #INSTANCE} 赋值** ✓（见 {@code RegistryHandler} ✓）</p>
 *
 * <h3>1.12.2 的替换（差异密集 ✗）</h3>
 * <table border="1">
 *   <tr><td>{@code ItemUseContext}</td>
 *       <td><b>没有这个类</b> ✗ → {@code onItemUse(EntityPlayer, World, BlockPos, EnumHand, EnumFacing, float,float,float)} ✓</td></tr>
 *   <tr><td>{@code context.isSecondaryUseActive()}</td>
 *       <td>{@code player.isSneaking()} ✓</td></tr>
 *   <tr><td>{@code context.getClickedFace()}</td><td>回调直接给了 {@code facing} ✓</td></tr>
 *   <tr><td>{@code player.displayClientMessage(c, aboveHotbar)}</td>
 *       <td>{@code player.sendStatusMessage(c, aboveHotbar)} ✓</td></tr>
 *   <tr><td>{@code appendHoverText(..., List&lt;ITextComponent&gt;, ...)}</td>
 *       <td>{@code addInformation(..., List&lt;String&gt;, ...)} ✗ ——
 *           <b>列表元素是 String</b> ✗ → 用 {@code I18n.format(...)} 取本地化文本 ✓</td></tr>
 *   <tr><td>{@code TranslationTextComponent/withStyle(TextFormatting.X)}</td>
 *       <td>{@code I18n.format(...)} + 拼接 {@code TextFormatting.X.toString()} ✓</td></tr>
 *   <tr><td>{@code MathHelper.square(x)}</td><td><b>1.12.2 没有</b> ✗ → 写 {@code x * x} ✓</td></tr>
 *   <tr><td>{@code CompoundNBT} 系列</td>
 *       <td>{@code NBTTagCompound} + {@code setInteger/getInteger/hasKey/removeTag} ✗</td></tr>
 *   <tr><td>{@code Item.Properties}</td><td>无参 {@code super()} ✓（属性用 setter 或在注册时设 ✓）</td></tr>
 *   <tr><td>{@code IItemColor}（染色图标 ✗）</td>
 *       <td><b>1.12.2 用 {@code ItemColors} 注册</b> ✗ ——
 *           需在客户端代理里做 ✓ <b>本批未接</b> ✗（见类尾注释 ✓）</td></tr>
 * </table>
 */
public class ZiplineRopeItem extends Item {

    /** 绳索的默认颜色（蓝 ✓） */
    public static final int DEFAULT_COLOR = 0x4C7FE6;

    /**
     * 注册时赋值的单例 ✓ —— 见类注释（1.12.2 没有 supplier 式注册 ✗）。
     *
     * <p><b>⚠️ 使用前必须判空</b> ✗ —— 理论上注册一定早于任何世界交互 ✓，
     * 但静态初始化顺序无法完全保证 ✗（见 TileEntity 里的判空 ✓）。</p>
     */
    @Nullable
    public static ZiplineRopeItem INSTANCE = null;

    public ZiplineRopeItem() {
        super();
    }

    // ── 提示文本 ─────────────────────────────────────────────────────────

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World worldIn,
                               @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flagIn) {
        NBTTagCompound tag = stack.getTagCompound();

        if (tag != null && tag.hasKey("Tile_X") && tag.hasKey("Tile_Y") && tag.hasKey("Tile_Z")) {
            tooltip.add(TextFormatting.YELLOW + I18n.format("parcool.gui.text.zipline.bind_pos",
                    tag.getInteger("Tile_X") + ", " + tag.getInteger("Tile_Y") + ", " + tag.getInteger("Tile_Z")));
        } else {
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("parcool.gui.text.zipline.not_bound"));
        }
        tooltip.add("");
        tooltip.add(TextFormatting.GRAY + I18n.format("parcool.gui.text.zipline.tension",
                getZiplineType(stack).getTranslationName().getUnformattedText()));
        if (hasCustomColor(stack)) {
            tooltip.add(TextFormatting.BLUE + I18n.format("parcool.gui.text.zipline.colored"));
        }
    }

    // ── 交互 ─────────────────────────────────────────────────────────────

    @Override
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos,
                                      @Nonnull EnumHand hand, @Nonnull EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        NBTTagCompound tag = stack.getTagCompound();

        // ── 情况 A：已经绑了第一个点 ✓ ──
        if (tag != null && hasBlockPosition(stack)) {
            if (world.getBlockState(pos).getBlock() instanceof ZiplineHookBlock) {
                BlockPos start = getBlockPosition(stack);
                if (start == null) return EnumActionResult.FAIL;

                BlockPos end = pos;
                if (start.equals(end)) return EnumActionResult.FAIL;

                // ① 水平距离不能太远 ✓
                double horizontalDistSqr = (double) (start.getX() - end.getX()) * (start.getX() - end.getX())
                        + (double) (start.getZ() - end.getZ()) * (start.getZ() - end.getZ());
                if (horizontalDistSqr > Zipline.MAXIMUM_HORIZONTAL_DISTANCE * Zipline.MAXIMUM_HORIZONTAL_DISTANCE) {
                    if (world.isRemote) {
                        player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                                "parcool.message.zipline.too_far"), true);
                    }
                    return EnumActionResult.FAIL;
                }
                // ② 坡度不能太陡 ✓
                double verticalDist = Math.abs(end.getY() - start.getY());
                if (verticalDist * MathHelper.fastInvSqrt(horizontalDistSqr) > 1.
                        || verticalDist > Zipline.MAXIMUM_VERTICAL_DISTANCE) {
                    if (world.isRemote) {
                        player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                                "parcool.message.zipline.too_steep"), true);
                    }
                    return EnumActionResult.FAIL;
                }

                TileEntity startEntity = world.getTileEntity(start);
                TileEntity endEntity = world.getTileEntity(end);
                if (!(startEntity instanceof ZiplineHookTileEntity)
                        || !(endEntity instanceof ZiplineHookTileEntity)) {
                    removeBlockPosition(stack);
                    if (world.isRemote) {
                        player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                                "parcool.message.zipline.point_not_found"), true);
                    }
                    return EnumActionResult.FAIL;
                }

                ZiplineHookTileEntity startZipEntity = (ZiplineHookTileEntity) startEntity;
                ZiplineHookTileEntity endZipEntity = (ZiplineHookTileEntity) endEntity;

                // ③ 索的中段不能被方块挡住 ✓
                if (getZiplineType(stack).getZipline(
                        startZipEntity.getActualZiplinePoint(null),
                        endZipEntity.getActualZiplinePoint(null)).conflictsWithSomething(world)) {
                    player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                            "parcool.message.zipline.obstacle_detected"), true);
                    return EnumActionResult.FAIL;
                }

                // ④ 真正建立连接（仅服务端 ✓）
                if (!world.isRemote) {
                    if (!startZipEntity.connectTo(endZipEntity,
                            new ZiplineInfo(getZiplineType(stack), getColor(stack)))) {
                        player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                                "parcool.message.zipline.already_exist"), true);
                        return EnumActionResult.FAIL;
                    }
                    stack.shrink(1);
                }
                player.playSound(SoundEvents.ZIPLINE_SET.get(), 1, 1);
                removeBlockPosition(stack);
                return EnumActionResult.SUCCESS;
            }

            // 潜行右键 → 清除已绑定的点 ✓
            if (player.isSneaking()) {
                if (world.isRemote) {
                    player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                            "parcool.message.zipline.reset_point"), true);
                }
                removeBlockPosition(stack);
                return EnumActionResult.SUCCESS;
            }
            return EnumActionResult.FAIL;
        }

        // ── 情况 B：还没绑点 ✓ ──
        if (world.getBlockState(pos).getBlock() instanceof ZiplineHookBlock) {
            setBlockPosition(stack, pos);
            if (world.isRemote) {
                player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                        "parcool.message.zipline.set_point", pos.toString()), true);
            }
            return EnumActionResult.SUCCESS;
        }
        if (player.isSneaking()) {
            changeZiplineType(stack);
            if (world.isRemote) {
                player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                        "parcool.message.zipline.change_tension",
                        getZiplineType(stack).getTranslationName().getUnformattedText()), true);
            }
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.FAIL;
    }

    // ── NBT 数据辅助（全是静态 ✓ —— 供 TileEntity 等处复用 ✓）──────────────

    public static void setBlockPosition(ItemStack stack, BlockPos pos) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger("Tile_X", pos.getX());
        tag.setInteger("Tile_Y", pos.getY());
        tag.setInteger("Tile_Z", pos.getZ());
    }

    public static void removeBlockPosition(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return;
        }
        tag.removeTag("Tile_X");
        tag.removeTag("Tile_Y");
        tag.removeTag("Tile_Z");
    }

    public static boolean hasBlockPosition(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey("Tile_X") && tag.hasKey("Tile_Y") && tag.hasKey("Tile_Z");
    }

    @Nullable
    public static BlockPos getBlockPosition(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return null;
        }
        return new BlockPos(tag.getInteger("Tile_X"), tag.getInteger("Tile_Y"), tag.getInteger("Tile_Z"));
    }

    /** 设颜色；**等于默认色时把键删掉** ✓（省 NBT ✓，也让「是否染过色」可判 ✓） */
    public static void setColor(ItemStack stack, int color) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        if (color != DEFAULT_COLOR) tag.setInteger("color", color);
        else tag.removeTag("color");
    }

    public static int getColor(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("color")) {
            return DEFAULT_COLOR;
        }
        return tag.getInteger("color");
    }

    public static boolean hasCustomColor(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey("color");
    }

    public static ZiplineType getZiplineType(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("zipline_type")) {
            return ZiplineType.STANDARD;
        }
        return ZiplineType.values()[tag.getByte("zipline_type") % ZiplineType.values().length];
    }

    /** 循环切到下一档 ✓（潜行右键循环 ✓） */
    public static void changeZiplineType(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setByte("zipline_type",
                (byte) ((getZiplineType(stack).ordinal() + 1) % ZiplineType.values().length));
    }

    public static void setZiplineType(ItemStack stack, ZiplineType type) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setByte("zipline_type", (byte) type.ordinal());
    }

    // ⚠️ 染色图标（1.16 的 IItemColor ✗）在 1.12.2 要用 ItemColors 注册 ✗ ——
    //    需在客户端代理里做 ✓，**本批未接** ✗（P6 收尾时补 ✓）。
    //    影响的只是「物品栏里图标按染色显示」✗，不影响功能 ✓。
}
