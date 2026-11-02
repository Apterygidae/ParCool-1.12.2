package com.alrex.parcool.platform;

import com.fuzs.aquaacrobatics.config.ConfigHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Aqua Acrobatics 配置的联动检查与查询。
 *
 * <p>移植方案 §4.5：Aqua 的若干配置项会<b>直接改变 ParCool 的行为</b>，
 * 而且玩家可以在 Aqua 的配置文件里随意改。因此 ParCool 必须：</p>
 * <ol>
 *   <li>启动时读一遍，对危险组合打警告（{@link #logStartupWarnings()}）</li>
 *   <li>提供查询接口，让 ParCool 的功能按 Aqua 的实际配置自适应（下面那些静态方法）</li>
 * </ol>
 *
 * <p>⚠️ <b>调用时机：只能在 FMLPreInitializationEvent 之后</b>。Aqua 是在它自己的 preInit
 * 里才把配置文件的值赋给 {@link ConfigHandler} 的字段，过早读取拿到的是硬编码默认值。</p>
 *
 * <p>所有读取都包了 try/catch：如果 Aqua 将来改了字段名，ParCool 应该退化成"读不到就按默认走"，
 * 而不是启动崩溃。</p>
 */
public final class AquaConfigCheck {

    private static final Logger LOGGER = LogManager.getLogger("ParCool");

    private AquaConfigCheck() {
    }

    // ────────────────────────────────────────────────────────────────────────
    // 查询接口（带安全兜底，读不到时返回「对 ParCool 最安全」的值）
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 玩家能否进入爬行姿态。
     * <p><b>ParCool 的 {@code Crawl} / {@code StartSwimByCrawl} 依赖它为 true</b>：
     * 为 false 时 {@code Pose.SWIMMING} 那条路径不完整，爬行不会被正确识别。</p>
     */
    public static boolean isCrawlingEnabled() {
        try {
            return ConfigHandler.MovementConfig.enableCrawling;
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Aqua 是否注册了「切换爬行」按键。
     * <p>开启时 Aqua 会占用默认键 <b>C</b>，与 ParCool 的
     * {@code key.parcool.Crawl} 和 {@code key.parcool.HideInBlock} 冲突。</p>
     */
    public static boolean isToggleCrawlingEnabled() {
        try {
            return ConfigHandler.MovementConfig.enableToggleCrawling;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Aqua 是否允许「空中按跳跃直接起飞滑翔翼」。
     * <p>开启时与 ParCool 的 {@code BehaviorEnforcer.cancelFallFlying()} 语义对立
     * （ParCool 想在滑铲/翻滚等动作期间禁止起飞）。P3 需要据此协调。</p>
     */
    public static boolean isEasyElytraTakeoffEnabled() {
        try {
            return ConfigHandler.MovementConfig.easyElytraTakeoff;
        } catch (Throwable t) {
            return true;
        }
    }

    /** Aqua 是否关掉了疾跑双击检测（影响 ParCool FastRun 的手感）。 */
    public static boolean isNoDoubleTapSprinting() {
        try {
            return ConfigHandler.MovementConfig.noDoubleTapSprinting;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Aqua 是否允许侧向疾跑（影响 ParCool 侧向动作与泳姿动画的衔接）。 */
    public static boolean isSidewaysSprinting() {
        try {
            return ConfigHandler.MovementConfig.sidewaysSprinting;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Aqua 是否允许侧向游泳。 */
    public static boolean isSidewaysSwimming() {
        try {
            return ConfigHandler.MovementConfig.sidewaysSwimming;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Aqua 的「新攀爬行为」：改 EntityLivingBase#travel 里的 collidedHorizontally 判定，影响 ParCool ClimbPoles。 */
    public static boolean isNewClimbingBehavior() {
        try {
            return ConfigHandler.MovementConfig.newClimbingBehavior;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Aqua 的方块碰撞模式。EXACT 下贴墙类动作更精确。 */
    public static String getPlayerBlockCollisions() {
        try {
            return String.valueOf(ConfigHandler.playerBlockCollisions);
        } catch (Throwable t) {
            return "UNKNOWN";
        }
    }

    /** 气泡柱是否启用（1.13 概念，Aqua 默认关闭）。 */
    public static boolean isBubbleColumnsEnabled() {
        try {
            return ConfigHandler.MiscellaneousConfig.bubbleColumns;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Aqua 是否接管了水中潜行下沉。 */
    public static boolean isSneakingForParrots() {
        try {
            return ConfigHandler.MiscellaneousConfig.sneakingForParrots;
        } catch (Throwable t) {
            return true;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 启动期检查
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 打印一轮启动警告。只在 FMLPreInitializationEvent 之后调用。
     *
     * @return 是否读到了 Aqua 配置（false 说明字段名可能变了，需要更新本类）
     */
    public static boolean logStartupWarnings() {
        boolean readable;
        boolean crawling;
        boolean toggleCrawling;
        boolean easyElytra;
        try {
            crawling = ConfigHandler.MovementConfig.enableCrawling;
            toggleCrawling = ConfigHandler.MovementConfig.enableToggleCrawling;
            easyElytra = ConfigHandler.MovementConfig.easyElytraTakeoff;
            readable = true;
            LOGGER.info("[ParCool] Aqua MovementConfig : enableCrawling={}, enableToggleCrawling={}, easyElytraTakeoff={}, noDoubleTapSprinting={}",
                    crawling, toggleCrawling, easyElytra, ConfigHandler.MovementConfig.noDoubleTapSprinting);
        } catch (Throwable t) {
            LOGGER.error("[ParCool] Failed to read Aqua MovementConfig. If Aqua was updated, AquaConfigCheck needs updating.", t);
            return false;
        }

        if (!crawling) {
            LOGGER.warn("[ParCool] ============================================================");
            LOGGER.warn("[ParCool] Aqua 的 enableCrawling = false");
            LOGGER.warn("[ParCool] ParCool 的 Crawl / StartSwimByCrawl 会行为异常（Pose.SWIMMING 路径不完整）。");
            LOGGER.warn("[ParCool] 请在 config/aquaacrobatics.cfg 里把 Movement.enableCrawling 改回 true。");
            LOGGER.warn("[ParCool] ============================================================");
        }
        if (toggleCrawling) {
            LOGGER.info("[ParCool] ============================================================");
            LOGGER.info("[ParCool] Aqua 的 enableToggleCrawling = true —— 这是<b>正确</b>的状态，请保持。");
            LOGGER.info("[ParCool]    · ParCool 的 Crawl 动作靠 Aqua 的 setForcingCrawling 维持爬行姿态");
            LOGGER.info("[ParCool]      而它内部有 canForceCrawling() 检查，依赖本项为 true");
            LOGGER.info("[ParCool]      （关掉它 = ParCool 的爬行永久失效）");
            LOGGER.info("[ParCool]    · 但它的<b>默认按键和 ParCool 的 key.parcool.Crawl 一样是 C</b>，");
            LOGGER.info("[ParCool]      按住时会两边同时改姿态 → 碰撞箱在站立/爬行间反复切换（鬼畜）。");
            LOGGER.info("[ParCool]    → 请在「选项 → 控制」里把 Aqua 那个 toggle-crawl 键");
            LOGGER.info("[ParCool]      <b>改绑或清空</b>，让 C 键只归 ParCool。");
            LOGGER.info("[ParCool]    → 不要关掉 Movement.enableToggleCrawling 本身。");
            LOGGER.info("[ParCool] ============================================================");
        } else {
            LOGGER.warn("[ParCool] ============================================================");
            LOGGER.warn("[ParCool] ⚠ Aqua 的 enableToggleCrawling = false —— ParCool 的爬行将无法工作。");
            LOGGER.warn("[ParCool]    ParCool 的 Crawl 通过 Aqua 的 setForcingCrawling() 维持爬行姿态，");
            LOGGER.warn("[ParCool]    而它内部有 canForceCrawling() 检查：");
            LOGGER.warn("[ParCool]        canForceCrawling() = enableToggleCrawling && ...");
            LOGGER.warn("[ParCool]    → 本项为 false 时该调用会被直接挡掉，玩家永远趴不下去。");
            LOGGER.warn("[ParCool]    → 请在 config/aquaacrobatics.cfg 里把它改回 true，");
            LOGGER.warn("[ParCool]      然后在「选项 → 控制」里把 Aqua 那个 toggle-crawl 键改绑或清空");
            LOGGER.warn("[ParCool]      （否则它会和 ParCool 的 C 键撞车，导致碰撞箱鬼畜）。");
            LOGGER.warn("[ParCool] ============================================================");
        }
        if (easyElytra) {
            LOGGER.warn("[ParCool] Aqua 的 easyElytraTakeoff = true：Aqua 会在空中按跳跃时主动起飞滑翔翼，");
            LOGGER.warn("[ParCool] 与 ParCool 的 cancelFallFlying() 语义对立（P3 阶段协调）。");
        }
        return readable;
    }
}
