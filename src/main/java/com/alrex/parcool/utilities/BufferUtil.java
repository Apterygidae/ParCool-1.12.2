package com.alrex.parcool.utilities;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.nio.ByteBuffer;

/**
 * {@link ByteBuffer} 的读写小工具（1.12.2 版，1:1 移植）。
 *
 * <p>用于动作状态的序列化 —— {@code ActionProcessor} 靠它把动作的
 * {@code saveSynchronizedState} 结果做「前后帧比对」，只把变化的部分发出去。</p>
 *
 * <p>1.12.2 差异只有改名：{@code Vector3d} → {@link Vec3d}；
 * <b>{@code Vector3i} 在 1.12.2 叫 {@link Vec3i}</b>；{@code vec.x()} → {@code vec.x}（1.12.2 是公开字段）。</p>
 */
public class BufferUtil {

    ByteBuffer buffer;

    private BufferUtil(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public static BufferUtil wrap(ByteBuffer byteBuffer) {
        return new BufferUtil(byteBuffer);
    }

    public BufferUtil putBoolean(boolean bool) {
        buffer.put(bool ? (byte) 1 : 0);
        return this;
    }

    public static boolean getBoolean(ByteBuffer buffer) {
        return buffer.get() != 0;
    }

    public BufferUtil putBlockPos(BlockPos pos) {
        return putVector3i(pos);
    }

    public BufferUtil putVector3i(Vec3i vec) {
        buffer.putInt(vec.getX()).putInt(vec.getY()).putInt(vec.getZ());
        return this;
    }

    public BufferUtil putVector3d(Vec3d vec) {
        buffer.putDouble(vec.x).putDouble(vec.y).putDouble(vec.z);
        return this;
    }

    public static BlockPos getBlockPos(ByteBuffer buffer) {
        return new BlockPos(buffer.getInt(), buffer.getInt(), buffer.getInt());
    }

    public static Vec3i getVector3i(ByteBuffer buffer) {
        return new Vec3i(buffer.getInt(), buffer.getInt(), buffer.getInt());
    }

    public static Vec3d getVector3d(ByteBuffer buffer) {
        return new Vec3d(buffer.getDouble(), buffer.getDouble(), buffer.getDouble());
    }

    public ByteBuffer unwrap() {
        return buffer;
    }

    /**
     * 比较两个 buffer 的内容是否相同（会消耗两者的 position —— 调用方随后要 clear，与上游一致）。
     */
    public static boolean haveSameContents(ByteBuffer buffer1, ByteBuffer buffer2) {
        if (buffer1.limit() != buffer2.limit()) {
            return false;
        }
        while (buffer1.hasRemaining() && buffer2.hasRemaining()) {
            if (buffer1.get() != buffer2.get()) {
                return false;
            }
        }
        return true;
    }
}
