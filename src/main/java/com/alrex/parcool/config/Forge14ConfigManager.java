package com.alrex.parcool.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;

/**
 * 1.12.2 配置后端。
 *
 * <p>对应 1.16.5 里由 Forge 自动托管的部分（{@code ModLoadingContext.registerConfig} +
 * {@code ForgeConfigSpec}）。1.12.2 这些都不存在，必须手工。</p>
 *
 * <h3>⚠️ 为什么是「单文件」而不是「客户端 / 服务端双文件」</h3>
 *
 * <p>上游 1.16.5 有两份配置：{@code ModConfig.Type.CLIENT} → {@code config/parcool-client.toml}、
 * {@code ModConfig.Type.SERVER} → <b>{@code serverconfig/parcool-server.toml}</b>。
 * <b>1.12.2 没有这套东西</b>：</p>
 * <ul>
 *   <li>没有 {@code ModConfig.Type}，也没有 {@code serverconfig/} 目录约定（1.13+ 才引入）</li>
 *   <li>没有「服务端配置自动同步给客户端」的机制</li>
 *   <li>{@code FMLPreInitializationEvent.getSuggestedConfigurationFile()} 给的路径就是
 *       {@code config/<modid>.cfg} —— 见反编译源码 {@code FMLPreInitializationEvent.java:81}：
 *       {@code new File(configurationDir, activeContainer.getModId() + ".cfg")}</li>
 * </ul>
 *
 * <p>因此这里采用 1.12.2 的惯例：<b>一个文件 {@code config/parcool.cfg}</b>，
 * 用分类名前缀区分两侧（{@code [Client.Control]} / {@code [Server.Control]}），
 * 服务器管理员打开文件就能一眼分出「哪些是本地客户端开关、哪些是服务端策略」。</p>
 *
 * <p><b>Java 侧的 {@code Client}/{@code Server} 拆分必须保留</b> ——
 * 它服务于 ParCool 自己的限制同步协议（{@code ServerLimitation} 由服务端下发、
 * {@code ClientSetting} 由客户端上报），与配置文件怎么存无关。</p>
 *
 * <h3>单机与专用服务端的行为</h3>
 * <ul>
 *   <li><b>单机</b>：集成服务端与客户端同进程、同文件，两侧读到的都是同一个文件 ✓</li>
 *   <li><b>专用服务端</b>：服务端只用 {@code [Server.*]} 段（{@code [Client.*]} 段被忽略）；
 *       客户端读自己的 {@code [Client.*]} 段，服务端限制由网络下发。互不干扰 ✓</li>
 * </ul>
 */
public final class Forge14ConfigManager {

    private static final Logger LOGGER = LogManager.getLogger("ParCool");

    /** 分类前缀：把两侧的 {@code ConfigGroup} 分到不同的文件段落里。 */
    public static final String CATEGORY_PREFIX_CLIENT = "Client.";
    public static final String CATEGORY_PREFIX_SERVER = "Server.";

    @Nullable
    private static Configuration configuration;

    @Nullable
    private static File configFile;

    private static boolean registered = false;

    private Forge14ConfigManager() {
    }

    /**
     * 供 Forge 的**配置界面**取用 ✓（见 {@code client/gui/ParCoolGuiFactory} ✓）。
     *
     * <p>Forge 原生界面需要一个 {@link Configuration} 才能在屏幕上展开分类 ✓ ——
     * 而 1.12.2 的 {@code GuiConfig(parent, modid, title)} 那种「省事构造」走的是
     * <b>{@code @Config} 注解体系</b> ✗，和本工程的手动 {@code Configuration + Property} 不是一套 ✗，
     * 用它只会得到一张**空白界面** ✗。</p>
     *
     * <p>→ 正确做法是自己拿 {@code Configuration} 遍历分类 ✓，
     * 每类包一个 {@code ConfigElement} ✓，再传给带 {@code List<IConfigElement>} 的构造 ✓。</p>
     *
     * @return 配置实例；{@link #load} 之前为 {@code null} ✓
     */
    @Nullable
    public static Configuration getConfiguration() {
        return configuration;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 生命周期
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 载入配置。必须在 {@code FMLPreInitializationEvent} 里调用，
     * 参数直接用 {@code event.getSuggestedConfigurationFile()}（即 {@code config/parcool.cfg}）。
     */
    public static void load(File suggestedConfigFile) {
        // ★ 把配置收进 mod 自己的子目录 ✓
        //   config/parcool.cfg  →  config/parcool/parcool.cfg
        //   （Kiwi 要求：别和其它几十个 mod 的 cfg 混在 config/ 根下 ✓）
        File dir = new File(suggestedConfigFile.getParentFile(), "parcool");
        File target = new File(dir, suggestedConfigFile.getName());

        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("[ParCool] Could not create config directory {}", dir.getAbsolutePath());
        }

        // ★ 一次性迁移旧位置的文件 ✓ —— 只在「新位置还没有、旧位置有」时做 ✓
        //   用 renameTo ✓（同盘瞬时 ✓）→ 用户已调好的设置**不会丢** ✓
        if (!target.exists() && suggestedConfigFile.exists()) {
            if (suggestedConfigFile.renameTo(target)) {
                LOGGER.info("[ParCool] Config migrated: {} -> {}",
                        suggestedConfigFile.getAbsolutePath(), target.getAbsolutePath());
            } else {
                LOGGER.warn("[ParCool] Could not migrate old config {} -> {}",
                        suggestedConfigFile.getAbsolutePath(), target.getAbsolutePath());
            }
        }

        configFile = target;

        // Configuration 的构造器会自动 load()；文件不存在时直接返回，不报错
        configuration = new Configuration(target);

        LOGGER.info("[ParCool] Configuration target: {}", target.getAbsolutePath());
    }

    /**
     * 把所有配置项写进 Configuration，然后落盘。
     *
     * <p>调用顺序：{@link #load(File)} 之后、任何 {@code .get()} 之前。幂等。</p>
     */
    public static void registerAll() {
        if (configuration == null) {
            throw new IllegalStateException("Forge14ConfigManager.load() must be called before registerAll()");
        }
        if (registered) return;

        for (ParCoolConfig.Client.Booleans item : ParCoolConfig.Client.Booleans.values()) item.register(configuration);
        for (ParCoolConfig.Client.Integers item : ParCoolConfig.Client.Integers.values()) item.register(configuration);
        for (ParCoolConfig.Client.Doubles item : ParCoolConfig.Client.Doubles.values()) item.register(configuration);

        for (ParCoolConfig.Server.Booleans item : ParCoolConfig.Server.Booleans.values()) item.register(configuration);
        for (ParCoolConfig.Server.Integers item : ParCoolConfig.Server.Integers.values()) item.register(configuration);
        for (ParCoolConfig.Server.Doubles item : ParCoolConfig.Server.Doubles.values()) item.register(configuration);

        // per-action 配置按 ActionList 动态生成，必须在枚举注册之后（它要用同一个 Configuration）
        ParCoolConfig.registerPerAction(configuration);
        // per-animator 配置按 AnimatorList 动态生成
        // ⚠️ 必须加【客户端守卫】✗：AnimatorList 的静态初始化会加载客户端动画器类 ✗，
        //    在专用服务端上会 NoClassDefFoundError ✗
        if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide().isClient()) {
            ParCoolConfig.registerPerAnimator(configuration);
        }
        // 非枚举的「额外项」（如 stamina_type / limitation_imposed）
        ParCoolConfig.Client.registerExtra(configuration);
        ParCoolConfig.Server.registerExtra(configuration);

        registered = true;
        LOGGER.info("[ParCool] Config items registered (client booleans={}, ints={}, doubles={}; server booleans={}, ints={}, doubles={})",
                ParCoolConfig.Client.Booleans.values().length,
                ParCoolConfig.Client.Integers.values().length,
                ParCoolConfig.Client.Doubles.values().length,
                ParCoolConfig.Server.Booleans.values().length,
                ParCoolConfig.Server.Integers.values().length,
                ParCoolConfig.Server.Doubles.values().length);

        save();
    }

    /**
     * 落盘。
     *
     * <p>1.12.2 与 1.16.5 有两处行为差异，都容易踩：</p>
     * <ol>
     *   <li><b>没有自动保存</b> —— 值被改过之后必须自己调这个</li>
     *   <li><b>Forge 不会告诉你文件写到哪了</b> —— 所以这里主动打印绝对路径与文件大小，
     *       否则「配置文件没生成」这种问题只能靠猜</li>
     * </ol>
     *
     * <p>另外：{@code Configuration.save()} 本身<b>不检查</b> {@code hasChanged()}
     * （反编译源码 {@code Configuration.java:1080-1102}：它直接建目录、建文件、写内容），
     * 所以这里不做条件判断 —— 多一层脏标记判断只会增加「文件静默没生成」的风险面。</p>
     */
    public static void save() {
        if (configuration == null) return;
        try {
            configuration.save();
        } catch (Throwable t) {
            LOGGER.error("[ParCool] Failed to save configuration", t);
            return;
        }
        if (configFile != null) {
            LOGGER.info("[ParCool] Config saved: {} (exists={}, size={} bytes)",
                    configFile.getAbsolutePath(), configFile.exists(),
                    configFile.exists() ? configFile.length() : -1L);
        }
    }

    /** 供启动自检打印用。 */
    @Nullable
    public static File getConfigFile() {
        return configFile;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 定义辅助：把 1.16.5 的 builder 调用翻译成 Configuration 调用
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 定义布尔项。
     *
     * <p>1.16.5 的 {@code builder.translation(key)} 在 1.12.2 没有对应 ——
     * Forge 1.12.2 的配置界面不支持逐项 i18n，只能靠 comment 说明。
     * 翻译 key 仍由各枚举项自己保留（{@code Translation} 字段），供 ParCool 自绘 GUI 用。</p>
     */
    public static Property defineBoolean(Configuration config, String category, @Nullable String comment, String path, boolean defaultValue) {
        Property property = config.get(category, path, defaultValue, comment);
        property.setLanguageKey("parcool.config.c." + path);
        return property;
    }

    public static Property defineInt(Configuration config, String category, @Nullable String comment, String path, int defaultValue, int min, int max) {
        // 用带 min/max 的重载，而不是先 get 再 setMinValue：
        // 后者对「配置文件里已存在的越界值」不会重新校验。
        Property property = config.get(category, path, defaultValue, comment, min, max);
        property.setLanguageKey("parcool.config.c." + path);
        return property;
    }

    public static Property defineDouble(Configuration config, String category, @Nullable String comment, String path, double defaultValue, double min, double max) {
        Property property = config.get(category, path, defaultValue, comment, min, max);
        property.setLanguageKey("parcool.config.c." + path);
        return property;
    }

    /** 枚举以字符串形式存储。 */
    public static Property defineString(Configuration config, String category, @Nullable String comment, String path, String defaultValue) {
        Property property = config.get(category, path, defaultValue, comment);
        property.setLanguageKey("parcool.config.c." + path);
        return property;
    }

    /**
     * **枚举型**配置 ✓ —— GUI 里会渲染成**点击循环的按钮** ✓，
     * 而不是要玩家手输的文本框 ✗。
     *
     * <h3>★ 为什么需要它</h3>
     * <p>本工程原本把枚举也存成普通字符串（{@code defineString} ✓）——
     * 结果在 Forge 配置界面里是**一个可编辑输入框** ✗，
     * 玩家得**自己敲** {@code Default} / {@code Hunger} ✗ —— 敲错大小写就 {@code valueOf} 抛异常 ✗。</p>
     *
     * <p>Forge 其实早就准备好了对应的控件 ✓：只要给 {@link Property} 设上
     * {@link Property#setValidValues(String[])} ✓，
     * {@code GuiConfigEntries} 就会把它渲染成
     * <b>{@code CycleValueEntry}</b> ✓（左右两个箭头按钮，点击在合法值之间循环 ✓）。</p>
     *
     * <p>⚠️ <b>validValues 由枚举自身推导</b> ✓（{@code getDeclaringClass().getEnumConstants()} ✓）——
     * 这样枚举将来加值，配置界面**自动跟上** ✓，不会出现"能选的值列表过期了"✗。</p>
     *
     * @param defaultValue 默认值 ✓ —— 它的类型决定可选值列表 ✓
     */
    public static <E extends Enum<E>> Property defineEnum(Configuration config, String category,
                                                          @Nullable String comment, String path, E defaultValue) {
        E[] constants = defaultValue.getDeclaringClass().getEnumConstants();
        String[] validValues = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            validValues[i] = constants[i].name();
        }

        Property property = config.get(category, path, defaultValue.name(), comment);
        property.setLanguageKey("parcool.config.c." + path);
        // ★ 这一句是「文本框 → 循环按钮」的关键 ✗★
        property.setValidValues(validValues);
        return property;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 读取辅助（都带兜底，配置项缺失/类型不对时不抛异常）
    // ────────────────────────────────────────────────────────────────────────

    public static boolean getBoolean(@Nullable Property property, boolean defaultValue) {
        if (property == null) return defaultValue;
        try {
            return property.getBoolean();
        } catch (Throwable t) {
            LOGGER.warn("[ParCool] Invalid boolean config '{}', using default {}", property.getName(), defaultValue);
            return defaultValue;
        }
    }

    public static int getInt(@Nullable Property property, int defaultValue) {
        if (property == null) return defaultValue;
        try {
            return property.getInt();
        } catch (Throwable t) {
            LOGGER.warn("[ParCool] Invalid int config '{}', using default {}", property.getName(), defaultValue);
            return defaultValue;
        }
    }

    public static double getDouble(@Nullable Property property, double defaultValue) {
        if (property == null) return defaultValue;
        try {
            return property.getDouble();
        } catch (Throwable t) {
            LOGGER.warn("[ParCool] Invalid double config '{}', using default {}", property.getName(), defaultValue);
            return defaultValue;
        }
    }

    public static String getString(@Nullable Property property, String defaultValue) {
        if (property == null) return defaultValue;
        try {
            String value = property.getString();
            return value == null ? defaultValue : value;
        } catch (Throwable t) {
            LOGGER.warn("[ParCool] Invalid string config '{}', using default {}", property.getName(), defaultValue);
            return defaultValue;
        }
    }
}
