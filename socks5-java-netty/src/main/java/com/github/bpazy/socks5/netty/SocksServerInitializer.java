package com.github.bpazy.socks5.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * @author ziyuan
 * created on 2019/8/5
 */
public class SocksServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(
                new LoggingHandler(LogLevel.DEBUG),
                new SocksPortUnificationServerHandler(),
                SocksServerHandler.INSTANCE);
    }
}
