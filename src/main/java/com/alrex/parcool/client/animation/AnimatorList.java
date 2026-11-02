package com.alrex.parcool.client.animation;

import com.alrex.parcool.client.animation.impl.BackwardWallJumpAnimator;
import com.alrex.parcool.client.animation.impl.CatLeapAnimator;
import com.alrex.parcool.client.animation.impl.ChargeJumpAnimator;
import com.alrex.parcool.client.animation.impl.ClimbUpAnimator;
import com.alrex.parcool.client.animation.impl.ClingToCliffAnimator;
import com.alrex.parcool.client.animation.impl.CrawlAnimator;
import com.alrex.parcool.client.animation.impl.DiveAnimationHostAnimator;
import com.alrex.parcool.client.animation.impl.DiveIntoWaterAnimator;
import com.alrex.parcool.client.animation.impl.DodgeAnimator;
import com.alrex.parcool.client.animation.impl.FastRunningAnimator;
import com.alrex.parcool.client.animation.impl.FastSwimAnimator;
import com.alrex.parcool.client.animation.impl.FlippingAnimator;
import com.alrex.parcool.client.animation.impl.HangAnimator;
import com.alrex.parcool.client.animation.impl.HideInBlockAnimator;
import com.alrex.parcool.client.animation.impl.HorizontalWallRunAnimator;
import com.alrex.parcool.client.animation.impl.JumpChargingAnimator;
import com.alrex.parcool.client.animation.impl.JumpFromBarAnimator;
import com.alrex.parcool.client.animation.impl.KongVaultAnimator;
import com.alrex.parcool.client.animation.impl.RollAnimator;
import com.alrex.parcool.client.animation.impl.SlidingAnimator;
import com.alrex.parcool.client.animation.impl.SpeedVaultAnimator;
import com.alrex.parcool.client.animation.impl.TapAnimator;
import com.alrex.parcool.client.animation.impl.VerticalWallRunAnimator;
import com.alrex.parcool.client.animation.impl.WallJumpAnimator;
import com.alrex.parcool.client.animation.impl.WallSlideAnimator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * 全部动画器的登记表 ✓ —— 让配置能为**每个动画器**生成一个开关 ✓
 *
 * <h3>★ 它解决什么问题</h3>
 * <p>玩家可以逐个关掉不喜欢的动画 ✓ ——
 * 配置里会生成 {@code animator_possibility.<动画器名>} ✓，
 * 由 {@link com.alrex.parcool.config.ParCoolConfig.Client#canAnimate} 读取 ✓，
 * 最终在 {@code Animation#setAnimator} 里生效（关了就不设置那个动画器 ✓）。</p>
 *
 * <h3>★ 为什么用「名字」而不是「索引」做配置键</h3>
 * <p>与 {@code ActionList} 同一考虑 ✓ —— <b>往列表里追加新动画器不会让已有配置失效</b> ✓
 * （索引会整体位移 ✗）。</p>
 *
 * <h3>⚠️ 与上游的差异</h3>
 * <p>上游列表里有 <b>{@code RideZiplineAnimator}</b> ✗ —— 本工程尚未移植 `RideZipline` ✓，
 * 该动画器还不存在 ✓，所以<b>暂不列入</b> ✓。
 * 等 `RideZipline` 完成后加进来即可（<b>只需在 {@link #ANIMATORS} 里加一行</b> ✓）。</p>
 *
 * <p><b>⚠️ 同理，上游的 {@code ExaggeratedSideDodgeAnimator} 本工程也没有</b> ✗ ——
 * 但已核实它<b>在上游只被自己引用</b> ✓（是死代码 ✗）→ <b>不列入是正确的</b> ✓。</p>
 *
 * <h3>⚠️ 为什么不加 {@code @SideOnly(CLIENT)}</h3>
 * <p>虽然它引用的动画器都是客户端类 ✗，但<b>配置类（公共）要引用本类</b> ✓ ——
 * 加了注解会让公共侧的引用被剥离/失效 ✗。<b>上游也没有加</b> ✓。</p>
 * <p><b>⭐ 代价与对策</b>：本类的静态初始化会加载客户端动画器类 ✗ ——
 * 在专用服务端上会失败 ✗。<b>所以「逐动画器配置的注册」必须加客户端守卫</b> ✓
 * （见 {@code ParCoolConfig.registerPerAnimator} ✓）。
 * 本类<b>只在客户端被真正触达</b> ✓（服务端不会调 {@code canAnimate} ✗）✓</p>
 */
public class AnimatorList {

    public static final List<Class<? extends Animator>> ANIMATORS = Arrays.asList(
            BackwardWallJumpAnimator.class,
            CatLeapAnimator.class,
            ChargeJumpAnimator.class,
            ClimbUpAnimator.class,
            ClingToCliffAnimator.class,
            CrawlAnimator.class,
            DiveAnimationHostAnimator.class,
            DiveIntoWaterAnimator.class,
            DodgeAnimator.class,
            FastRunningAnimator.class,
            FastSwimAnimator.class,
            FlippingAnimator.class,
            HangAnimator.class,
            HideInBlockAnimator.class,
            HorizontalWallRunAnimator.class,
            JumpChargingAnimator.class,
            JumpFromBarAnimator.class,
            KongVaultAnimator.class,
            RollAnimator.class,
            SlidingAnimator.class,
            SpeedVaultAnimator.class,
            TapAnimator.class,
            VerticalWallRunAnimator.class,
            WallJumpAnimator.class,
            WallSlideAnimator.class
    );

    private static final HashMap<Class<? extends Animator>, Short> INDEX_MAP;

    static {
        INDEX_MAP = new HashMap<>((int) (ANIMATORS.size() * 1.5));
        for (Class<? extends Animator> animator : ANIMATORS) {
            INDEX_MAP.put(animator, (short) ANIMATORS.indexOf(animator));
        }
    }

    private AnimatorList() {
    }

    /** 取索引；不在表里（例如尚未移植的动画器 ✓）返回 {@code -1} ✓。 */
    public static short getIndex(Class<? extends Animator> animator) {
        Short index = INDEX_MAP.get(animator);
        return index == null ? (short) -1 : index;
    }

    /** 按键名生成配置时用 ✓（与 {@code ActionList.getByIndex} 对称）。 */
    public static Class<? extends Animator> getByIndex(int index) {
        if (index < 0 || index >= ANIMATORS.size()) return null;
        return ANIMATORS.get(index);
    }

    public static int getSize() {
        return ANIMATORS.size();
    }
}
