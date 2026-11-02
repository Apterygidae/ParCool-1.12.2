package com.alrex.parcool.common.capability;

import com.alrex.parcool.common.capability.capabilities.Capabilities;
import com.alrex.parcool.common.capability.stamina.HungerStamina;
import com.alrex.parcool.common.capability.stamina.ParCoolStamina;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 体力 Capability（1.12.2 版）。1:1 移植自上游同名接口。
 *
 * <h3>与 1.16.5 的差异</h3>
 * <ul>
 *   <li>取值不再经 {@code LazyOptional}：1.12.2 的 {@code getCapability} 直接返回实例或 {@code null}
 *       （多一个 {@code EnumFacing} 参数，传 {@code null} 即可）</li>
 *   <li>{@code PlayerEntity} → {@link EntityPlayer}、{@code ServerPlayerEntity} → {@link EntityPlayerMP}</li>
 * </ul>
 */
public interface IStamina {

    enum Type {
        Default(ParCoolStamina.class, ParCoolStamina::new, null),
        Hunger(HungerStamina.class, HungerStamina::new, HungerStamina::consumeOnServer);

        Type(Class<? extends IStamina> clazz,
             Function<EntityPlayer, IStamina> constructor,
             @Nullable BiConsumer<EntityPlayerMP, Integer> serverStaminaHandler) {
            this.constructor = constructor;
            this.clazz = clazz;
            this.serverStaminaHandler = serverStaminaHandler;
        }

        private final Function<EntityPlayer, IStamina> constructor;
        private final Class<? extends IStamina> clazz;
        @Nullable
        private final BiConsumer<EntityPlayerMP, Integer> serverStaminaHandler;

        public IStamina newInstance(EntityPlayer player) {
            return constructor.apply(player);
        }

        public void handleConsumeOnServer(EntityPlayerMP player, int value) {
            if (this.serverStaminaHandler != null) {
                serverStaminaHandler.accept(player, value);
            }
        }

        @Nullable
        public static Type getFromInstance(IStamina stamina) {
            for (Type type : Type.values()) {
                if (type.clazz.isAssignableFrom(stamina.getClass())) {
                    return type;
                }
            }
            return null;
        }
    }

    /** 取玩家的体力实例；没有 capability 时返回 null。 */
    @Nullable
    static IStamina get(EntityPlayer player) {
        return player.getCapability(Capabilities.STAMINA_CAPABILITY, null);
    }

    int getActualMaxStamina();

    int get();

    int getOldValue();

    void consume(int value);

    void recover(int value);

    boolean isExhausted();

    void setExhaustion(boolean value);

    void tick();

    void set(int value);

    default void updateOldValue() {
    }

    default boolean isImposingExhaustionPenalty() {
        return isExhausted();
    }

    default boolean wantToConsumeOnServer() {
        return false;
    }

    default int getRequestedValueConsumedOnServer() {
        return 0;
    }
}
