package com.alrex.parcool.common.block.zipline;

import com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 挂钩方块的 TileEntity —— **存这张钩子上连了哪些滑索** ✓ —— 1.12.2 版。
 *
 * <h3>★ 数据结构</h3>
 * <ul>
 *   <li>{@code connections} —— <b>对端挂钩坐标 → 那条索的元数据</b>（松紧 + 颜色 ✓）<br>
 *       两端各存一份 ✓（所以任一端被拆，另一端也能清理 ✓）</li>
 *   <li>{@code connectionEntities} —— <b>仅服务端</b>：对端坐标 → 实际的绳索实体 ✓
 *       （客户端不需要 ✓，实体由网络同步 ✓）</li>
 * </ul>
 * <p>用 {@link TreeMap} 是为了**顺序稳定** ✓（NBT 存档与比较都不受遍历顺序影响 ✓）。</p>
 *
 * <h3>★ 绳索实体的「自愈」</h3>
 * <p>实体可能因为区块卸载等原因丢失 ✗ → {@link #update()} 每 tick 检查
 * 「连接数 > 实体数」✗ → 重新生成缺的那些 ✓；对端已不承认这条连接的就**反过来清掉** ✓</p>
 *
 * <h3>1.12.2 的替换（本类差异密集 ✗）</h3>
 * <table border="1">
 *   <tr><td>{@code TileEntityType<?>} 构造参数</td>
 *       <td><b>1.12.2 的 {@code TileEntity} 是<b>无参</b>构造</b> ✗，类型由注册表决定 ✓</td></tr>
 *   <tr><td>{@code ITickableTileEntity}</td><td>{@link ITickable}（方法名 {@code update()} ✗）</td></tr>
 *   <tr><td>{@code level}</td><td>{@code world} ✓</td></tr>
 *   <tr><td>{@code getBlockPos()}</td><td>{@code getPos()} ✓</td></tr>
 *   <tr><td>{@code save/load}</td><td>{@code writeToNBT/readFromNBT} ✗</td></tr>
 *   <tr><td>{@code getUpdateTag/handleUpdateTag}</td>
 *       <td>{@code getUpdateTag} ✓ + {@code getUpdatePacket}/{@code onDataPacket} ✗</td></tr>
 *   <tr><td>{@code level.addFreshEntity(e)}</td><td>{@code world.spawnEntity(e)} ✓</td></tr>
 *   <tr><td>{@code level.getBlockEntity(pos)}</td><td>{@code world.getTileEntity(pos)} ✓</td></tr>
 *   <tr><td>{@code level.isLoaded(pos)}</td><td>{@code world.isBlockLoaded(pos)} ✓</td></tr>
 *   <tr><td>{@code CompoundNBT/ListNBT/INBT}</td>
 *       <td>{@code NBTTagCompound/NBTTagList/NBTBase} ✗</td></tr>
 *   <tr><td>{@code new ItemStack(Items.ZIPLINE_ROPE::get)}</td>
 *       <td><b>1.12.2 没有这种 supplier 写法</b> ✗ → 用
 *           {@link ZiplineRopeItem#INSTANCE}（注册时赋值 ✓）</td></tr>
 * </table>
 */
public class ZiplineHookTileEntity extends TileEntity implements ITickable {

    private final TreeMap<BlockPos, ZiplineInfo> connections = new TreeMap<>();

    /** 仅服务端 ✓ —— 对端坐标 → 绳索实体 ✓ */
    private final TreeMap<BlockPos, ZiplineRopeEntity> connectionEntities = new TreeMap<>();

    /** 1.12.2 的 TileEntity 需要无参构造 ✓（类型在注册时绑定 ✓）。 */
    public ZiplineHookTileEntity() {
    }

    public Set<BlockPos> getConnectionPoints() {
        return connections.keySet();
    }

    private TreeMap<BlockPos, ZiplineInfo> getConnectionInfo() {
        return connections;
    }

    /** 拆掉这张钩子上的**所有**连接，返回应该掉出来的绳索物品 ✓ */
    public List<ItemStack> removeAllConnection() {
        if (world == null) return Collections.emptyList();

        // ① 让对端也把我这条连接删掉 ✓
        getConnectionPoints().stream()
                .filter(world::isBlockLoaded)
                .map(world::getTileEntity)
                .map(it -> it instanceof ZiplineHookTileEntity ? (ZiplineHookTileEntity) it : null)
                .filter(Objects::nonNull)
                .forEach(it -> it.onPairHookRegistrationRemoved(this));

        List<ItemStack> itemStacks = Collections.emptyList();
        if (!world.isRemote) {
            // ② 服务端：把实体也删掉，并按颜色掉出绳索物品 ✓
            connectionEntities.values().forEach(ZiplineRopeEntity::setDead);
            itemStacks = getConnectionInfo().values().stream().map(it -> {
                ItemStack stack = new ItemStack(ZiplineRopeItem.INSTANCE);
                ZiplineRopeItem.setColor(stack, it.getColor());
                return stack;
            }).collect(Collectors.toList());
        }
        connectionEntities.clear();
        getConnectionInfo().clear();
        return itemStacks;
    }

    private void onPairHookRegistrationRemoved(ZiplineHookTileEntity removedPair) {
        getConnectionPoints().remove(removedPair.getPos());
        connectionEntities.remove(removedPair.getPos());
    }

    private void onPairHookUnloaded(ZiplineHookTileEntity removedPair) {
        connectionEntities.remove(removedPair.getPos());
    }

    /** 区块卸载：把实体清掉（对端也通知一声 ✓），但**保留连接记录** ✓（重新加载后会重建 ✓） */
    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (world != null) {
            getConnectionPoints().stream()
                    .filter(world::isBlockLoaded)
                    .map(world::getTileEntity)
                    .map(it -> it instanceof ZiplineHookTileEntity ? (ZiplineHookTileEntity) it : null)
                    .filter(Objects::nonNull)
                    .forEach(it -> it.onPairHookUnloaded(this));
            if (!world.isRemote) {
                connectionEntities.values().forEach(ZiplineRopeEntity::setDead);
            }
            connectionEntities.clear();
        }
    }

    /** 滑索在世界空间里的实际挂点 ✓ —— 交给方块算（铁钩会偏离中心 ✓） */
    public Vec3d getActualZiplinePoint(@Nullable BlockPos connected) {
        if (world == null) {
            return new Vec3d(getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5);
        }
        IBlockState state = world.getBlockState(this.getPos());
        if (state.getBlock() instanceof ZiplineHookBlock) {
            return ((ZiplineHookBlock) state.getBlock()).getActualZiplinePoint(this.getPos(), state);
        }
        return new Vec3d(getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5);
    }

    /**
     * 连到另一张钩子 ✓ —— **只在服务端真正建立** ✓（会生成绳索实体 ✓）。
     * 两端各自记一份 ✓。
     */
    public boolean connectTo(ZiplineHookTileEntity target, ZiplineInfo info) {
        if (this == target) return false;

        if (world != null && !world.isRemote) {
            if (this.getConnectionPoints().stream().anyMatch(target.getPos()::equals)) {
                return false;
            }
            ZiplineRopeEntity ropeEntity = spawnRope(world, target, info);
            if (ropeEntity != null) {
                this.getConnectionInfo().put(target.getPos(), info);
                target.getConnectionInfo().put(this.getPos(), info);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private ZiplineRopeEntity spawnRope(net.minecraft.world.World level, ZiplineHookTileEntity target, ZiplineInfo info) {
        if (level.isRemote) return null;
        if (info == null) return null;
        if (target.connectionEntities.containsKey(this.getPos())) return null;

        ZiplineRopeEntity entity = new ZiplineRopeEntity(level, getPos(), target.getPos(), info);
        boolean result = level.spawnEntity(entity);
        if (result) {
            this.connectionEntities.put(target.getPos(), entity);
            target.connectionEntities.put(this.getPos(), entity);
        }
        return result ? entity : null;
    }

    // ── NBT ──────────────────────────────────────────────────────────────

    private void saveTo(NBTTagCompound nbt) {
        NBTTagList connections = new NBTTagList();
        for (Map.Entry<BlockPos, ZiplineInfo> infoEntry : getConnectionInfo().entrySet()) {
            NBTTagCompound entryTag = new NBTTagCompound();
            BlockPos pos = getPos();
            // ★ 存**相对坐标** ✓ —— 这样整个结构被推动/复制时连接不会错位 ✓
            entryTag.setInteger("rX", infoEntry.getKey().getX() - pos.getX());
            entryTag.setInteger("rY", infoEntry.getKey().getY() - pos.getY());
            entryTag.setInteger("rZ", infoEntry.getKey().getZ() - pos.getZ());
            entryTag.setTag("Info", infoEntry.getValue().save());
            connections.appendTag(entryTag);
        }
        nbt.setTag("Connection", connections);
    }

    private void restoreFrom(NBTTagCompound nbt) {
        NBTBase connections = nbt.getTag("Connection");
        if (!(connections instanceof NBTTagList)) {
            return;
        }
        NBTTagList listConnections = (NBTTagList) connections;
        getConnectionInfo().clear();

        for (int i = 0; i < listConnections.tagCount(); i++) {
            NBTBase entry = listConnections.get(i);
            if (!(entry instanceof NBTTagCompound)) continue;

            NBTTagCompound cTag = (NBTTagCompound) entry;
            BlockPos pos;
            if (cTag.hasKey("rX") && cTag.hasKey("rY") && cTag.hasKey("rZ")) {
                // 新格式：相对坐标 ✓
                pos = getPos().add(cTag.getInteger("rX"), cTag.getInteger("rY"), cTag.getInteger("rZ"));
            } else if (cTag.hasKey("X") && cTag.hasKey("Y") && cTag.hasKey("Z")) {
                // 旧格式：绝对坐标 ✓ —— 兼容保留 ✓
                pos = new BlockPos(cTag.getInteger("X"), cTag.getInteger("Y"), cTag.getInteger("Z"));
            } else {
                continue;
            }
            ZiplineInfo info = ZiplineInfo.load(cTag.getTag("Info"));
            getConnectionInfo().put(pos, info);
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        super.writeToNBT(compound);
        saveTo(compound);
        return compound;
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound) {
        super.readFromNBT(compound);
        restoreFrom(compound);
    }

    // ── 网络同步（让客户端也知道连接，才能渲染绳索 ✓）──────────────────────

    @Nonnull
    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound nbt = super.getUpdateTag();
        saveTo(nbt);
        return nbt;
    }

    /**
     * ⚠️ 客户端收到后要**重新解析连接表** ✗→✓ —— 否则客户端不知道绳索连到哪 ✓
     * （1.16 的 {@code handleUpdateTag} 对应这里 ✓）
     */
    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        super.onDataPacket(net, pkt);
        NBTTagCompound tag = pkt.getNbtCompound();
        if (tag != null) restoreFrom(tag);
    }

    @Nullable
    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    // ── 每 tick：保证「连接数」与「实体数」一致（自愈 ✓）──────────────────

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        // 实体被别处删除（例如 /kill ✓）→ 忘掉它 ✓
        // 1.12.2 是 isEntityAlive（1.16 叫 isAlive ✗）
        connectionEntities.values().removeIf(it -> !it.isEntityAlive());

        if (connectionEntities.size() < getConnectionPoints().size()) {
            List<ZiplineHookTileEntity> tileEntities = getConnectionPoints()
                    .stream()
                    .filter(it -> !connectionEntities.containsKey(it))
                    .filter(world::isBlockLoaded)
                    .map(world::getTileEntity)
                    .map(it -> it instanceof ZiplineHookTileEntity ? (ZiplineHookTileEntity) it : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            tileEntities.forEach(it -> {
                if (it.getConnectionPoints().contains(this.getPos())) {
                    // 对端也承认 → 重建缺口 ✓
                    this.spawnRope(world, it, getConnectionInfo().get(it.getPos()));
                } else {
                    // 对端不承认 → 我这边也清掉 ✓
                    this.getConnectionPoints().remove(it.getPos());
                }
            });
        }
    }
}
