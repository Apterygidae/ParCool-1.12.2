package com.alrex.parcool.server.limitation;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.info.ServerLimitation;
import com.alrex.parcool.common.network.SyncServerInfoMessage;
import com.alrex.parcool.utilities.ServerUtil;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * 「限制」的仓库：按玩家 UUID 缓存、按 文件夹/文件夹/UUID.json 落盘。
 *
 * <h3>与上游的差异</h3>
 * <ul>
 *   <li>{@code ServerPlayerEntity} → {@code EntityPlayerMP}；{@code player.getUUID()} → {@code getUniqueID()}</li>
 *   <li><b>{@code init} 改挂 {@code FMLServerStartingEvent}</b>（不是上游的
 *       {@code FMLServerAboutToStartEvent}）—— 1.12.2 在该事件时世界还没加载，
 *       {@code ServerUtil.getServerConfigPath} 取不到存档目录。详见 {@link ServerUtil}。</li>
 *   <li>{@code org.apache.commons.io.FileUtils.deleteDirectory} → 自写的递归删除
 *       （不引入额外的库依赖）</li>
 *   <li>{@code save} 不再从事件里取 server —— 1.12.2 的 {@code FMLServerStoppingEvent}
 *       <b>没有 getServer()</b>，改为复用 init 时缓存的路径</li>
 * </ul>
 */
public class Limitations {
    private static final Limitation.ID GLOBAL_ID = new Limitation.ID(ParCool.MOD_ID, "global");
    public static final Limitation.ID INDIVIDUAL_ID = new Limitation.ID(ParCool.MOD_ID, "individual");
    private static final SortedMap<UUID, SortedMap<Limitation.ID, Limitation>> Loaded = new TreeMap<>();
    private static final SortedSet<Limitation.ID> RegisteredID = new TreeSet<>();
    private static final Limitation GlobalLimitation = new Limitation(GLOBAL_ID);
    @Nullable
    private static Path LimitationFolderRootPath = null;

    private Limitations() {
    }

    private static SortedMap<Limitation.ID, Limitation> getLimitationMapOf(UUID playerID) {
        SortedMap<Limitation.ID, Limitation> map = Loaded.get(playerID);
        if (map == null) {
            map = load(playerID);
        }
        return map;
    }

    public static Collection<Limitation> getLimitationsOf(UUID playerID) {
        return getLimitationMapOf(playerID).values();
    }

    public static Limitation createLimitationOf(UUID playerID, Limitation.ID id) {
        Limitation limitation = getLimitationOf(playerID, id);
        if (limitation != null) return limitation;
        limitation = new Limitation(id);
        RegisteredID.add(id);
        getLimitationMapOf(playerID).put(id, limitation);
        return limitation;
    }

    public static boolean delete(Limitation.ID id) {
        if (LimitationFolderRootPath == null) return false;
        for (SortedMap<Limitation.ID, Limitation> limitationMap : Loaded.values()) {
            limitationMap.remove(id);
        }
        return deleteRecursively(getFolderPath(LimitationFolderRootPath, id).toFile());
    }

    public static Limitation getGlobalLimitation() {
        return GlobalLimitation;
    }

    public static Collection<Limitation.ID> getRegisteredIDs() {
        return RegisteredID;
    }

    @Nullable
    public static Limitation getLimitationOf(UUID playerID, Limitation.ID id) {
        if (id.equals(GLOBAL_ID)) {
            return GlobalLimitation;
        }
        return getLimitationMapOf(playerID).get(id);
    }

    /**
     * 重算该玩家的限制并下发（进服 / 收到客户端上报 / 管理员改完值 都会走到这）。
     *
     * <p>顺序与上游一致：先 {@code setServerLimitation}，再把限制（+体力）推给客户端 ——
     * 客户端拿到之后 {@code limitationIsNotSynced()} 才会变 false。</p>
     */
    public static void update(EntityPlayerMP player) {
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        parkourability.getActionInfo().setServerLimitation(ServerLimitation.get(player));
        IStamina stamina = IStamina.get(player);
        if (stamina == null) {
            SyncServerInfoMessage.sync(player);
        } else {
            SyncServerInfoMessage.syncWithStamina(player, stamina);
        }
    }

    /** 只重算不下发（用于「另外的玩家也受影响」的批量刷新，跟上游一致）。 */
    public static void updateOnlyLimitation(EntityPlayerMP player) {
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        parkourability.getActionInfo().setServerLimitation(ServerLimitation.get(player));
        SyncServerInfoMessage.sync(player);
    }

    public static SortedMap<Limitation.ID, Limitation> load(UUID playerID) {
        if (LimitationFolderRootPath == null) {
            throw new IllegalStateException(
                    "When loading Limitation Player:" +
                            playerID.toString() +
                            ", Initialization is not completed yet"
            );
        }
        File limitationFolder = LimitationFolderRootPath.toFile();
        File[] directories = limitationFolder.listFiles(File::isDirectory);
        if (directories == null) {
            // ⚠️ 上游这里 return null → 调用方（ServerLimitation.get）会 NPE。
            //    目录还没建出来（首次启动、或 mkdirs 失败）是完全可能的情况，
            //    所以这里改成「返回空表」：语义等价（本来就没数据），但不会炸。
            ParCool.LOGGER.error("Cannot get Limitation folders (path={}), treated as empty",
                    limitationFolder.getAbsolutePath());
            directories = new File[0];
        }
        SortedMap<Limitation.ID, Limitation> playerData = Loaded.computeIfAbsent(playerID, k -> new TreeMap<>());
        for (File dir : directories) {
            File[] limitationGroups = dir.listFiles(File::isDirectory);
            if (limitationGroups == null) {
                ParCool.LOGGER.error("Cannot get Limitation folders of '" + dir.getName() + "'");
                continue;
            }
            for (File limitationGroup : limitationGroups) {
                File[] limitationFiles = limitationGroup.listFiles((file) -> file.isFile() && file.canRead() && file.getName().endsWith(".json"));
                if (limitationFiles == null) {
                    ParCool.LOGGER.error("Cannot get Limitation files of '" + dir.getName() + "'");
                    continue;
                }
                Limitation.ID limitationID = new Limitation.ID(dir.getName(), limitationGroup.getName());
                RegisteredID.add(limitationID);
                for (File limitationFile : limitationFiles) {
                    String limitationFilename = limitationFile.getName();
                    String uuidString = limitationFilename.substring(0, limitationFilename.length() - ".json".length());
                    UUID playerUUID;
                    try {
                        playerUUID = UUID.fromString(uuidString);
                    } catch (IllegalArgumentException e) {
                        // 1.12.2 落地时多一层保护：文件名不是 UUID 就跳过，别让整个 load 崩掉
                        ParCool.LOGGER.warn("Skipped malformed limitation file: " + limitationFile.getAbsolutePath());
                        continue;
                    }
                    if (!playerUUID.equals(playerID)) {
                        continue;
                    }
                    try (JsonReader reader =
                                 new JsonReader(
                                         new InputStreamReader(
                                                 new BufferedInputStream(
                                                         new FileInputStream(limitationFile)
                                                 ),
                                                 StandardCharsets.UTF_8
                                         )
                                 )
                    ) {
                        Limitation limitation = new Limitation(limitationID);
                        limitation.loadFrom(reader);
                        playerData.put(limitation.getID(), limitation);
                    } catch (FileNotFoundException e) {
                        ParCool.LOGGER.error("Could not read '" + limitationFile.getAbsolutePath() + "', skipped.");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        ParCool.LOGGER.info("Limitation of " + playerID + " was loaded");
        return playerData;
    }

    public static void unload(UUID playerID) {
        if (LimitationFolderRootPath == null) {
            // 上游这里抛 IllegalStateException；1.12.2 下「玩家登出」比服务端初始化更容易被打断
            // （例如世界卸载途中踢人）→ 降级为告警，不让登出流程炸掉。
            ParCool.LOGGER.warn("Limitation folder is not initialized yet. Skipped unloading of " + playerID);
            return;
        }
        SortedMap<Limitation.ID, Limitation> map = Loaded.remove(playerID);
        if (map == null) {
            ParCool.LOGGER.warn("Limitation entry is not loaded for UUID:" + playerID + ". Skipped.");
            return;
        }
        for (Limitation limitation : map.values()) {
            File limitationFile = getActualFilePath(playerID, limitation.getID()).toFile();
            if (!limitationFile.getParentFile().exists()) {
                limitationFile.getParentFile().mkdirs();
            }
            writeLimitation(limitation, limitationFile);
        }
        ParCool.LOGGER.info("Limitation of " + playerID + " was unloaded");
    }

    /**
     * 服务端启动时初始化。
     *
     * <p>⚠️ 挂在 {@code FMLServerStartingEvent}（世界已加载），不是上游的
     * {@code FMLServerAboutToStartEvent}。见 {@link ServerUtil} 的时机说明。</p>
     */
    public static void init(FMLServerStartingEvent event) {
        init(ServerUtil.getServerConfigPath(event.getServer()));
    }

    public static void init(MinecraftServer server) {
        init(ServerUtil.getServerConfigPath(server));
    }

    private static void init(Path serverConfigPath) {
        GlobalLimitation.readFromServerConfig();
        LimitationFolderRootPath = serverConfigPath.resolve("parcool").resolve("limitations");
        File limitationFolder = LimitationFolderRootPath.toFile();
        if (!limitationFolder.exists() && !limitationFolder.mkdirs()) {
            ParCool.LOGGER.error("Could not create limitation folder: " + limitationFolder.getAbsolutePath());
            return;
        }
        ParCool.LOGGER.info("Limitation folder: " + limitationFolder.getAbsolutePath());
    }

    public static void save(FMLServerStoppingEvent event) {
        save();
    }

    public static void save() {
        if (LimitationFolderRootPath == null) return;
        for (Map.Entry<UUID, SortedMap<Limitation.ID, Limitation>> limitationEntry : Loaded.entrySet()) {
            UUID playerID = limitationEntry.getKey();
            for (Limitation limitation : limitationEntry.getValue().values()) {
                File limitationFile = getFolderPath(LimitationFolderRootPath, limitation.getID())
                        .resolve(playerID.toString() + ".json").toFile();
                writeLimitation(limitation, limitationFile);
            }
        }
    }

    private static void writeLimitation(Limitation limitation, File limitationFile) {
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
            limitation.saveTo(writer);
        } catch (IOException e) {
            ParCool.LOGGER.error("IOException during saving limitation : " + e.getMessage());
        }
    }

    public static Path getFolderPath(Path limitationRootPath, Limitation.ID id) {
        return limitationRootPath
                .resolve(id.getGroup())
                .resolve(id.getName());
    }

    @Nullable
    public static Path getActualFilePath(UUID playerID, Limitation.ID id) {
        if (LimitationFolderRootPath == null) return null;
        Path folderPath = getFolderPath(LimitationFolderRootPath, id);
        return folderPath.resolve(playerID.toString() + ".json");
    }

    /** 取代 {@code FileUtils.deleteDirectory} —— 只为少一个库依赖。 */
    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!deleteRecursively(child)) return false;
            }
        }
        return file.delete();
    }
}
