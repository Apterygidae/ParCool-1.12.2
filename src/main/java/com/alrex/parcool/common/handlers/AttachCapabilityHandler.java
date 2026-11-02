package com.alrex.parcool.common.handlers;

import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.capability.capabilities.Capabilities;
import com.alrex.parcool.common.capability.provider.ParkourabilityProvider;
import com.alrex.parcool.common.capability.provider.StaminaProvider;
import com.alrex.parcool.common.network.SyncServerInfoMessage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 把 Capability 挂到玩家实体上（1.12.2 版）+ 重生/换维度时复制状态。
 *
 * <p>基本可直接照搬上游：{@code AttachCapabilitiesEvent<Entity>} 在 1.12.2 <b>同名同形</b>，
 * {@code addCapability(ResourceLocation, ICapabilityProvider)} 也一样。
 * 只有两处要注意：</p>
 * <ul>
 *   <li>上游用 {@code event.getObject().level.isClientSide} 判断客户端（用于挂 ANIMATION），
 *       1.12.2 是 {@code .world.isRemote}。<b>但这里不这么做</b> —— 见下。</li>
 *   <li>客户端专属的 {@code Animation}（P5）不走本类，而走 {@code ClientProxy}。
 *       理由与 {@code EventBusForgeRegistry} 一致：公共类里出现客户端类引用，
 *       就要依赖 FML 运行期剥离才安全；走 {@code @SidedProxy} 是硬保证。</li>
 * </ul>
 */
public class AttachCapabilityHandler {

    private AttachCapabilityHandler() {
    }

    @SubscribeEvent
    public static void onAttachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getObject();

        event.addCapability(Capabilities.PARKOURABILITY_LOCATION, new ParkourabilityProvider());
        event.addCapability(Capabilities.STAMINA_LOCATION, new StaminaProvider(player));
        // TODO(P5): 客户端专属的 ANIMATION 走 ClientProxy，不放这里（见类注释）
    }

    /**
     * 玩家<b>死亡重生 / 换维度</b>时，把旧实例的两侧同步信息复制过去，
     * <b>并重新向客户端下发一次限制</b> ✓
     *
     * <h3>🚨🚨 这个处理器此前<b>完全缺失</b> ✗ —— 是实测 bug 的病根</h3>
     * <p>症状：<b>死亡（含掉出世界/虚空）后所有跑酷功能失效</b> ✗，
     * 聊天框提示 {@code parcool.message.error.limitation.not_synced} ✗</p>
     * <p><b>机制</b>：重生/换维度时实体被重建 ✗ → capability 是<b>全新的</b> ✗
     * → {@code ActionInfo.serverLimitation} 回到 {@code UNSYNCED_INSTANCE} ✗
     * → 所有动作的 {@code can()} 全部失败 ✗</p>
     * <p><b>关键</b>：{@code Parkourability.CopyFrom} <b>早就写好了</b> ✓
     * （注释还写着「上游同一套做法」✓）—— 但<b>全工程没有一个调用者</b> ✗✗✗</p>
     *
     * <h3>🚨🚨🚨 光有 {@code CopyFrom} 还不够 —— {@code Clone} 是<b>纯服务端</b>事件 ✗</h3>
     * <p>已从反编译源码核实：{@code PlayerEvent.Clone} 只从
     * {@code EntityPlayerMP.java:1279}（→ {@code ForgeEventFactory.onPlayerClone}）发出 ✗
     * —— <b>客户端的 {@code EntityPlayerSP} 在 {@code NetHandlerPlayClient} 里重建时【不发这个事件】</b> ✗</p>
     * <p>所以只做 {@code CopyFrom} 的话：</p>
     * <ul>
     *   <li><b>服务端</b>：限制拷过来了 ✓ 动作允许执行 ✓</li>
     *   <li><b>客户端</b>：能力仍是全新的 ✗ → {@code limitationIsNotSynced()} 为真 ✗
     *       → 所有动作的客户端 `canStart` 被拦 ✗
     *       → 只能等 256 tick 后的周期性自愈 ✗</li>
     * </ul>
     * <p><b>实测日志（2026-10-04 19:54）</b>：虚空死亡后
     * <b>客户端有整整 15 秒一次都没收到新的限制</b> ✗（19:53:58 死 → 19:54:13 才自愈 ✓）</p>
     *
     * <h3>✅ 修法：重生后主动下发一次（与 {@code PlayerJoinHandler} 的 join 路径同理 ✓）</h3>
     * <p>{@code SyncServerInfoMessage.syncWithStamina(player, stamina)} 会把限制 + 体力一起推给客户端 ✓
     * —— 这正是 join 时用的那条路径 ✓</p>
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Parkourability original = Parkourability.get(event.getOriginal());
        Parkourability newOne = Parkourability.get(event.getEntityPlayer());
        if (original == null || newOne == null) return;

        // ① 服务端侧：把同步信息拷到新实例 ✓（死亡与换维度都算 ✓）
        newOne.CopyFrom(original);

        // ② 客户端侧：**必须主动重新下发** ✗→✓（见上方长注释）
        //    只有服务端玩家才发得出包 ✓ —— 这也说明本事件确实是服务端专属 ✓
        if (event.getEntityPlayer() instanceof EntityPlayerMP) {
            EntityPlayerMP serverPlayer = (EntityPlayerMP) event.getEntityPlayer();
            IStamina stamina = IStamina.get(serverPlayer);
            if (stamina != null) {
                SyncServerInfoMessage.syncWithStamina(serverPlayer, stamina);
            } else {
                SyncServerInfoMessage.sync(serverPlayer);
            }
        }
    }
}
