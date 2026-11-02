package com.alrex.parcool.common.capability.stamina;

import com.alrex.parcool.api.Attributes;
import com.alrex.parcool.api.Effects;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nullable;

/**
 * 本地玩家（客户端）的体力实现（1.12.2 版）。
 *
 * <p>三处「服务端上限」已全部接回 {@code ActionInfo}（P4-A 完成后即可用）：
 * 体力上限、每次恢复量上限、无限体力判定。与上游逻辑一致。</p>
 */
public class ParCoolStamina implements IStamina {

    public ParCoolStamina(@Nullable EntityPlayer player) {
        this.player = player;
        // 上游：本地玩家初始给 Integer.MAX_VALUE，随后由 set() 夹到实际上限。
        // 本类只会被 StaminaProvider 用于「客户端的本地玩家」（服务端侧用 OtherStamina），
        // 所以这里不需要再判 isLocalPlayer()。
        if (player != null) {
            set(Integer.MAX_VALUE);
        }
    }

    public ParCoolStamina() {
        this.player = null;
    }

    @Nullable
    private final EntityPlayer player;

    private int stamina = 0;
    private int staminaOld = 0;
    private boolean exhausted = false;
    private int recoverCoolTime = 0;

    @Override
    public int getActualMaxStamina() {
        if (player == null) return 1;
        IAttributeInstance attr = player.getEntityAttribute(Attributes.MAX_STAMINA);
        if (attr == null) return 1;
        int attributeMax = (int) attr.getAttributeValue();

        // ⚠️ 本类是在 AttachCapabilitiesEvent 里被构造的，那一刻 Entity.capabilities
        // 还没赋值，getCapability 会返回 null。所以这里必须容忍 Parkourability 暂时缺席，
        // 回落到「属性值」—— 否则构造期的 set(Integer.MAX_VALUE) 会被夹成 1。
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return attributeMax;

        // 正常路径：属性值与服务端上限取较小值
        return Math.min(attributeMax, parkourability.getActionInfo().getMaxStaminaLimit());
    }

    @Override
    public int get() {
        return stamina;
    }

    @Override
    public int getOldValue() {
        return staminaOld;
    }

    @Override
    public void consume(int value) {
        if (player == null) return;
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        if (exhausted
                || parkourability.getActionInfo().isStaminaInfinite(player.isSpectator() || player.isCreative())
                || player.isPotionActive(Effects.INEXHAUSTIBLE.get())
        ) return;
        recoverCoolTime = 30;
        set(stamina - value);
        if (stamina == 0) {
            exhausted = true;
        }
    }

    @Override
    public void recover(int value) {
        set(stamina + value);
        if (stamina == getActualMaxStamina()) {
            exhausted = false;
        }
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
        if (recoverCoolTime > 0) recoverCoolTime--;
        if (recoverCoolTime > 0) return;
        if (player == null) return;
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;

        IAttributeInstance attr = player.getEntityAttribute(Attributes.STAMINA_RECOVERY);
        if (attr == null) return;
        // 每次恢复量同样受服务端上限约束
        int recovery = Math.min((int) attr.getAttributeValue(),
                parkourability.getActionInfo().getStaminaRecoveryLimit());

        if (player.onGround) {
            recover(recovery);
        } else {
            recover(recovery / 5);
        }
    }

    @Override
    public void updateOldValue() {
        staminaOld = stamina;
    }

    @Override
    public void set(int value) {
        stamina = Math.min(value, getActualMaxStamina());
        if (stamina <= 0) {
            stamina = 0;
        }
    }
}
