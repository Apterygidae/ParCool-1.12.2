package com.alrex.parcool.common.info;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.config.ParCoolConfig;

/**
 * 「客户端设置 × 服务端限制」的合成视图（1.12.2 版，1:1 移植）。
 *
 * <p>动作通过它取到「最终生效」的值：{@code can(action)} 是总开关，
 * 上限类数值取两侧的与/或。</p>
 */
public class ActionInfo {

    private ClientSetting clientSetting = ClientSetting.UNSYNCED_INSTANCE;
    private ServerLimitation serverLimitation = ServerLimitation.UNSYNCED_INSTANCE;

    public ActionInfo() {
    }

    public ClientSetting getClientSetting() {
        return clientSetting;
    }

    public void setClientSetting(ClientSetting clientSetting) {
        this.clientSetting = clientSetting;
    }

    public ServerLimitation getServerLimitation() {
        return serverLimitation;
    }

    public void setServerLimitation(ServerLimitation serverLimitation) {
        this.serverLimitation = serverLimitation;
    }

    /** 该动作此刻是否允许执行（客户端设置与服务端限制都放行）。 */
    public boolean can(Class<? extends Action> action) {
        return ParCool.isActive()
                && getClientSetting().getPossibilityOf(action)
                && getServerLimitation().isPermitted(action);
    }

    /** 体力消耗取两侧较大者（更严）。 */
    public int getStaminaConsumptionOf(Class<? extends Action> action) {
        return Math.max(
                getClientSetting().getStaminaConsumptionOf(action),
                getServerLimitation().getStaminaConsumptionOf(action)
        );
    }

    public int getStaminaRecoveryLimit() {
        return getServerLimitation().get(ParCoolConfig.Server.Integers.MaxStaminaRecovery);
    }

    public int getMaxStaminaLimit() {
        return getServerLimitation().get(ParCoolConfig.Server.Integers.MaxStaminaLimit);
    }

    public boolean isStaminaInfinite(boolean creativeOrSpectator) {
        if (getClientSetting().get(ParCoolConfig.Client.Booleans.InfiniteStamina) && isInfiniteStaminaPermitted())
            return true;
        return creativeOrSpectator && getClientSetting().get(ParCoolConfig.Client.Booleans.InfiniteStaminaWhenCreative);
    }

    public boolean isInfiniteStaminaPermitted() {
        return getServerLimitation().get(ParCoolConfig.Server.Booleans.AllowInfiniteStamina);
    }
}
