package com.alrex.parcool.core;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.api.Attributes;
import com.alrex.parcool.api.Effects;
import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.common.capability.capabilities.Capabilities;
import com.alrex.parcool.common.potion.Potions;
import com.alrex.parcool.config.Forge14ConfigManager;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.AquaConfigCheck;
import com.alrex.parcool.platform.ParCoolPlatform;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 启动自检。
 *
 * <p>调用时机必须是 {@code FMLPostInitializationEvent}：Aqua 在 preInit 里才把配置文件的值
 * 赋给 {@code ConfigHandler} 的字段，过早读取会拿到默认值。</p>
 *
 * <p>当前检查项覆盖 P0（Mixin 链路 + Aqua 硬依赖）与 P1（注册表 + 配置）两个阶段。</p>
 */
public final class StartupChecks {

    private static final Logger LOGGER = LogManager.getLogger("ParCool");

    private StartupChecks() {
    }

    public static void run() {
        LOGGER.info("========== [ParCool] startup self-check ==========");

        checkMixinChain();
        checkAqua();
        checkRegistries();
        checkConfig();

        LOGGER.info("=================================================");
    }

    // ────────────────────────────────────────────────────────────────────────
    // P0：Mixin 与硬依赖
    // ────────────────────────────────────────────────────────────────────────

    private static void checkMixinChain() {
        LOGGER.info(" 1. Mixin framework        : {}",
                ParCoolCore.isMixinLoaded() ? "OK" : "FAILED  <<< 检查 MixinBootstrap 是否安装");

        // 需要进入世界后才能判定，见 client/ClientInGameCheck
        LOGGER.info(" 2. ParCool mixin          : {}",
                P0Verifier.hasTouched() ? "OK" : "PENDING (进入世界后判定)");

        LOGGER.info(" 3. Aqua mixin (interface) : {}",
                isAquaMixinApplied() ? "OK (EntityPlayer implements IPlayerResizeable)"
                                     : "FAILED  <<< Aqua 的 mixin 未生效，硬依赖 API 不可用");

        LOGGER.info(" 4. aquaacrobatics loaded  : {}", Loader.isModLoaded("aquaacrobatics") ? "OK" : "FAILED");
        LOGGER.info(" 5. patchouli loaded       : {} (optional)", Loader.isModLoaded("patchouli"));
    }

    /**
     * 「Aqua 的 mixin 是否生效」的判据是 {@code EntityPlayer} 实现了 Aqua 的接口。
     *
     * <p>注意：引用 {@code EntityPlayer.class} 会触发其类加载，因此 preInit 之前不要调用。</p>
     */
    public static boolean isAquaMixinApplied() {
        try {
            return IPlayerResizeable.class.isAssignableFrom(EntityPlayer.class);
        } catch (Throwable t) {
            LOGGER.error("[ParCool] Failed to check Aqua mixin application", t);
            return false;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // P1：注册表与属性
    // ────────────────────────────────────────────────────────────────────────

    private static void checkRegistries() {
        LOGGER.info(" 6. SoundEvents registered : {}", SoundEvents.VAULT.isPresent()
                ? "OK (vault = " + SoundEvents.VAULT.get().getRegistryName() + ")" : "FAILED");
        LOGGER.info(" 7. Potion effect          : {}", Effects.INEXHAUSTIBLE.isPresent()
                ? "OK (" + Effects.INEXHAUSTIBLE.get().getRegistryName() + ")" : "FAILED");
        LOGGER.info(" 8. PotionType             : {}", Potions.ENERGY_DRINK.isPresent()
                ? "OK (" + Potions.ENERGY_DRINK.get().getRegistryName() + ")" : "FAILED");
        LOGGER.info(" 9. Custom attributes      : {}", describeAttributes());
    }

    /**
     * 属性不经过注册表（1.12.2 的 {@code ForgeRegistries} 没有 ATTRIBUTES），
     * 所以这里核对的是「对象已创建」+ 打印实际名字，便于人工确认没有与别的 mod 撞名。
     *
     * <p>注意 {@code IAttribute} 的取值方法在 MCP stable_39 里叫 <b>{@code getName()}</b>，
     * 不叫 {@code getAttributeUnlocalizedName()}（后者是别的版本线的命名）。</p>
     */
    private static String describeAttributes() {
        try {
            return "created (max_stamina=" + Attributes.MAX_STAMINA.getName()
                    + ", stamina_recovery=" + Attributes.STAMINA_RECOVERY.getName()
                    + ", vanilla reference=" + SharedMonsterAttributes.MOVEMENT_SPEED.getName() + ")";
        } catch (Throwable t) {
            LOGGER.error("[ParCool] Failed to inspect attributes", t);
            return "FAILED";
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // P1：配置
    // ────────────────────────────────────────────────────────────────────────

    private static void checkConfig() {
        try {
            java.io.File file = Forge14ConfigManager.getConfigFile();
            LOGGER.info("10. Config file           : {}", file == null ? "NULL  <<< load() 未被调用"
                    : file.getAbsolutePath() + " (exists=" + file.exists()
                      + ", size=" + (file.exists() ? file.length() : -1L) + " bytes)");

            LOGGER.info("11. Config (client)       : ParCoolIsActive={}, EnableAnimation={}, InfiniteStamina={}",
                    ParCoolConfig.Client.Booleans.ParCoolIsActive.get(),
                    ParCoolConfig.Client.Booleans.EnableAnimation.get(),
                    ParCoolConfig.Client.Booleans.InfiniteStamina.get());
            // 注意：这里刻意不再打印 AdvantageousValue —— 它<b>不是</b>配置项，
            // 而是 Server.Booleans 每个枚举项上的一个字段（见 ParCoolConfig 里的修正记录）。
            LOGGER.info("12. Config (server)       : AllowInfiniteStamina={}, MaxWallRunContinuableTick={}",
                    ParCoolConfig.Server.Booleans.AllowInfiniteStamina.get(),
                    ParCoolConfig.Server.Integers.MaxWallRunContinuableTick.get());
            LOGGER.info("13. Proxy.isActive()      : {}", ParCool.isActive());
        } catch (Throwable t) {
            LOGGER.error("10-13. Config             : FAILED  <<< 配置未注册或注册时机不对", t);
        }

        checkCapabilities();
    }

    /**
     * Capability 检查。
     *
     * <p>这里检查的是 <b>{@code @CapabilityInject} 有没有把静态字段填上</b> ——
     * 它由 FML 在注册后反射注入。字段为 null 说明 {@code Capabilities.register()}
     * 没被调用，或调用时机晚于 FML 的注入。</p>
     *
     * <p>「实例是否真的挂到玩家身上」需要玩家实例，放到进世界后的
     * {@code ClientInGameCheck} 里验证。</p>
     */
    private static void checkCapabilities() {
        try {
            boolean injected = Capabilities.STAMINA_CAPABILITY != null;
            LOGGER.info("14. Stamina capability    : {}",
                    injected ? "OK (injected, location=" + Capabilities.STAMINA_LOCATION + ")"
                             : "FAILED  <<< Capabilities.register() 未调用或时机过晚");
        } catch (Throwable t) {
            LOGGER.error("14. Stamina capability    : FAILED", t);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Aqua 联动（移植方案 §4.5）
    // ────────────────────────────────────────────────────────────────────────

    private static void checkAqua() {
        AquaConfigCheck.logStartupWarnings();
        // 顺带确认 ParCoolPlatform 的 API 收口在服务端也能正常访问（纯类加载检查，不碰玩家实例）
        LOGGER.info("    ParCoolPlatform ready   : {}", ParCoolPlatform.class.getName());
    }
}
