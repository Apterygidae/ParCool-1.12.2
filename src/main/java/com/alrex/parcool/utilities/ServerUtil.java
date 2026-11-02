package com.alrex.parcool.utilities;

import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.nio.file.Path;

/**
 * 服务端路径工具（1.12.2 版）。
 *
 * <p>上游用 {@code server.getWorldPath(new FolderName("serverconfig"))} —— 那是
 * 1.13+ 才有的「服务器配置目录」概念。**1.12.2 既没有 serverconfig，也没有
 * 「世界加载前就能取到路径」的 API** → 本类改用<b>世界存档目录</b>，
 * 语义最接近（限制数据跟随存档走，而不是跟随实例）。</p>
 *
 * <h3>⚠️ 调用时机是硬约束</h3>
 * <p>必须晚于世界加载。反编译源码里 {@code DedicatedServer} 的顺序是：</p>
 * <pre>
 *   :271  handleServerAboutToStart(server)   // FMLServerAboutToStartEvent ← 此时还没有世界 ✗
 *   :273  loadAllWorlds(...)                 // 世界在这里加载
 *   :310  handleServerStarting(server)       // FMLServerStartingEvent     ← 可用 ✓
 * </pre>
 * <p>{@code IntegratedServer} 是同构的（{@code :159} / {@code :162}）。</p>
 * <p>→ <b>{@code Limitations.init} 挂在 {@code FMLServerStartingEvent} 上，不挂
 * {@code FMLServerAboutToStartEvent}</b>（上游挂的是后者，因为 1.16 的
 * {@code getWorldPath} 不依赖世界对象 —— 这是移植时故意的分歧）。</p>
 */
public class ServerUtil {

    private ServerUtil() {
    }

    /** 返回世界存档根目录（例如 {@code <服务器目录>/world}）。 */
    public static Path getServerConfigPath(MinecraftServer server) {
        File worldDir = server.getEntityWorld().getSaveHandler().getWorldDirectory();
        if (worldDir == null) {
            // 理论上不会发生；真发生了也别 NPE 掉整个服务器启动
            worldDir = new File(".");
        }
        return worldDir.toPath();
    }
}
