package com.alrex.parcool.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * P0 专用的 Mixin 注入验证器。
 *
 * <p>为什么单独放一个类、而不是在 mixin 类里写静态字段：Mixin 对静态字段与
 * {@code <clinit>} 的合并有额外约束，用一个普通的持有类最省事，也便于 P0 之后整体删除。</p>
 *
 * <p><b>P0 结束后可以删除本类</b>，届时把 {@code EntityPlayerMixin} 里对它的调用一并清掉即可。</p>
 */
public final class P0Verifier {

    private static final Logger LOGGER = LogManager.getLogger("ParCool");
    private static final AtomicBoolean TOUCHED = new AtomicBoolean(false);

    private P0Verifier() {
    }

    /**
     * 由 {@code EntityPlayerMixin} 在每个 tick 调用；只在第一次真正打印日志。
     *
     * <p>能看到这行日志 = 三件事同时成立：① Mixin 配置被加载；② 目标方法名在
     * obf 环境下正确解析（也就是 refmap 生效）；③ 注入点确实执行了。</p>
     */
    public static void markMixinApplied(String where) {
        if (TOUCHED.compareAndSet(false, true)) {
            LOGGER.info("-----------------------------------------------------------");
            LOGGER.info("[ParCool/P0] >>> MIXIN INJECTION VERIFIED <<< at {}", where);
            LOGGER.info("[ParCool/P0] refmap 与 Mixin 引导链路正常。");
            LOGGER.info("-----------------------------------------------------------");
        }
    }

    /** 注入点是否已经执行过。 */
    public static boolean hasTouched() {
        return TOUCHED.get();
    }
}
