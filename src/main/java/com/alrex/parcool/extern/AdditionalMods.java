package com.alrex.parcool.extern;

import com.alrex.parcool.common.action.impl.Dodge;

/**
 * 「可选第三方 mod 集成」的收口（1.12.2 版）—— **本工程刻意不实现这些集成**。
 *
 * <h3>为什么是空实现</h3>
 * <p>上游通过本类适配两个<b>只在高版本存在</b>的相机 mod：</p>
 * <ul>
 *   <li>{@code BetterThirdPerson} —— 1.19+ 才有，1.12.2 不存在</li>
 *   <li>{@code ShoulderSurfing} —— 1.12.2 有旧版，但 API 完全不同，需要单独重写</li>
 * </ul>
 * <p>移植方案里对 {@code extern/} 的决定是：<b>BetterThirdPerson 直接删；ShoulderSurfing
 * 重写或删</b>。所以本类保留上游的<b>调用形状</b>（这样动作代码不用改），
 * 但所有回调都是<b>透传</b> —— 即「不做任何相机相关处理」✓</p>
 *
 * <p><b>行为影响</b>：在装了上述相机 mod 的环境下，闪避方向不会按相机朝向校正。
 * 对原版相机的玩家<b>没有区别</b> ✓（因为原版本来就该透传）</p>
 */
public final class AdditionalMods {

    private AdditionalMods() {
    }

    /** 对应上游 {@code BetterThirdPersonManager}。此处是透传实现。 */
    public static CameraHandler betterThirdPerson() {
        return CameraHandler.PASS_THROUGH;
    }

    /** 对应上游 {@code ShoulderSurfingManager}。此处是透传实现。 */
    public static CameraHandler shoulderSurfingManager() {
        return CameraHandler.PASS_THROUGH;
    }

    /**
     * 相机是否与身体解耦（装了特定相机 mod 时为 true）。
     *
     * <p>本工程恒 {@code false} —— 见类注释 ✓</p>
     */
    public static boolean isCameraDecoupled() {
        return false;
    }

    /** 上游那两个 manager 的公共调用形状：按相机朝向校正闪避方向。 */
    public interface CameraHandler {
        Dodge.DodgeDirection handleCustomCameraRotationForDodge(Dodge.DodgeDirection direction);

        /** 不做处理，原样返回 ✓ */
        CameraHandler PASS_THROUGH = direction -> direction;
    }
}
