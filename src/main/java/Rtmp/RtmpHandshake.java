package Rtmp;

import Util.Common;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class RtmpHandshake {
    public byte[] c1Data = new byte[1536];
    private boolean isVersion = false; //是否认证了版本信息
    private boolean isC1 = false; //是否认证了 c1
    private boolean isC2 = false; //是否认证了 c2
    public  boolean isHandshake = false; //是否已经握手成功
    private List<Byte> S1 = new ArrayList<Byte>();
    private byte[] zero = {0x00, 0x00, 0x00, 0x00};

    /**
     * 解析握手数据
     * @param ctx
     */

//     握手以客户端发送 C0 和 C1 块开始。
//     客户端必须等待接收到 S1 才能发送 C2。
//     客户端必须等待接收到 S2 才能发送任何其他数据。
//     服务器端必须等待接收到 C0 才能发送 S0 和 S1，
//     服务器端必须等待接收到 C1 才能发送 S2。
//     服务器端必须等待接收到 C2 才能发送任何其他数据。

// c1 s1 数据格式  4 byte time 4 byte zero   1536 - 8 random byte

    public void handShake(ChannelHandlerContext ctx, ByteBuf byteBuf) {
        if (!isVersion) { // 认证 version
            byte[] flags = new byte[1];
            byteBuf.readBytes(flags);
            if (flags[0] != Common.C0) {
                ctx.close();
                return;
            } else {
                isVersion = true;
            }
        }

        if (!isC1) {
            if (byteBuf.readableBytes() >= Common.C1_LENGTH) {
                this.c1Data = new byte[Common.C1_LENGTH];
                byteBuf.readBytes(this.c1Data); //读取c1 内容
                int time = (int) (new Date().getTime() / 1000);
                byte[] timeByte = Common.intToByte(time);
                ctx.writeAndFlush(Unpooled.copiedBuffer(new byte[]{Common.S0}));
                for (byte i : timeByte) {
                    S1.add(i);
                }
                for (byte i : zero) {
                    S1.add(i);
                }
                for (int i = 0; i < Common.RANDOM_LENGTH; i++) {
                    Random random = new Random();
                    S1.add((byte) random.nextInt(9));
                }
                ctx.writeAndFlush(Unpooled.copiedBuffer(Common.conversionByteArray(S1)));
                isC1 = true;

                int s2time = (int) (new Date().getTime() / 1000); //设置 s2 time
                byte[] timeByte2 = Common.intToByte(s2time);
                int index = 0;
                for (int i = 4; i < 8; i++) {
                    this.c1Data[i] = timeByte2[index];
                    index++;
                }
                ctx.writeAndFlush(Unpooled.copiedBuffer(this.c1Data));
            }
        }
        if (!isC2) {
            if (byteBuf.readableBytes() >= Common.C1_LENGTH) {
                byte[] s2 = new byte[Common.C1_LENGTH];
                byteBuf.readBytes(s2); //读取c1 内容
                isHandshake = true;
            }
        }
    }
}
