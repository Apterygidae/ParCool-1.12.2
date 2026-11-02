package com.alrex.parcool.client.config;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.info.ClientSetting;
import com.alrex.parcool.common.network.SyncClientInformationMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 配置界面**改完之后**要做两件事：<b>① 落盘</b>、<b>② 热同步</b>。
 *
 * <h3>🔴 ① 落盘 —— 这是 2026-10-12 用户报的「重启后配置回退」的根因</h3>
 * <p>Forge 的原生配置界面<b>只改内存、不写文件</b> ✗。按「完成」时它做的是
 * （{@code GuiConfig:326-335}）：{@code entryList.saveConfigElements()} → post
 * {@code OnConfigChangedEvent}，<b>仅此而已</b> ✓。</p>
 * <p>「谁来 {@code Configuration.save()}」在 1.12.2 里<b>是 mod 自己的事</b> ✓：</p>
 * <ul>
 *   <li>{@code ConfigManager} <b>自己不订阅</b> {@code OnConfigChangedEvent} ✗
 *       （{@code ForgeModContainer} 订阅，但那是给 Forge 自己的配置用的 ✓）</li>
 *   <li>{@code ConfigManager.sync(...)} 全程只处理 {@code @Config} 注解类 ✓ ——
 *       而本工程用手动 {@code Configuration + Property}、<b>一个 {@code @Config} 类都没有</b> ✗
 *       （{@code ConfigManager.sync} 里 {@code map == null} 时<b>直接 return</b> ✓）</li>
 * </ul>
 * <p>→ 结果是「改了 → 会话内生效 → <b>重启后回退</b>」✓，而且每次启动
 * {@code Forge14ConfigManager.registerAll()} 末尾还会 {@code save()} 一次，
 * 把文件里的旧值原样写回 ✓（所以实例配置的 mtime 每次启动都会更新、内容却一直没变 ✓）。</p>
 *
 * <h3>② 热同步 —— Forge 管不了的那一步</h3>
 * <p>跑酷功能是否可用**由服务端裁定** ✓ —— 客户端改了「哪些动作启用」之后，
 * 必须把新的 {@code ClientSetting} **发给服务端** ✓，否则服务端还按旧的许可在判定 ✓
 * （表现为"改了没反应" ✗）。这是上游 {@code SettingXxxScreen.save()} 做过的同一件事 ✓。</p>
 *
 * <h3>★ 两个触发点（缺一不可）</h3>
 * <ol>
 *   <li><b>本类的 {@code OnConfigChangedEvent} 订阅</b> —— Forge 在<b>根界面</b>按「完成」时 post ✓
 *       （标准 1.12.2 写法 ✓）。</li>
 *   <li><b>{@code ParCoolConfigGui.onGuiClosed()}</b> —— 覆盖事件覆盖不到的路径 ✗：
 *       子分类界面按「完成」根本不 post 事件（它们构造时没有 {@code configID} ✓）、
 *       按 Esc 也不 post ✓。见该类的类注释。</li>
 * </ol>
 * <p>为避免「完成」路径被做两遍 ✓，用 {@link #consumeHandledByEvent()} 去重
 * （事件先于 {@code onGuiClosed} 发生 ✓，顺序是确定的 ✓）。</p>
 *
 * <p>⚠️ 只在客户端注册 ✓（{@code ClientProxy.preInit} ✓）；事件本身也只在客户端存在 ✓。</p>
 */
@SideOnly(Side.CLIENT)
public class ConfigSyncHandler {

    /**
     * 「本次 GUI 会话已经由事件处理过了」——由事件处理器置位、由
     * {@code ParCoolConfigGui.onGuiClosed()} 无条件消费 ✓。
     *
     * <p>⚠️ 只在客户端主线程读写 ✓，无需同步 ✓。</p>
     */
    private static boolean handledByEvent = false;

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        // 事件是全 mod 共享的 ✓ —— 必须按 modID 过滤 ✗
        if (!ParCool.MOD_ID.equals(event.getModID())) return;

        handledByEvent = true;
        applyAndSync();
    }

    /**
     * 消费「已由事件处理」标记 ✓。
     *
     * <p>⚠️ 调用方必须**无条件**消费（不要放在 `if` 里面 ✗）——
     * 否则标记会残留到下一次 GUI 会话，导致那次的 Esc 路径被误跳过 ✗。</p>
     */
    public static boolean consumeHandledByEvent() {
        boolean handled = handledByEvent;
        handledByEvent = false;
        return handled;
    }

    /**
     * <b>落盘 + 热同步</b>。
     *
     * <p>幂等性：重复调用只是多写一次文件、多发一个包 ✓（值相同 ✓），语义无害 ✓；
     * 但正常路径下两个触发点已经用标记去重过了 ✓。</p>
     */
    public static void applyAndSync() {
        // ① ★ 落盘 —— Forge 原生界面不会做这一步（见类注释）
        //    必须在这里（而不是只在事件里）做：Esc 路径、子分类路径都要靠它 ✓
        ParCool.saveConfig();

        // ② 热同步 —— 需要玩家在场
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        // 在主菜单改配置时没有玩家 ✓ → 跳过（下次进世界时 join 流程会重新同步 ✓）
        if (player == null) return;

        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;

        // 按刚保存的配置重建 ClientSetting ✓，并发给服务端 ✓
        //（第二参数 = 顺便请求服务端把限制下发给本客户端 ✓）
        parkourability.getActionInfo().setClientSetting(ClientSetting.readFromLocalConfig());
        SyncClientInformationMessage.sync(player, true);

        ParCool.LOGGER.info("[ParCool] Config changed -> saved + client setting re-synced");
    }
}
