package com.alrex.parcool.common.entity.zipline;

import com.alrex.parcool.common.block.zipline.ZiplineHookTileEntity;
import com.alrex.parcool.common.block.zipline.ZiplineInfo;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import com.alrex.parcool.common.zipline.Zipline;
import com.alrex.parcool.common.zipline.ZiplineType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * **滑索的绳索实体** —— 它「就是那条索」✓ —— 1.12.2 版。
 *
 * <h3>★ 为什么用实体而不是别的东西</h3>
 * <p>实体天然会被**网络同步**到所有客户端 ✓、能跟着区块加载/卸载 ✓、
 * 还能被 {@code getEntitiesWithinAABB} 查到 ✓ —— 正好适合「一条存在于世界里的索」✓</p>
 *
 * <h3>★ 它只存「两端挂钩坐标」，形状是**算出来的**</h3>
 * <p>同步的四个字段：起点、终点、颜色、松紧档 ✓。
 * 真正的曲线靠 {@link #getZipline()} **按需构造并缓存** ✓，
 * 两端或档位一变就重建 ✓（见缓存判据 ✓）。</p>
 *
 * <h3>1.12.2 的替换（本类差异密集 ✗）</h3>
 * <table border="1">
 *   <tr><td>{@code EntityDataManager.defineId(Class, DataSerializer)}</td>
 *       <td><b>{@code EntityDataManager.createKey(Class, DataSerializer)}</b> ✗
 *           —— 仍是静态字段 ✓，但注册走 {@code entityInit()} ✓</td></tr>
 *   <tr><td>{@code defineSynchedData()}</td><td>{@code entityInit()} ✗</td></tr>
 *   <tr><td>{@code DataSerializers.INT}</td><td>{@code DataSerializers.VARINT} ✗</td></tr>
 *   <tr><td>{@code EntityType} + {@code EntitySize}/{@code getDimensions(Pose)}</td>
 *       <td><b>1.12.2 用 {@code Entity(World)} 构造 + {@code setSize(w,h)}</b> ✗</td></tr>
 *   <tr><td>{@code readAdditionalSaveData/addAdditionalSaveData}</td>
 *       <td>{@code readEntityFromNBT/writeEntityToNBT} ✗</td></tr>
 *   <tr><td>{@code noPhysics}</td><td>{@code noClip} ✗</td></tr>
 *   <tr><td>{@code forcedLoading}</td><td>{@code forceSpawn} ✗</td></tr>
 *   <tr><td>{@code getBoundingBoxForCulling}</td><td>{@code getRenderBoundingBox} ✗</td></tr>
 *   <tr><td>{@code interact(...)}</td><td>{@code processInitialInteract(...)} ✗</td></tr>
 *   <tr><td>{@code level}</td><td>{@code world} ✓</td></tr>
 *   <tr><td>{@code Entity#move(MoverType, Vector3d)}</td>
 *       <td>{@code move(MoverType, double, double, double)} ✗</td></tr>
 *   <tr><td>{@code getAddEntityPacket()}</td>
 *       <td><b>不需要</b> ✗ —— 1.12.2 靠 {@code EntityRegistry.registerModEntity} 自动处理 ✓</td></tr>
 * </table>
 *
 * <h3>🚨 修掉了上游的一处真 bug</h3>
 * <p>上游 {@code addAdditionalSaveData} 把三个分量**写进了同一个键** ✗：</p>
 * <pre>
 * compoundNBT.putInt("Tile1_X", startPos.getX());
 * compoundNBT.putInt("Tile1_X", startPos.getY());   // ← 键名抄错，覆盖了 X
 * compoundNBT.putInt("Tile1_X", startPos.getZ());
 * </pre>
 * <p>而读取端读的是 {@code Tile1_X / Tile1_Y / Tile1_Z} ✓ →
 * <b>Y、Z 永远读不到</b> ✗ → 存档重载后绳索位置错乱 ✗</p>
 * <p><b>本移植修正为各自的键名</b> ✓（这是明确的 bug，不是风格差异 ✓），并在读端保留
 * 对**旧键名**的兼容 ✓ —— 万一上游将来修好，格式也能互通 ✓。</p>
 */
public class ZiplineRopeEntity extends Entity {

    private static final DataParameter<BlockPos> DATA_START_POS =
            EntityDataManager.createKey(ZiplineRopeEntity.class, DataSerializers.BLOCK_POS);
    private static final DataParameter<BlockPos> DATA_END_POS =
            EntityDataManager.createKey(ZiplineRopeEntity.class, DataSerializers.BLOCK_POS);
    private static final DataParameter<Integer> DATA_COLOR =
            EntityDataManager.createKey(ZiplineRopeEntity.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> DATA_ZIP_TYPE =
            EntityDataManager.createKey(ZiplineRopeEntity.class, DataSerializers.VARINT);

    /** 缓存：曲线对象 + 它对应的输入（两端与档位 ✓）—— 输入变了才重建 ✓ */
    private BlockPos zipline_start;
    private BlockPos zipline_end;
    private ZiplineType zip_type;
    private Zipline zipline;

    @Nullable
    private AxisAlignedBB cullingBB;

    /** 1.12.2 的实体构造：只要一个 {@code World} ✓（类型由注册表绑定 ✓）。 */
    public ZiplineRopeEntity(World world) {
        super(world);
        // 索很细很薄 ✓；不参与碰撞 ✓
        setSize(0.1f, 0.1f);
    }

    public ZiplineRopeEntity(World world, BlockPos start, BlockPos end, ZiplineInfo info) {
        this(world);
        setStartPos(start);
        setEndPos(end);
        setColor(info.getColor());
        setZiplineType(info.getType());
        setPosition(
                (end.getX() + start.getX()) / 2.0 + 0.5,
                Math.min(end.getY(), start.getY()),
                (end.getZ() + start.getZ()) / 2.0 + 0.5
        );
        noClip = true;
        forceSpawn = true;
    }

    @Override
    protected void entityInit() {
        // 1.12.2 的零坐标常量叫 ORIGIN（1.16 叫 ZERO ✗）
        dataManager.register(DATA_START_POS, BlockPos.ORIGIN);
        dataManager.register(DATA_END_POS, BlockPos.ORIGIN);
        dataManager.register(DATA_COLOR, ZiplineRopeItem.DEFAULT_COLOR);
        dataManager.register(DATA_ZIP_TYPE, ZiplineType.STANDARD.ordinal());
    }

    // ── 曲线（按需构造 + 缓存 ✓）──────────────────────────────────────────

    public Zipline getZipline() {
        BlockPos start = getStartPos();
        BlockPos end = getEndPos();
        ZiplineType type = getZiplineType();

        if (zipline == null
                || !start.equals(zipline_start)
                || !end.equals(zipline_end)
                || !type.equals(zip_type)) {
            zipline_start = start;
            zipline_end = end;
            zip_type = type;
            cullingBB = new AxisAlignedBB(
                    start.getX(), start.getY(), start.getZ(),
                    end.getX(), end.getY(), end.getZ()).grow(1);

            Vec3d startPos;
            Vec3d endPos;
            boolean delayInit = false;

            if (world != null) {
                TileEntity startEntity = world.getTileEntity(start);
                TileEntity endEntity = world.getTileEntity(end);
                if (startEntity instanceof ZiplineHookTileEntity) {
                    startPos = ((ZiplineHookTileEntity) startEntity).getActualZiplinePoint(end);
                } else {
                    startPos = centerOf(start);
                    delayInit = true;
                }
                if (endEntity instanceof ZiplineHookTileEntity) {
                    endPos = ((ZiplineHookTileEntity) endEntity).getActualZiplinePoint(start);
                } else {
                    endPos = centerOf(end);
                    delayInit = true;
                }
            } else {
                startPos = centerOf(start);
                endPos = centerOf(end);
                delayInit = true;
            }

            // ⚠️ 挂钩方块还没加载出来时（区块刚加载 ✗）**不写缓存** ✗ ——
            //    否则会把「用方块中心凑出来的错曲线」缓存住 ✗
            //    等挂钩加载好，下一帧会重算 ✓（上游同此 ✓）
            if (delayInit) {
                return type.getZipline(startPos, endPos);
            } else {
                zipline = type.getZipline(startPos, endPos);
            }
        }
        return zipline;
    }

    private static Vec3d centerOf(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    // ── 渲染裁剪 ─────────────────────────────────────────────────────────

    /**
     * 用「离索的直线距离」来判断要不要渲染 ✓ ——
     * 索可以拉得很长 ✗，默认的「离实体中心多远」判据会**在索中段把它裁掉** ✗
     * （实体位置在索中点 ✓，但索能伸出 115 格 ✗）
     */
    @Override
    public boolean isInRangeToRender3d(double x, double y, double z) {
        BlockPos start = getStartPos();
        BlockPos end = getEndPos();
        if (start.equals(BlockPos.ORIGIN) && end.equals(BlockPos.ORIGIN)) return false;

        double xOffset = end.getX() - start.getX();
        double yOffset = end.getY() - start.getY();
        double zOffset = end.getZ() - start.getZ();
        double baseXOffset = start.getX() - x;
        double baseYOffset = start.getY() - y;
        double baseZOffset = start.getZ() - z;
        double lenSqr = xOffset * xOffset + yOffset * yOffset + zOffset * zOffset;
        if (lenSqr < 1.0E-6) return super.isInRangeToRender3d(x, y, z);

        // 把观察点投影到直线上，取最近点 ✓
        double t = -(xOffset * baseXOffset + yOffset * baseYOffset + zOffset * baseZOffset) / lenSqr;
        Vec3d mostNearPoint = new Vec3d(
                xOffset * t + start.getX(),
                yOffset * t + start.getY(),
                zOffset * t + start.getZ());
        return mostNearPoint.squareDistanceTo(x, y, z)
                < Zipline.MAXIMUM_HORIZONTAL_DISTANCE * Zipline.MAXIMUM_HORIZONTAL_DISTANCE;
    }

    @Nonnull
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (cullingBB == null) {
            BlockPos start = getStartPos();
            BlockPos end = getEndPos();
            cullingBB = new AxisAlignedBB(
                    start.getX(), start.getY(), start.getZ(),
                    end.getX(), end.getY(), end.getZ()).grow(1);
        }
        return cullingBB;
    }

    /** 索自己的位置由端点决定 ✓ —— **不接受任何移动** ✗ */
    @Override
    public void move(@Nonnull MoverType type, double x, double y, double z) {
        // 故意留空：绳子的位置是「两端挂钩的派生物」✓，不该被物理推动 ✓
    }

    // ── 同步字段的读写 ────────────────────────────────────────────────────

    public BlockPos getStartPos() {
        return dataManager.get(DATA_START_POS);
    }

    public BlockPos getEndPos() {
        return dataManager.get(DATA_END_POS);
    }

    private void setStartPos(BlockPos start) {
        dataManager.set(DATA_START_POS, start);
    }

    private void setEndPos(BlockPos end) {
        dataManager.set(DATA_END_POS, end);
    }

    public int getColor() {
        return dataManager.get(DATA_COLOR);
    }

    private void setColor(int color) {
        dataManager.set(DATA_COLOR, color);
    }

    public ZiplineType getZiplineType() {
        return ZiplineType.values()[dataManager.get(DATA_ZIP_TYPE) % ZiplineType.values().length];
    }

    private void setZiplineType(ZiplineType type) {
        dataManager.set(DATA_ZIP_TYPE, type.ordinal());
    }

    /** 右键它 → 判定为「已处理」（避免与其它交互冲突 ✓） */
    @Override
    public boolean processInitialInteract(@Nonnull EntityPlayer player, @Nonnull EnumHand hand) {
        // 1.16 的 ActionResultType.sidedSuccess(isClient) 语义：两侧都算「成功」✓
        return true;
    }

    // ── 存档 ──────────────────────────────────────────────────────────────

    @Override
    protected void readEntityFromNBT(@Nonnull NBTTagCompound compound) {
        // ★ 兼容两种键名：修好的（Tile1_Y/Z ✓）与上游那段有 bug 的（只有 X ✓）
        int sx = compound.getInteger("Tile1_X");
        int sy = compound.hasKey("Tile1_Y") ? compound.getInteger("Tile1_Y") : 0;
        int sz = compound.hasKey("Tile1_Z") ? compound.getInteger("Tile1_Z") : 0;
        int ex = compound.getInteger("Tile2_X");
        int ey = compound.hasKey("Tile2_Y") ? compound.getInteger("Tile2_Y") : 0;
        int ez = compound.hasKey("Tile2_Z") ? compound.getInteger("Tile2_Z") : 0;
        setStartPos(new BlockPos(sx, sy, sz));
        setEndPos(new BlockPos(ex, ey, ez));

        // 颜色与档位上游没存 ✗ ── 一并存上 ✓（否则重载后颜色/松紧会丢 ✗）
        if (compound.hasKey("Color")) setColor(compound.getInteger("Color"));
        if (compound.hasKey("ZipType")) {
            int ord = compound.getInteger("ZipType") % ZiplineType.values().length;
            setZiplineType(ZiplineType.values()[ord]);
        }
    }

    @Override
    protected void writeEntityToNBT(@Nonnull NBTTagCompound compound) {
        BlockPos startPos = getStartPos();
        BlockPos endPos = getEndPos();
        // 🚨 上游把三个分量写进同一个键 ✗ → 这里修正为各自的键名 ✓
        compound.setInteger("Tile1_X", startPos.getX());
        compound.setInteger("Tile1_Y", startPos.getY());
        compound.setInteger("Tile1_Z", startPos.getZ());
        compound.setInteger("Tile2_X", endPos.getX());
        compound.setInteger("Tile2_Y", endPos.getY());
        compound.setInteger("Tile2_Z", endPos.getZ());
        // 上游没存这两项 ✗ → 补上 ✓
        compound.setInteger("Color", getColor());
        compound.setInteger("ZipType", getZiplineType().ordinal());
    }

    /** 绳索不参与任何碰撞判定 ✓（否则会把玩家推开 ✗） */
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }
}
