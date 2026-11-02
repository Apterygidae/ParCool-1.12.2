package com.alrex.parcool.client.gui;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.config.Forge14ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ParCool 的**配置界面工厂** ✓ —— 用 Forge 原生界面，不自己画 ✗。
 *
 * <p>挂载方式：{@code @Mod(guiFactory = "com.alrex.parcool.client.gui.ParCoolGuiFactory")} ✓
 * —— 之后在**模组列表里点 ParCool 的「Config」按钮**就会打开它 ✓。</p>
 *
 * <h3>★ 为什么不用 {@code GuiConfig(parent, modid, title)} 那个「省事构造」✗</h3>
 * <p>1.12.2 有**两套**并存的配置体系 ✗：</p>
 * <ul>
 *   <li><b>{@code @Config} 注解 + {@code ConfigManager}</b>（1.12.2 新增 ✓）——
 *       省事构造内部走的是 {@code ConfigManager.getModConfigClasses(modid)} ✓</li>
 *   <li><b>手动 {@code Configuration} + {@code Property}</b>（老式，本工程用的 ✓）</li>
 * </ul>
 * <p>本工程的配置**不带 {@code @Config} 注解** ✗ →
 * 省事构造拿不到任何配置类 ✗ → **界面一片空白** ✗。</p>
 * <p>✅ 正解：自己拿 {@link Configuration} 遍历分类 ✓，
 * 每类包一个 {@link ConfigElement} ✓，再交给带 {@code List<IConfigElement>} 的构造 ✓。</p>
 *
 * <h3>★ configID 的作用（别传 null ✗）</h3>
 * <p>传**非 null** 的 {@code configID} ✓ → 按「完成」且确有改动时，
 * Forge 会 post {@code ConfigChangedEvent.OnConfigChangedEvent} ✓。
 * 本工程的**配置热同步**就挂在那上面 ✓（见 {@code client/config/ConfigSyncHandler} ✓）。</p>
 *
 * <p>⚠️ <b>不加 {@code @SideOnly(Side.CLIENT)}</b> ✗ —— 本类是**被 Forge 按类名反射加载**的 ✓
 * （{@code FMLClientHandler.getGuiFactoryFor()} ✓，只在客户端调用 ✓）。
 * 加了反而有"被 SideTransformer 剥离后反射失败"的理论风险 ✗。
 * 实测确认：{@code FMLModContainer} 只把注解值当**字符串**存着 ✓，双端都不会提前加载这个类 ✓。</p>
 */
public class ParCoolGuiFactory implements IModGuiFactory {

    /** 打开配置界面时的标题的 lang 键 ✓。 */
    private static final String TITLE_KEY = "parcool.config.title";

    /** 标题的兜底（lang 里没这个键时用）✓。 */
    private static final String TITLE_FALLBACK = "ParCool Settings";

    /**
     * 分类名的 lang 键前缀 ✓ —— **必须带命名空间** ✗。
     *
     * <p>为什么：{@code ConfigCategory.getLanguagekey()} 的兜底就是
     * {@code getQualifiedName()}（即 {@code client.control} 这种裸键 ✗）→
     * {@code I18n.format("client")} 会去查一个**全局**的 {@code client} 键 ✗ ——
     * 既容易和别的 mod 撞车 ✗，读起来也不像本 mod 的东西 ✓。</p>
     *
     * <p>⚠️ {@code setLanguageKey} 只改 {@code languagekey} 字段 ✓，
     * <b>不影响写进 cfg 的分类名</b> ✓（{@code Configuration.save()} 用的是
     * {@code getQualifiedName()} ✓）。</p>
     */
    private static final String CATEGORY_LANG_PREFIX = "parcool.config.cat.";

    /** configID ✓ —— 同时充当 {@code OnConfigChangedEvent} 的标识 ✓。 */
    private static final String CONFIG_ID = ParCool.MOD_ID;

    @Override
    public void initialize(Minecraft minecraftInstance) {
        // 无需初始化 ✓
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        // 本 mod 没有「运行时选项」（那种需要暂停菜单入口的配置 ✓）→ 返回 null ✓
        return null;
    }

    /**
     * 构造配置界面 ✓ —— Forge 在模组列表点「Config」时调用 ✓。
     *
     * @param parentScreen 调用方（模组列表）✓ —— 按「完成」会返回它 ✓
     */
    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        List<IConfigElement> elements = new ArrayList<>();

        Configuration configuration = Forge14ConfigManager.getConfiguration();
        if (configuration != null) {
            // ★ 第一步：给【每个分类】（含子分类 ✓）挂上带命名空间的 lang 键 ✓
            //   见 CATEGORY_LANG_PREFIX 的注释：不改这一步的话，
            //   界面上的分类名就是 `client.control` 这样的裸键 ✓（而且 I18n 查的是全局键 ✗）
            for (String categoryName : configuration.getCategoryNames()) {
                configuration.getCategory(categoryName)
                        .setLanguageKey(CATEGORY_LANG_PREFIX + categoryName);
            }

            // ⚠️★ 只加【顶层】分类 ✗★ —— 这是「第一层只显示 客户端/服务端」的关键 ✓
            //
            //   `getCategoryNames()` 返回的是**所有**分类 ✓ —— 既有顶层（`client` / `server` ✓）
            //   也有子分类（`client.control` / `server.stamina` … ✓）。
            //   如果全都塞进 elements ✗ → **两级会被平铺成一层** ✗
            //   （实测症状：`server` 跑到 `client` 的下面 ✗，且子分类与顶层混在一起 ✓）
            //
            //   ✅ 判据：**含 `.` 的就是子分类** ✓（Forge 用 `.` 表示嵌套 ✓）
            //      → 跳过它们 ✓，由 `ConfigElement(顶层分类)` 自己去展开子分类 ✓
            for (String categoryName : configuration.getCategoryNames()) {
                if (categoryName.contains(".")) continue;
                elements.add(new ConfigElement(configuration.getCategory(categoryName)));
            }
        }

        return new ParCoolConfigGui(
                parentScreen,
                elements,
                ParCool.MOD_ID,
                CONFIG_ID,      // ★ 非 null → 按「完成」时会 post OnConfigChangedEvent ✓
                false,          // allRequireWorldRestart
                false,          // allRequireMcRestart
                title()
        );
    }

    /**
     * 标题 ✓ —— {@code GuiConfig} 是**直接画**传进去的字符串 ✗（不做 I18n ✓），
     * 所以这里自己翻译 ✓。缺键时回落到英文 ✓（{@code I18n.format} 会原样返回 key ✗）。
     */
    private static String title() {
        String translated = net.minecraft.client.resources.I18n.format(TITLE_KEY);
        return TITLE_KEY.equals(translated) ? TITLE_FALLBACK : translated;
    }
}
