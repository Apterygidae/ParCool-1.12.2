package com.alrex.parcool.api.unstable;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.server.limitation.Limitations;
import com.google.gson.stream.JsonWriter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 「限制」的命令侧门面（上游 {@code api/unstable/Limitation.java}，210 行）。
 *
 * <p>存在的理由：把「改值 → 生效 → 落盘」三件事绑成一个流式调用，
 * 命令代码里就不用重复 {@code setXxx(...); apply(); save();}。</p>
 * <p>两个实现：{@code NormalLimitation}（挂在某个玩家身上，会落盘）与
 * {@code GlobalLimitation}（服务端全局，只 apply 不落盘 —— 它的值来自配置文件）。</p>
 *
 * <p>1.12.2 差异：{@code ServerPlayerEntity}→{@code EntityPlayerMP}、
 * {@code player.getUUID()}→{@code getUniqueID()}。其余逐字保留。</p>
 */
public abstract class Limitation {
    public static Limitation get(EntityPlayerMP player, ID limitationID) {
        return new NormalLimitation(player, Limitations.createLimitationOf(player.getUniqueID(), limitationID.convert()));
    }

    public static Limitation getIndividual(EntityPlayerMP player) {
        return new NormalLimitation(player, Limitations.createLimitationOf(player.getUniqueID(), Limitations.INDIVIDUAL_ID));
    }

    public static Limitation getGlobal(MinecraftServer server) {
        return new GlobalLimitation(server);
    }

    public static class ID {
        public final String group;
        public final String name;

        public ID(String group, String name) {
            this.group = group;
            this.name = name;
        }

        public String getGroup() {
            return group;
        }

        public String getName() {
            return name;
        }

        com.alrex.parcool.server.limitation.Limitation.ID convert() {
            return new com.alrex.parcool.server.limitation.Limitation.ID(group, name);
        }

        @Override
        public String toString() {
            return this.group + ":" + this.name;
        }
    }

    public Limitation set(ParCoolConfig.Server.Booleans item, boolean value) {
        instance.set(item, value);
        return this;
    }

    public Limitation set(ParCoolConfig.Server.Integers item, int value) {
        instance.set(item, value);
        return this;
    }

    public Limitation set(ParCoolConfig.Server.Doubles item, double value) {
        instance.set(item, value);
        return this;
    }

    public Limitation setDefault() {
        instance.setAllDefault();
        return this;
    }

    public Limitation permit(Class<? extends Action> action, boolean permission) {
        instance.setPossibilityOf(action, permission);
        return this;
    }

    public Limitation setLeastStaminaConsumption(Class<? extends Action> action, int value) {
        instance.setLeastStaminaConsumption(action, value);
        return this;
    }

    public Limitation enable() {
        instance.setEnabled(true);
        return this;
    }

    public Limitation disable() {
        instance.setEnabled(false);
        return this;
    }

    public boolean get(ParCoolConfig.Server.Booleans item) {
        return instance.get(item);
    }

    public int get(ParCoolConfig.Server.Integers item) {
        return instance.get(item);
    }

    public double get(ParCoolConfig.Server.Doubles item) {
        return instance.get(item);
    }

    public boolean isPermitted(Class<? extends Action> action) {
        return instance.isPermitted(action);
    }

    public int getLeastStaminaConsumption(Class<? extends Action> action) {
        return instance.getLeastStaminaConsumption(action);
    }

    public boolean isEnabled() {
        return instance.isEnabled();
    }

    public abstract void apply();

    public abstract void save();

    public static boolean delete(ID limitationID) {
        return Limitations.delete(limitationID.convert());
    }

    private static class NormalLimitation extends Limitation {

        private final EntityPlayerMP player;

        private NormalLimitation(EntityPlayerMP player, com.alrex.parcool.server.limitation.Limitation instance) {
            super(instance);
            this.player = player;
        }

        @Override
        public void apply() {
            Limitations.updateOnlyLimitation(player);
        }

        @Override
        public void save() {
            Path filepath = Limitations.getActualFilePath(player.getUniqueID(), instance.getID());
            if (filepath == null) {
                ParCool.LOGGER.error(
                        "On Saving Limitation : Could not resolve file("
                                + player.getUniqueID()
                                + ","
                                + instance.getID().getGroup()
                                + ":"
                                + instance.getID().getName()
                                + ")"
                );
                return;
            }
            File limitationFile = filepath.toFile();
            if (!limitationFile.getParentFile().exists()) {
                limitationFile.getParentFile().mkdirs();
            }
            try (JsonWriter writer =
                         new JsonWriter(
                                 new OutputStreamWriter(
                                         new BufferedOutputStream(
                                                 Files.newOutputStream(limitationFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                                         ),
                                         StandardCharsets.UTF_8
                                 )
                         )
            ) {
                instance.saveTo(writer);
            } catch (IOException e) {
                ParCool.LOGGER.error(
                        "IOException during saving limitation : "
                                + e.getMessage()
                );
            }
        }
    }

    private static class GlobalLimitation extends Limitation {
        private final MinecraftServer server;

        private GlobalLimitation(MinecraftServer server) {
            super(Limitations.getGlobalLimitation());
            this.server = server;
        }

        @Override
        public void apply() {
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                Limitations.updateOnlyLimitation(player);
            }
        }

        @Override
        public void save() {
            // 全局限制的值来自配置文件（limitation_imposed + Server.*），没有自己的 json
        }
    }

    protected final com.alrex.parcool.server.limitation.Limitation instance;

    private Limitation(com.alrex.parcool.server.limitation.Limitation instance) {
        this.instance = instance;
    }
}
