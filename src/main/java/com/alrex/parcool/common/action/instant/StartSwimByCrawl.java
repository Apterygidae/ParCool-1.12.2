package com.alrex.parcool.common.action.instant;

import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.InstantAction;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.platform.ParCoolPlatform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * 「按爬行键 → 进入游泳」（1.12.2 版，**第二个真实现的动作**）。
 *
 * <p>对应上游的 {@code StartSwimByCrawl}。它的作用是补齐一个行为差异：</p>
 * <ul>
 *   <li>{@code Crawl.canStart} 里有 {@code !isInWaterOrBubble} —— <b>在水里不能爬</b>
 *       （上游实测确认这是设计本意：一格水中按 C 会进入游泳而非爬行）</li>
 *   <li>那「在水里按 C」该发生什么？就是本类 —— 让玩家<b>开始游泳</b></li>
 * </ul>
 *
 * <p><b>两者条件天然互斥</b>：水里只有本类能启动，陆地上只有 {@code Crawl} 能启动
 * → 同一个 C 键在不同环境下有正确且不冲突的行为 ✓</p>
 *
 * <h3>1.12.2 的 API 替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.isInWaterOrBubble()}</td><td>{@link ParCoolPlatform#isInWaterOrBubble}</td></tr>
 *   <tr><td>{@code player.isSwimming()}</td>
 *       <td>{@link ParCoolPlatform#isSwimming} —— Aqua 直通原版的「正在水中游动」标志</td></tr>
 *   <tr><td>{@code player.setSwimming(true)}</td><td>{@link ParCoolPlatform#setSwimming}</td></tr>
 *   <tr><td>{@code player.setSprinting(true)}</td><td>同名 ✓</td></tr>
 * </table>
 *
 * <p>上游的 {@code canStart} 没标 {@code @OnlyIn}（因为它读了客户端的 {@code KeyRecorder}）——
 * 1.12.2 必须补 {@code @SideOnly(Side.CLIENT)}，否则专用服务端会去碰被剥掉的客户端类。</p>
 */
public class StartSwimByCrawl extends InstantAction {

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        return ParCoolPlatform.isInWaterOrBubble(player)
                && !ParCoolPlatform.isSwimming(player)
                && KeyRecorder.keyCrawlState.isPressed();
    }

    /** 双端都跑：服务端负责权威状态，客户端马上看到表现。 */
    @Override
    public void onStart(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        player.setSprinting(true);
        ParCoolPlatform.setSwimming(player, true);
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.None;
    }
}
