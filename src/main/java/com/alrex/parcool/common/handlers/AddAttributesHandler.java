package com.alrex.parcool.common.handlers;

import com.alrex.parcool.api.Attributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 把 ParCool 的自定义属性挂到玩家身上（1.12.2 版）。
 *
 * <p>对应 1.16.5 的 {@code AddAttributesHandler}，但<b>机制完全不同</b>：</p>
 * <ul>
 *   <li>1.16.5：{@code EntityAttributeModificationEvent} + {@code event.add(EntityType.PLAYER, attr)}
 *       —— 该事件在 1.12.2 不存在</li>
 *   <li>1.12.2：在 {@code EntityConstructing} 里直接往玩家的 {@code AttributeMap} 注册</li>
 * </ul>
 *
 * <p><b>为什么 EntityConstructing 的时机是对的</b>（已在反编译源码中核对调用顺序）：</p>
 * <ol>
 *   <li>{@code Entity.<init>} 末尾（{@code Entity.java:269}）post {@code EntityConstructing}</li>
 *   <li>{@code EntityLivingBase.<init>} 第 208 行才调用 {@code applyEntityAttributes()}</li>
 * </ol>
 * <p>即本处理器先跑，随后 {@code applyEntityAttributes} 注册原版属性 —— 它只做 map put，
 * 不会清空已有条目，所以我们注册的属性会保留。</p>
 *
 * <p><b>为什么不会 NPE</b>：{@code EntityLivingBase.getAttributeMap()} 是懒初始化的
 * （反编译源码 {@code EntityLivingBase.java:1786-1794} 里先判 null 再 new AttributeMap），
 * 所以在 {@code Entity.<init>} 阶段调用它会即时创建出 map。</p>
 */
public class AddAttributesHandler {

    @SubscribeEvent
    public static void onEntityConstructing(EntityEvent.EntityConstructing event) {
        if (!(event.getEntity() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntity();

        attach(player, Attributes.MAX_STAMINA);
        attach(player, Attributes.STAMINA_RECOVERY);
    }

    private static void attach(EntityPlayer player, IAttribute attribute) {
        // 玩家可能被反复构造（换维度、重生），必须幂等
        if (player.getEntityAttribute(attribute) != null) return;
        player.getAttributeMap().registerAttribute(attribute);
    }
}
