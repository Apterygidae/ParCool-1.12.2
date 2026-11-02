package com.alrex.parcool.common.capability.stamina;

import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * 「别人的体力」—— 服务端侧的权威值容器（1.12.2 版）。
 *
 * <p>对应上游 {@code OtherStamina}。核心语义：<b>值不自己算，只接受网络同步</b>
 * （由 {@code SyncStaminaToClientMessage} 从服务端推下来），因此
 * {@code consume}/{@code recover}/{@code tick} 几乎都是空实现。</p>
 *
 * <h3>与 1.16.5 的 API 差异</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code Attributes.MOVEMENT_SPEED}</td><td>{@link SharedMonsterAttributes#MOVEMENT_SPEED}</td></tr>
 *   <tr><td>{@code ModifiableAttributeInstance}</td><td>{@link IAttributeInstance}</td></tr>
 *   <tr><td>{@code AttributeModifier(uuid, name, amount, Operation)}</td>
 *       <td>{@code AttributeModifier(uuid, name, amount, int operation)} —— 第 4 参是 {@code int}</td></tr>
 *   <tr><td>{@code attr.addTransientModifier(...)}</td><td>{@code attr.applyModifier(...)}</td></tr>
 * </table>
 *
 * <p>{@code tick()} 里的「是否施加力竭惩罚」已接回
 * {@code ClientInfo.get(EnableStaminaExhaustionPenalty)} —— 那是「客户端设置经网络同步到服务端」的链路，
 * 依赖 P2-B 的 {@code SyncClientInformationMessage} 真正开始推送后才会有实际值。</p>
 */
public class OtherStamina implements IStamina {

    private static final String EXHAUSTED_SPEED_MODIFIER_NAME = "parcool.modifier.exhausted.speed";
    /** 上游用 {@code UUID.randomUUID()}（每次启动都不同）。这里保持同样做法。 */
    private static final UUID EXHAUSTED_SPEED_MODIFIER_UUID = UUID.randomUUID();

    private final EntityPlayer player;

    private int max = 0;
    private int value = 0;
    private boolean exhausted;
    private boolean imposingPenalty;

    public OtherStamina(EntityPlayer player) {
        this.player = player;
    }

    /** 由服务端下发（{@code SyncStaminaToClientMessage}）时设置。 */
    public void setMax(int value) {
        max = value;
    }

    @Override
    public int getActualMaxStamina() {
        return max;
    }

    @Override
    public int get() {
        return value;
    }

    @Override
    public int getOldValue() {
        return value;
    }

    @Override
    public void consume(int value) {
        // 权威值由服务端推，客户端不自行扣减
    }

    @Override
    public void recover(int value) {
        // 同上
    }

    @Override
    public boolean isExhausted() {
        return exhausted;
    }

    @Override
    public void setExhaustion(boolean value) {
        exhausted = value;
    }

    @Override
    public void tick() {
        if (!(player instanceof EntityPlayerMP)) return;

        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (attr == null) return;

        // 每次重算前先移除旧修饰符，避免叠加
        AttributeModifier old = attr.getModifier(EXHAUSTED_SPEED_MODIFIER_UUID);
        if (old != null) {
            attr.removeModifier(old);
        }

        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;

        // 是否施加力竭惩罚，由「客户端设置」经网络同步到服务端后决定
        if (isImposingExhaustionPenalty()
                && parkourability.getClientInfo().get(ParCoolConfig.Client.Booleans.EnableStaminaExhaustionPenalty)) {
            player.setSprinting(false);
            attr.applyModifier(new AttributeModifier(
                    EXHAUSTED_SPEED_MODIFIER_UUID,
                    EXHAUSTED_SPEED_MODIFIER_NAME,
                    -0.05D,
                    0   // 0 = ADDITION（1.12.2 用 int，不是 AttributeModifier.Operation 枚举）
            ));
        }
    }

    @Override
    public boolean isImposingExhaustionPenalty() {
        return imposingPenalty;
    }

    public void setImposingPenalty(boolean imposingPenalty) {
        this.imposingPenalty = imposingPenalty;
    }

    @Override
    public void set(int value) {
        this.value = value;
    }
}
