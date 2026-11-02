package com.alrex.parcool.utilities;

/**
 * 缓动函数（1.12.2 版，<b>1:1 移植，逐字未改</b>）。
 *
 * <p>纯数学，无任何 MC API —— 上游原文的类注释：</p>
 * <blockquote>All Functions take arg1 as phase(0~1) and return value will also be in 0~1</blockquote>
 */
public class EasingFunctions {

    public static float CubicInOut(float phase) {
        if (phase < 0.5) {
            return 4 * phase * phase * phase;
        } else {
            phase = 1 - phase;
            return 1 - 4 * phase * phase * phase;
        }
    }

    public static float SinInOutBySquare(float phase) {
        if (phase < 0.5) {
            return 2 * phase * phase;
        } else {
            return 1 - 2 * (phase - 1) * (phase - 1);
        }
    }
}
