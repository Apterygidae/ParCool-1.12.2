package com.alrex.parcool.client.gui;

import com.alrex.parcool.client.config.ConfigSyncHandler;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.util.List;

/**
 * 把 Forge 原生 {@link GuiConfig} 包一层，**补上它不做的「落盘」**。
 *
 * <h3>🔴 为什么必须包这一层（2026-10-12 用户报的「重启后配置回退」）</h3>
 * <p>Forge 的原生配置界面<b>不会写文件</b> ✗。反编译源码里按「完成」的全部动作是
 * （{@code GuiConfig:326-335}）：</p>
 * <pre>
 *   if (... && this.entryList.hasChangedEntry(true)) {
 *       this.entryList.saveConfigElements();          // ← 只是把 GUI 的值写进内存的 Property
 *       MinecraftForge.EVENT_BUS.post(new OnConfigChangedEvent(...));
 *   }
 * </pre>
 * <p>而「谁来 {@code Configuration.save()}」在 1.12.2 里<b>是要 mod 自己做的</b> ✓ ——
 * {@code ConfigManager} <b>自己不订阅</b> {@code OnConfigChangedEvent} ✗，
 * 它唯一的 {@code save()} 在 {@code ConfigManager:184}、只服务于 {@code @Config} 注解类；
 * 本工程用的是手动 {@code Configuration + Property}、<b>一个 {@code @Config} 类都没有</b> ✗
 * → 于是「改了 → 会话内生效 → 重启后回退」✓（每次启动 {@code registerAll()} 末尾还会
 * {@code save()} 一次，把文件里的旧值原样写回 ✓）。</p>
 *
 * <h3>★ 为什么钩在 {@code onGuiClosed} 而不是只靠事件</h3>
 * <ul>
 *   <li><b>子分类界面根本不发事件</b> ✗：{@code GuiConfigEntries:1350} 构造子界面时走的是
 *       <b>不带 {@code configID}</b> 的那个构造 → 子界面按「完成」时
 *       {@code (configID != null || parentScreen == null || !(parentScreen instanceof GuiConfig))}
 *       整条为 false → <b>不保存也不 post</b> ✓。子界面的值靠<b>根界面</b>的
 *       {@code saveConfigElements()} 递归下去才写进 Property ✓ —— 而本工程所有配置项都在子分类里 ✓，
 *       所以「只在事件里保存」这条链是断的 ✗。</li>
 *   <li><b>按 Esc 也不发事件</b> ✗：{@code onGuiClosed} 里 Forge 只是
 *       {@code entry.onGuiClosed()}，而 {@code FieldEntry.onGuiClosed()} 是<b>空实现</b> ✓
 *       → 内存里的值不会回滚、文件也不会更新 → 留下「会话内变了、文件没变」的半吊子状态 ✗。</li>
 *   <li>根界面无论按「完成」还是 Esc <b>都必然经过 {@code onGuiClosed}</b> ✓
 *       → 钩在这里<b>一次覆盖全部退出路径</b> ✓。</li>
 * </ul>
 *
 * <h3>★ 为什么要自己调 {@code saveConfigElements()}</h3>
 * <p>GUI 里的新值只存在 <b>entry 的 {@code currentValue}</b> 字段里 ✗ ——
 * 是「完成」按钮调 {@code saveConfigElements()} 才把它 {@code set()} 进 {@code Property} ✓。
 * Esc 路径没这一步 → 必须自己补 ✓，否则 {@code Configuration.save()} 写出去的还是旧值 ✗。</p>
 *
 * <p>⚠️ 只在<b>根界面</b>需要（{@code ParCoolGuiFactory} 只对根界面用本类 ✓）——
 * 子界面不是本类，但它们的值会被根界面的递归保存带出去 ✓。</p>
 */
public class ParCoolConfigGui extends GuiConfig {

    public ParCoolConfigGui(GuiScreen parentScreen, List<IConfigElement> configElements,
                            String modID, String configID,
                            boolean allRequireWorldRestart, boolean allRequireMcRestart, String title) {
        super(parentScreen, configElements, modID, configID,
                allRequireWorldRestart, allRequireMcRestart, title);
    }

    @Override
    public void onGuiClosed() {
        // ★ 无条件消费「已由事件处理」标记（不能放进下面的 if ✗ —— 否则会残留到下次会话）
        boolean handledByEvent = ConfigSyncHandler.consumeHandledByEvent();

        // ⚠️ 必须在 super 之前算 —— super 会去动父界面的 needsRefresh（与本判断无关，但顺序更明确）
        boolean changed = entryList != null && entryList.hasChangedEntry(true);

        super.onGuiClosed();

        if (!changed || handledByEvent) return;

        // Esc 路径（含「在子分类里改完、退回根界面再 Esc」✓）：
        // ① 先把 GUI 的值写进 Property（= 「完成」按钮做的那一步 ✓）
        entryList.saveConfigElements();
        // ② 落盘 + 热同步 ✓
        ConfigSyncHandler.applyAndSync();
    }
}
