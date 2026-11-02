package com.alrex.parcool.utilities;

/**
 * 小数学工具（1.12.2 版）。
 *
 * <p>上游在 {@code com.alrex.parcool.utilities.MathUtil} 里放了若干 lerp 变体，
 * Animator 与 {@code PlayerModelTransformer} 大量使用。这里按用到的签名补齐。</p>
 */
public final class MathUtil {

    private MathUtil() {
    }

    /** 与被插值对象同向的线性插值：{@code factor = 0} 返回 {@code from}。 */
    public static float lerp(float from, float to, float factor) {
        return from + (to - from) * factor;
    }

    public static double lerp(double from, double to, double factor) {
        return from + (to - from) * factor;
    }

    /** {@code factor = 0} 返回 {@code from}；超出 [0,1] 也照常外插（与上游一致）。 */
    /**
     * 与 {@link #lerpBy(float, float, float)} 同义，但走 **double** ✓。
     *
     * <p>⚠️ 1.12.2 的 {@code MathHelper} 没有通用的 lerp ✗（只有 clampedLerp ✗），
     * 而本工程多处需要「按系数插值」✓ → 这里补齐两种精度 ✓。</p>
     */
    public static double lerpBy(double factor, double from, double to) {
        return from + (to - from) * factor;
    }

    public static float lerpBy(float factor, float from, float to) {
        return lerp(from, to, factor);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** 把角度归一化到 [0, 360)。 */
    public static float normalizeDegrees(float degrees) {
        float result = degrees % 360f;
        return result < 0 ? result + 360f : result;
    }

    /**
     * 平方（上游 {@code MathUtil.squaring}）。
     *
     * <p>{@code RollAnimator} 用它算「滚动位移因子」：
     * {@code -squaring(progress - 1) + 1} —— 即一个开口向下的抛物线，
     * progress 从 0→1 时因子从 0 升到 1 再回到 0 ✓</p>
     */
    public static float squaring(float value) {
        return value * value;
    }

    public static double squaring(double value) {
        return value * value;
    }
}
