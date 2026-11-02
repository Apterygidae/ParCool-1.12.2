package com.alrex.parcool.server.command.impl;

import com.alrex.parcool.common.block.zipline.ZiplineHookTileEntity;
import com.alrex.parcool.common.block.zipline.ZiplineInfo;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import com.alrex.parcool.common.zipline.Zipline;
import com.alrex.parcool.common.zipline.ZiplineType;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.server.command.CommandTreeBase;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * {@code /parcool zipline ...} —— 滑索连接的管理命令。
 *
 * <pre>
 *   /parcool zipline set &lt;x1&gt; &lt;y1&gt; &lt;z1&gt; &lt;x2&gt; &lt;y2&gt; &lt;z2&gt; [&lt;nbt&gt;]
 * </pre>
 *
 * <h3>1.12.2 的四处替换</h3>
 * <table border="1">
 *   <tr><td>{@code BlockPosArgument.getOrLoadBlockPos(context, name)}</td>
 *       <td>{@code CommandBase.parseBlockPos(sender, args, index, false)}（自带相对坐标与 {@code ~} 支持）</td></tr>
 *   <tr><td>{@code NBTTagArgument.getNbtTag(context, name)}</td>
 *       <td>{@code JsonToNBT.getTagFromJson(args[6])}（1.12.2 的通用 NBT 文本解析器）</td></tr>
 *   <tr><td>{@code context.getSource().getLevel()}</td><td>{@code sender.getEntityWorld()}</td></tr>
 *   <tr><td>{@code MathHelper.square(x)}</td><td><b>1.12.2 没有</b> → {@code x * x}</td></tr>
 *   <tr><td>{@code BlockPos#toShortString()}</td><td><b>1.12.2 没有</b> → {@code toString()}</td></tr>
 * </table>
 *
 * <p>⚠️ 「钩子太远 / 太陡」这两条**只警告不返回**（会继续尝试连接）——
 * 这是上游的行为，照搬；只有「找不到钩子」才提前返回。</p>
 */
public class ZiplineCommand extends CommandTreeBase {

    public ZiplineCommand() {
        addSubcommand(new SetCommand());
    }

    @Override
    public String getName() {
        return "zipline";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "parcool.command.zipline.usage";
    }

    /** 与上游 {@code .requires(commandSource -> commandSource.hasPermission(2))} 对应。 */
    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    static void failure(ICommandSender sender, String key, Object... args) {
        // 上游 sendFailure：只回执行者，且不受 sendCommandFeedback 影响
        sender.sendMessage(new TextComponentTranslation(key, args));
    }

    /** 对应上游 {@code sendSuccess(msg, true)}：回执行者 + 广播给其他 OP。 */
    static void success(ICommand command, ICommandSender sender, String key, Object... args) {
        CommandBase.notifyCommandListener(sender, command, 0, key, args);
        CommandBase.notifyCommandListener(sender, command, 1, key, args);
    }

    public static class SetCommand extends CommandBase {

        @Override
        public String getName() {
            return "set";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "parcool.command.zipline.set.usage";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 6 && args.length != 7) {
                throw new WrongUsageException(getUsage(sender));
            }

            BlockPos hook1 = CommandBase.parseBlockPos(sender, args, 0, false);
            BlockPos hook2 = CommandBase.parseBlockPos(sender, args, 3, false);
            World level = sender.getEntityWorld();

            double dx = hook1.getX() - hook2.getX();
            double dz = hook1.getZ() - hook2.getZ();
            double horizontalDistSqr = dx * dx + dz * dz;
            if (horizontalDistSqr > Zipline.MAXIMUM_HORIZONTAL_DISTANCE * Zipline.MAXIMUM_HORIZONTAL_DISTANCE) {
                failure(sender, "parcool.command.message.hookTooFar");
            }
            double verticalDist = Math.abs(hook2.getY() - hook1.getY());
            if (verticalDist * MathHelper.fastInvSqrt(horizontalDistSqr) > 1. || verticalDist > Zipline.MAXIMUM_VERTICAL_DISTANCE) {
                failure(sender, "parcool.command.message.ziplineTooSteep");
            }

            // 强制加载目标区块，否则未加载区块里的挂钩 TileEntity 取不到
            //（对应上游 BlockPosArgument.getOrLoadBlockPos 的「or load」语义；
            //  1.12.2 没有 getChunkFromBlockCoords，用 World#getChunk(BlockPos)）
            level.getChunk(hook1);
            level.getChunk(hook2);

            TileEntity entity = level.getTileEntity(hook1);
            if (!(entity instanceof ZiplineHookTileEntity)) {
                failure(sender, "parcool.command.message.hookNotFound", hook1.toString());
                return;
            }
            TileEntity entity2 = level.getTileEntity(hook2);
            if (!(entity2 instanceof ZiplineHookTileEntity)) {
                failure(sender, "parcool.command.message.hookNotFound", hook2.toString());
                return;
            }

            ZiplineInfo info;
            if (args.length == 7) {
                NBTBase infoTag;
                try {
                    infoTag = JsonToNBT.getTagFromJson(args[6]);
                } catch (NBTException e) {
                    throw new WrongUsageException(getUsage(sender));
                }
                info = ZiplineInfo.load(infoTag);
            } else {
                info = new ZiplineInfo(ZiplineType.STANDARD, ZiplineRopeItem.DEFAULT_COLOR);
            }

            if (!((ZiplineHookTileEntity) entity).connectTo((ZiplineHookTileEntity) entity2, info)) {
                failure(sender, "parcool.command.message.failedConnectZipline");
                return;
            }
            success(this, sender, "parcool.command.message.success.setZipline", hook1.toString(), hook2.toString());
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                              @Nullable BlockPos targetPos) {
            if (args.length >= 1 && args.length <= 6) {
                return CommandBase.getTabCompletionCoordinate(args, args.length <= 3 ? 0 : 3, targetPos);
            }
            return Collections.emptyList();
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
            return false;
        }
    }
}
