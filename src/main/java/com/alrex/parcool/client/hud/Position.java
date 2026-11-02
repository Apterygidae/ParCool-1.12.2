package com.alrex.parcool.client.hud;

import net.minecraft.util.Tuple;

/**
 * HUD 的**位置描述** ✓ —— 1:1 移植自上游（1.16.5）。
 *
 * <p>不直接存像素坐标 ✓，而是存「对齐方式 + 边距」✓ ——
 * 这样窗口尺寸变化时能自动跟随 ✓。</p>
 *
 * <h3>⚠️ 1.12.2 的差异（只有一处）</h3>
 * <p>{@link Tuple} 的取值方法名不同：1.12.2 是 {@code getFirst()/getSecond()} ✗，
 * 而 1.16 是 {@code getA()/getB()} ✓ —— 见 {@link #calculate} 的调用方。</p>
 */
public class Position {

    /** 默认位置（左上角、无边距）✓ —— 供配置缺省时兜底。 */
    public static final Position DEFAULT = new Position(Horizontal.Left, Vertical.Top, 0, 0);

    public enum Horizontal {Left, Right}

    public enum Vertical {Top, Bottom}

    private Horizontal alignmentHorizontal;
    private Vertical alignmentVertical;
    private int marginHorizontal;
    private int marginVertical;

    public Position(Horizontal alignH, Vertical alignV, int marginH, int marginV) {
        this.alignmentHorizontal = alignH;
        this.alignmentVertical = alignV;
        this.marginHorizontal = marginH;
        this.marginVertical = marginV;
    }

    /**
     * 算出 HUD **左上角** 的屏幕坐标 ✓。
     *
     * @param width        该 HUD 的宽度 ✓
     * @param height       该 HUD 的高度 ✓
     * @param screenWidth  当前 GUI 缩放后的屏幕宽 ✓
     * @param screenHeight 当前 GUI 缩放后的屏幕高 ✓
     */
    public Tuple<Integer, Integer> calculate(int width, int height, int screenWidth, int screenHeight) {
        int x = (alignmentHorizontal == Horizontal.Right)
                ? screenWidth - marginHorizontal - width
                : marginHorizontal;
        int y = (alignmentVertical == Vertical.Bottom)
                ? screenHeight - marginVertical - height
                : marginVertical;
        return new Tuple<>(x, y);
    }

    // ── getters / setters（配置 GUI 双向绑定用 ✓）──────────────────────

    public void setAlignmentHorizontal(Horizontal alignmentHorizontal) {
        this.alignmentHorizontal = alignmentHorizontal;
    }

    public void setAlignmentVertical(Vertical alignmentVertical) {
        this.alignmentVertical = alignmentVertical;
    }

    public void setMarginHorizontal(int marginHorizontal) {
        this.marginHorizontal = marginHorizontal;
    }

    public void setMarginVertical(int marginVertical) {
        this.marginVertical = marginVertical;
    }

    public Horizontal getAlignmentHorizontal() {
        return alignmentHorizontal;
    }

    public Vertical getAlignmentVertical() {
        return alignmentVertical;
    }

    public int getMarginHorizontal() {
        return marginHorizontal;
    }

    public int getMarginVertical() {
        return marginVertical;
    }
}
