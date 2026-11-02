package com.alrex.parcool.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * 「渲染期行为强制」的客户端小工具（1.12.2 版）。
 *
 * <h3>它解决什么问题</h3>
 * <p>某些动作要求<b>临时把摄像机切到第三人称</b>（藏进方块时最典型 —— 第一人称会看到方块内部 ✓）。
 * 上游的做法是：动作置一个「标记 + 想要的相机类型」，再由一个
 * {@code GameSettingsMixin} 在 {@code getCameraType()} 被调用时返回被强制的值 ✓</p>
 *
 * <h3>★★ 1.12.2 的关键差异：没有 {@code PointOfView}，也没有 getter 可注入</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PointOfView} 枚举（FIRST_PERSON / THIRD_PERSON_BACK / …）</td>
 *       <td><b>没有这个枚举</b> ✗ → 用 {@code int thirdPersonView}（0/1/2）✓</td></tr>
 *   <tr><td>混入 {@code Options#getCameraType()}（是个方法 ✓）</td>
 *       <td><b>{@code GameSettings.thirdPersonView} 是公开字段</b>，<b>没有 getter 可注入</b> ✗
 *           → 改为「每 tick 直接把字段设回去」✓ 效果等价，还省掉一个 mixin ✓</td></tr>
 * </table>
 *
 * <p><b>⭐ 所以本类在 1.12.2 的分工是</b>：只保存「标记 + 目标视角」，
 * 由动作自己的客户端 tick 调用 {@link #apply()}
 * 把 {@code gameSettings.thirdPersonView} 设过去 ✓
 * 标记失效（动作结束）后就不再设置，玩家自然能切回第一人称 ✓</p>
 *
 * <p><b>⚠️ 注意</b>：这里是<b>每 tick 设置</b>而不是每帧 —— 玩家在同一 tick 内手动切视角会被覆盖回来，
 * 但下一 tick 标记消失后就能正常切换 ✓ 与上游「强制」的语义一致 ✓</p>
 */
@SideOnly(Side.CLIENT)
public final class RenderBehaviorEnforcer {

    private RenderBehaviorEnforcer() {
    }

    public interface Marker {
        boolean remain();
    }

    /** 1.12.2 的「第三人称背后」= {@code thirdPersonView == 1} ✓ */
    public static final int THIRD_PERSON_BACK = 1;

    @Nullable
    private static Marker cameraTypeMarker = null;
    @Nullable
    private static Supplier<Integer> cameraTypeSupplier = null;

    public static void setMarkerEnforceCameraType(Marker marker, Supplier<Integer> thirdPersonViewSupplier) {
        cameraTypeMarker = marker;
        cameraTypeSupplier = thirdPersonViewSupplier;
    }

    /**
     * 把被强制的视角写进 {@code gameSettings}（由动作的客户端 tick 调用）✓
     *
     * <p>标记失效时自动清除，之后不再干预 ✓</p>
     */
    public static void apply() {
        if (cameraTypeMarker == null || cameraTypeSupplier == null) return;
        if (!cameraTypeMarker.remain()) {
            cameraTypeMarker = null;
            cameraTypeSupplier = null;
            return;
        }
        Integer target = cameraTypeSupplier.get();
        if (target == null) return;
        net.minecraft.client.Minecraft.getMinecraft().gameSettings.thirdPersonView = target;
    }

    /** 供自检/调试：当前被强制的视角（没有则 {@code null}）✓ */
    @Nullable
    public static Integer getEnforcedCameraType() {
        if (cameraTypeMarker != null && cameraTypeMarker.remain()) {
            return cameraTypeSupplier == null ? null : cameraTypeSupplier.get();
        }
        cameraTypeMarker = null;
        cameraTypeSupplier = null;
        return null;
    }
}
