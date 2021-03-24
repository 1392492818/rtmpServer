package Handler;

import Util.Common;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.net.SocketAddress;

public class RtmpHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        byte[] data = (byte[]) msg;
        ctx.writeAndFlush(Unpooled.copiedBuffer(data));
    }
}
