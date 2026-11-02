package com.alrex.parcool.common.entity;

import com.alrex.parcool.ParCool;
import net.minecraft.util.ResourceLocation;

/**
 * 实体的登记参数 ✓ —— 1.12.2 版。
 *
 * <h3>★★ 1.12.2 没有 {@code EntityType<T>} 注册表 ✗</h3>
 * <p>1.16 的实体要注册 {@code EntityType<T>} 对象 ✓ 并作为构造参数传进实体 ✗。</p>
 * <p><b>1.12.2 用一条「mod 实体登记」调用搞定</b> ✗→✓：</p>
 * <pre>
 * EntityRegistry.registerModEntity(
 *     ResourceLocation name, Class&lt;? extends Entity&gt; entityClass,
 *     String entityName, int id, Object mod,
 *     int trackingRange, int updateFrequency, boolean sendsVelocityUpdates);
 * </pre>
 * <p>它<b>同时</b>负责：</p>
 * <ul>
 *   <li>把类登记进全局实体表 ✓</li>
 *   <li>生成／同步用的实体 id ✓（{@code id} 参数 ✓）</li>
 *   <li>追踪范围与更新频率 ✓（对应上游 {@code clientTrackingRange} / {@code updateInterval} ✓）</li>
 *   <li><b>以及生成 {@code SPacketSpawnObject} 等网络包</b> ✓ ——
 *       所以 1.12.2 的实体<b>不需要</b>写 {@code getAddEntityPacket} ✗</li>
 * </ul>
 *
 * <h3>★ 上游参数的对应</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code EntityClassification.MISC}</td><td>无此参数 ✓（1.12.2 无分类 ✗）</td></tr>
 *   <tr><td>{@code .noSave()}</td><td>由实体的 {@code writeEntityToNBT} 决定存什么 ✓</td></tr>
 *   <tr><td>{@code .clientTrackingRange(115/1.9 ≈ 60)}</td>
 *       <td>{@code trackingRange} ✓ —— <b>单位是「区块」不是「方块」</b> ✗
 *           → 60 方块 ≈ <b>4 区块</b> ✓</td></tr>
 *   <tr><td>{@code .updateInterval(Integer.MAX_VALUE)}</td>
 *       <td>{@code updateFrequency} ✓ —— 3 = 每 3 tick 一次 ✓；
 *           索不需要频繁更新 → 用大值 ✓</td></tr>
 *   <tr><td>{@code .noSummon()}</td><td>无直接对应 ✗（不提供刷怪蛋即可 ✓）</td></tr>
 *   <tr><td>{@code .sized(0.1f, 0.1f)}</td>
 *       <td>由实体的 {@code setSize(0.1f, 0.1f)} 设 ✓（已就位 ✓）</td></tr>
 * </table>
 */
public class EntityType {

    /** 实体登记名 ✓ */
    public static final ResourceLocation ZIPLINE_ROPE_NAME =
            new ResourceLocation(ParCool.MOD_ID, "zipline_rope");

    /** 追踪范围 ✓ —— <b>单位是「方块」✗，不是区块</b> ✓。
     *
     * <h3>⚠️⚠️ 我在这个单位上错了两次 ✗（2026-10-11 ✓）</h3>
     * <ol>
     *   <li>第一版设 4 ✗ → 只有 **4 方块**（当时以为是 4 区块 = 64 方块 ✗）</li>
     *   <li>第二版"修正"成 8 ✗ → 只有 **8 方块** ✓
     *       —— 用户实测**玩家离开约 10 格就消失** ✓，数字正好对上 ✓</li>
     * </ol>
     *
     * <h3>★ 源码依据（不要再猜 ✗）</h3>
     * <pre>
     * // EntityTrackerEntry.java:497 —— 追踪距离就是 min(range, maxRange)，无任何 ×16
     * int i = Math.min(this.range, this.maxRange);
     *
     * // EntityTrackerEntry 构造：rangeIn 直接存进 this.range ✓
     * public EntityTrackerEntry(Entity e, int rangeIn, int maxRangeIn, ...)
     * { this.range = rangeIn; ... }
     * </pre>
     * <p>而 `registerModEntity(..., trackingRange, ...)` 的 `trackingRange` 就是 `rangeIn` ✓ ——
     * <b>没有乘 16 的步骤</b> ✗。</p>
     *
     * <h3>★ 该设多少</h3>
     * <p>滑索最大水平跨度 {@code MAXIMUM_HORIZONTAL_DISTANCE} = **115 方块** ✓，
     * 实体位于索的**中点** ✓ → 两端距它约 58 方块 ✓。
     * 这里取 **160** ✓ —— 覆盖半跨度有余 ✓，也和上游的慷慨程度相当 ✓
     * （上游 1.16 用 {@code MAXIMUM_HORIZONTAL_DISTANCE / 1.9 ≈ 60} 个**区块** ✗ = 960 方块 ✓）。</p>
     */
    public static final int ZIPLINE_ROPE_TRACKING_RANGE = 160;

    /**
     * 更新频率 ✓ —— 1.12.2 的语义是「每 N tick 同步一次」✓。
     *
     * <p>绳索是静态物体（位置由两端钩子决定 ✓），不需要频繁同步 ✓ ——
     * 上游给了超大值 ✓，这里取 20（1 秒 ✓）既省带宽又能及时反映变化 ✓。</p>
     */
    public static final int ZIPLINE_ROPE_UPDATE_FREQUENCY = 20;

    /**
     * 登记用的数字 id ✓。
     *
     * <p><b>⚠️ 1.12.2 要求同一 mod 内唯一</b> ✗，但<b>不要求全局唯一</b> ✓
     * （内部会加 mod 前缀 ✓）。取 1 即可 ✓（本 mod 目前只有这一个实体 ✓）。</p>
     */
    public static final int ZIPLINE_ROPE_ENTITY_ID = 1;

    private EntityType() {
    }
}
