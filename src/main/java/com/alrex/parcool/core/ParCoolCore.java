package com.alrex.parcool.core;

import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Map;

/**
 * 1.12.2 的 Mixin 引导入口。
 *
 * <p>这是与 1.16.5 最本质的差异：Forge 36 自带 Mixin，只要在 jar manifest 里写
 * {@code TweakClass=MixinTweaker} 就能生效；而 Forge 14.23.5 不带 Mixin，
 * 必须自己实现一个 IFMLLoadingPlugin 作为 CoreMod，手工调用
 * {@link MixinBootstrap#init()} 并注册 mixin 配置。</p>
 *
 * <p>本类的结构照搬 aquaacrobatics-1.12 的 {@code AquaAcrobaticsCore}（已验证可用）。</p>
 *
 * <p><b>不要在本类里引用 {@code ParCool} 类</b>：CoreMod 的加载时机早于 mod 类，
 * 引用它可能触发过早的类初始化。这里是硬编码常量，最安全。</p>
 */
@IFMLLoadingPlugin.Name(ParCoolCore.NAME)
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class ParCoolCore implements IFMLLoadingPlugin {

    public static final String NAME = "ParCool Core";

    /** Mixin 配置路径：1.12.2 约定放在 META-INF 下（1.16.5 是放在 jar 根目录）。 */
    private static final String MIXIN_CONFIG = "META-INF/mixins.parcool.json";

    private static final Logger LOGGER = LogManager.getLogger(NAME);

    private static boolean mixinLoaded = false;
    private static boolean fgDevHack = false;

    public ParCoolCore() {
        // dev 环境若把本 jar 重复当作普通 coremod 加载，可用 -Dparcool.fghack=true 触发早初始化。
        // 默认 false：走 IFMLCallHook 路径（getSetupClass 返回的 ParCoolSetupHook）。
        fgDevHack = "true".equals(System.getProperty("parcool.fghack"));
        if (fgDevHack) {
            setupMixins();
        }
    }

    /**
     * 幂等。既可能由构造器调用（fgDevHack），也可能由 {@link ParCoolSetupHook#call()} 调用。
     */
    static void setupMixins() {
        if (mixinLoaded) return;

        try {
            Class.forName("org.spongepowered.asm.launch.MixinTweaker");
        } catch (ClassNotFoundException e) {
            LOGGER.fatal("No Mixin framework detected. ParCool CANNOT work without it.");
            LOGGER.fatal("Please install MixinBootstrap (or MixinBooter).");
            LOGGER.fatal("Note: Aqua Acrobatics requires MixinBootstrap as well, so having Aqua usually means it is already present.");
            return;
        }

        MixinBootstrap.init();
        Mixins.addConfiguration(MIXIN_CONFIG);
        mixinLoaded = true;
        LOGGER.info("[ParCool] Mixin framework initialised, config = {}", MIXIN_CONFIG);

        if (fgDevHack) {
            LOGGER.info("[ParCool] Running with -Dparcool.fghack=true, applying dev workaround.");
            CodeSource codeSource = ParCoolCore.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                URL location = codeSource.getLocation();
                try {
                    File file = new File(location.toURI());
                    if (file.isFile()) {
                        CoreModManager.getReparseableCoremods().remove(file.getName());
                    }
                } catch (URISyntaxException ignored) {
                    // 忽略：仅影响 dev 环境的重复加载问题
                }
            } else {
                LOGGER.warn("[ParCool] No CodeSource. If this is not a development environment there may be problems.");
            }
        }
    }

    /** Mixin 框架是否成功初始化。供启动自检使用。 */
    public static boolean isMixinLoaded() {
        return mixinLoaded;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    /**
     * 返回类名字符串而非 Class 对象 —— 让 FML 在正确的时机才加载这个类。
     */
    @Nullable
    @Override
    public String getSetupClass() {
        return "com.alrex.parcool.core.ParCoolSetupHook";
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // 不需要读取 coremod 的 jar 位置
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
