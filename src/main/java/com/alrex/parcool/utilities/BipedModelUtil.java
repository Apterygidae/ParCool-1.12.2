package com.alrex.parcool.utilities;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;

/**
 * 模型相关的小工具（1.12.2 版）。
 *
 * <p>目前只有一个方法：判断「玩家当前是在用哪只手攻击」——
 * 动画器用它避免覆盖正在挥动的那只手臂。</p>
 *
 * <h3>1.12.2 的 API 替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code HandSide}</td><td>{@link EnumHandSide}</td></tr>
 *   <tr><td>{@code Hand}</td><td>{@link EnumHand}</td></tr>
 *   <tr><td>{@code player.getMainArm()}</td><td>{@code player.getPrimaryHand()}</td></tr>
 *   <tr><td>{@code player.swingingArm}</td><td>{@code player.swingingHand}（1.12.2 的字段名）</td></tr>
 *   <tr><td>{@code handside.getOpposite()}</td><td>{@code handside.opposite()}（方法名少一个 get）</td></tr>
 * </table>
 */
public class BipedModelUtil {

    private BipedModelUtil() {
    }

    public static EnumHandSide getAttackArm(EntityPlayer player) {
        EnumHandSide handside = player.getPrimaryHand();
        return player.swingingHand == EnumHand.MAIN_HAND ? handside : handside.opposite();
    }
}
