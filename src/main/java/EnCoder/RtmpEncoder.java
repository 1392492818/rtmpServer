package EnCoder;

import Util.Common;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.MessageToByteEncoder;
import javafx.scene.SubScene;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

public class RtmpEncoder extends OneToOneEncoder {

    protected Object encode(ChannelHandlerContext channelHandlerContext, Channel channel, Object o) throws Exception {
        System.out.println("进来了");
        return null;
    }
}
