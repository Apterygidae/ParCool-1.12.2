package com.alrex.parcool.api.unstable.action;

import com.alrex.parcool.common.action.Action;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * 动作生命周期事件族（1.12.2 版，1:1 移植）。
 *
 * <p>第三方 mod 监听这些事件即可介入 ParCool 的动作：
 * {@code TryToStart} / {@code TryToContinue} 标了 {@link Cancelable}，
 * 取消（{@code event.setCanceled(true)}）就能阻止动作开始或继续。</p>
 *
 * <p>1.12.2 差异：事件基类 {@code net.minecraftforge.eventbus.api.Event} →
 * {@code net.minecraftforge.fml.common.eventhandler.Event}；{@code @Cancelable} 同理换包；
 * {@code PlayerEntity} → {@link EntityPlayer}。</p>
 *
 * <p>{@code Event.isCancelable()} 在 1.12.2 的默认实现是「看类上有没有 {@code @Cancelable} 注解」——
 * 所以上游那几个显式覆写 {@code isCancelable()} 的（如 {@code TryToStart}）在这里依然有意义：
 * 它们<b>故意不加注解但让事件可取消</b>。原样保留。</p>
 *
 * <p>标记 {@code @Deprecated} 的是上游留的旧名，保留以免破坏第三方 mod 的编译。</p>
 */
public class ParCoolActionEvent extends Event {

    private final EntityPlayer player;
    private final Action action;

    public EntityPlayer getPlayer() {
        return player;
    }

    public Action getAction() {
        return action;
    }

    public ParCoolActionEvent(EntityPlayer player, Action action) {
        this.player = player;
        this.action = action;
    }

    // ── 旧名（保留） ────────────────────────────────────────────────────────

    @Deprecated
    @Cancelable
    public static class TryToStartEvent extends ParCoolActionEvent {
        @Override
        public boolean isCancelable() {
            return true;
        }

        public TryToStartEvent(EntityPlayer player, Action action) {
            super(player, action);
        }
    }

    @Deprecated
    @Cancelable
    public static class TryToContinueEvent extends ParCoolActionEvent {
        @Override
        public boolean isCancelable() {
            return true;
        }

        public TryToContinueEvent(EntityPlayer player, Action action) {
            super(player, action);
        }
    }

    @Deprecated
    public static class StartEvent extends ParCoolActionEvent {
        public StartEvent(EntityPlayer player, Action action) {
            super(player, action);
        }
    }

    @Deprecated
    public static class StopEvent extends ParCoolActionEvent {
        public StopEvent(EntityPlayer player, Action action) {
            super(player, action);
        }
    }

    // ── 现名 ────────────────────────────────────────────────────────────────

    public static class TryToStart extends ParCoolActionEvent {
        @Override
        public boolean isCancelable() {
            return true;
        }

        public TryToStart(EntityPlayer player, Action action) {
            super(player, action);
        }
    }

    @Cancelable
    public static class TryToContinue extends ParCoolActionEvent {
        @Override
        public boolean isCancelable() {
            return true;
        }

        public TryToContinue(EntityPlayer player, Action action) {
            super(player, action);
        }
    }

    public static class Start extends ParCoolActionEvent {
        private Start(EntityPlayer player, Action action) {
            super(player, action);
        }

        public static class Pre extends Start {
            public Pre(EntityPlayer player, Action action) {
                super(player, action);
            }
        }

        public static class Post extends Start {
            public Post(EntityPlayer player, Action action) {
                super(player, action);
            }
        }
    }

    public static class Finish extends ParCoolActionEvent {
        private Finish(EntityPlayer player, Action action) {
            super(player, action);
        }

        public static class Pre extends Finish {
            public Pre(EntityPlayer player, Action action) {
                super(player, action);
            }
        }

        public static class Post extends Finish {
            public Post(EntityPlayer player, Action action) {
                super(player, action);
            }
        }
    }

    public static class Tick extends ParCoolActionEvent {
        private Tick(EntityPlayer player, Action action) {
            super(player, action);
        }

        public static class Pre extends Tick {
            public Pre(EntityPlayer player, Action action) {
                super(player, action);
            }
        }

        public static class Post extends Tick {
            public Post(EntityPlayer player, Action action) {
                super(player, action);
            }
        }
    }
}
