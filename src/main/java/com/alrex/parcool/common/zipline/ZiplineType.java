package com.alrex.parcool.common.zipline;

import com.alrex.parcool.common.zipline.impl.GeneralQuadraticCurveZipline;
import com.alrex.parcool.common.zipline.impl.StraightZipline;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * 滑索的「松紧」档位 ✓ —— 决定用哪条曲线 ✓
 *
 * <table border="1">
 *   <tr><th>档位</th><th>曲线</th><th>下垂量</th></tr>
 *   <tr><td>{@code STRAIGHT}（紧）</td><td>直线</td><td>0</td></tr>
 *   <tr><td>{@code STANDARD}（标准）</td><td>直线 或 二次曲线</td>
 *       <td>两端等高时退化为直线 ✓</td></tr>
 *   <tr><td>{@code LOOSE}（松）</td><td>二次曲线</td>
 *       <td>{@code 0.35 + 0.035 * 距离} ✓ <b>随跨度增大</b></td></tr>
 *   <tr><td>{@code VERY_LOOSE}（很松）</td><td>二次曲线</td>
 *       <td>{@code 0.6 + 0.06 * 距离} ✓</td></tr>
 * </table>
 *
 * <p><b>⭐ 下垂量随跨度线性增长</b> ✗→✓ —— 所以长索会更明显地垂成弧形 ✓
 * 而不是「短索陡、长索平」✗ ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><td>{@code TranslationTextComponent}</td><td>{@link TextComponentTranslation} ✓</td></tr>
 *   <tr><td>{@code Vector3d}</td><td>{@link Vec3d} ✓</td></tr>
 * </table>
 */
public enum ZiplineType {
    STRAIGHT("parcool.gui.text.zipline.type.tight"),
    STANDARD("parcool.gui.text.zipline.type.normal"),
    LOOSE("parcool.gui.text.zipline.type.loose"),
    VERY_LOOSE("parcool.gui.text.zipline.type.very_loose");

    private final String translationID;

    ZiplineType(String translation) {
        this.translationID = translation;
    }

    public ITextComponent getTranslationName() {
        return new TextComponentTranslation(translationID);
    }

    /** 按本档位造一条滑索 ✓ */
    public Zipline getZipline(Vec3d point1, Vec3d point2) {
        if (this == STRAIGHT) {
            return new StraightZipline(point1, point2);
        } else if (this == STANDARD) {
            // 两端等高时直线与曲线等价 ✓ —— 直接用直线（更省 ✓）
            if (Math.abs(point1.y - point2.y) < 0.0001)
                return new StraightZipline(point1, point2);
            else
                return new GeneralQuadraticCurveZipline(point1, point2, 0);
        } else if (this == LOOSE) {
            return new GeneralQuadraticCurveZipline(point1, point2, 0.35 + 0.035 * point2.distanceTo(point1));
        } else if (this == VERY_LOOSE) {
            return new GeneralQuadraticCurveZipline(point1, point2, 0.6 + 0.06 * point2.distanceTo(point1));
        }
        return new StraightZipline(point1, point2);
    }
}
