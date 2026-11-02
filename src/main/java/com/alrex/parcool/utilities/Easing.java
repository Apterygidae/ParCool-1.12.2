package com.alrex.parcool.utilities;

/**
 * 分段缓动构造器（1.12.2 版，<b>1:1 移植，逐字未改</b>）。
 *
 * <p>用法：{@code new Easing(phase).squareOut(0, 1, 0, 1).get()}</p>
 * <ul>
 *   <li>每个方法收到 {@code (start, until)} 区间；只有第一个命中区间的会生效
 *       （之后 {@code calculated = true}，其余直接返回）</li>
 *   <li>都没命中时 {@code get()} 返回 {@code phase} 本身</li>
 * </ul>
 *
 * <p>纯数学（只依赖 {@link MathUtil#lerp}），**被上游 16 个文件使用** ——
 * 是动画器的基础设施。</p>
 */
public class Easing {

    float phase;
    float result;
    boolean calculated = false;

    public Easing(float phase) {
        this.phase = phase;
    }

    public static Easing with(float phase) {
        return new Easing(phase);
    }

    public boolean isInRange(float start, float until) {
        return start <= phase && phase < until;
    }

    public Easing sinInOut(float start, float until, float from, float to) {
        if (calculated) return this;
        if (!isInRange(start, until)) return this;
        float offset = phase - start;
        result = MathUtil.lerp(from, to, EasingFunctions.SinInOutBySquare(offset / (until - start)));
        calculated = true;
        return this;
    }

    public Easing cubicInOut(float start, float until, float from, float to) {
        if (calculated) return this;
        if (!isInRange(start, until)) return this;
        float offset = phase - start;
        result = MathUtil.lerp(from, to, EasingFunctions.CubicInOut(offset / (until - start)));
        calculated = true;
        return this;
    }

    public Easing squareIn(float start, float until, float from, float to) {
        if (calculated) return this;
        if (!isInRange(start, until)) return this;
        float offset = phase - start;
        float inPhase = offset / (until - start);
        result = MathUtil.lerp(from, to, inPhase * inPhase);
        calculated = true;
        return this;
    }

    public Easing squareOut(float start, float until, float from, float to) {
        if (calculated) return this;
        if (!isInRange(start, until)) return this;
        float offset = phase - start;
        float inPhase = 1 - offset / (until - start);
        result = MathUtil.lerp(from, to, 1 - inPhase * inPhase);
        calculated = true;
        return this;
    }

    public Easing linear(float start, float until, float from, float to) {
        if (calculated) return this;
        if (!isInRange(start, until)) return this;
        float offset = phase - start;
        float inPhase = offset / (until - start);
        result = MathUtil.lerp(from, to, inPhase);
        calculated = true;
        return this;
    }

    public Easing noChange(float start, float until, float value) {
        if (calculated) return this;
        if (!isInRange(start, until)) return this;
        result = value;
        calculated = true;
        return this;
    }

    public float get() {
        if (calculated) {
            return result;
        }
        return phase;
    }
}
