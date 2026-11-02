package com.alrex.parcool.proxy;

/**
 * 专用服务端代理。P0 阶段没有服务端专属逻辑，先留空壳。
 *
 * <p>注意：1.12.2 的 {@code @SideOnly} 不会被 Forge 自动剥离，
 * 服务端代理里<b>绝不能</b>引用任何客户端类，否则专用服务端会直接崩溃。</p>
 */
public class ServerProxy extends CommonProxy {
}
