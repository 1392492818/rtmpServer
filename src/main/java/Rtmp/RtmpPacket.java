package Rtmp;

import Decoder.AudioStreamDecoder;
import Util.Common;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

//  +--------------+     +-------------+----------------+-------------------+
//          | Chunk Header |  =  | Basic header| Message Header |Extended Timestamp |
//          +--------------+     +-------------+----------------+-------------------+
public class RtmpPacket {

    @Setter
    @Getter
    protected int csid;

    @Setter
    @Getter
    protected int head_len; //头部长度

    @Setter
    @Getter
    protected int timestamp = 0;

    @Setter
    @Getter
    private int videoTimestamp;

    @Setter
    @Getter
    private int audioTimestamp;

    @Setter
    @Getter
    protected int messageLength; //消息数据长度

    @Setter
    @Getter
    protected RtmpMessage messageType; //消息类型

    @Setter
    @Getter
    protected int streamId; //流id

    @Setter
    @Getter
    protected int extTimestamp; //额外时间戳

    @Setter
    @Getter
    private boolean lackMessage = false;

    @Getter
    private byte[] messageData;

    private int fmt; //basic head fmt

    @Setter
    private int chunkLength = Common.DEFAULT_CHUNK_MESSAGE_LENGTH;

    @Setter
    @Getter
    private ByteBuf byteBuf;

    private ByteBuf copyByteBuf;

    //是否数据分包
    @Setter
    @Getter
    private boolean isSubpackage = false;

    private int readIndex = 0;

    File txt;

    private AudioStreamDecoder audioStreamDecoder = new AudioStreamDecoder();

    //对应的 chunk head length 长度
    private int[] chunk_message_length = {11, 7, 3, 0};

    // Basic head 类型
//    private enum BasicHead {
//        FULL,   // Basic header
//        NO_MSG_STREAM_ID,
//        TIMESTAMP, // no timestamp
//        ONLY // chunk header == null
//    };


    private void setCsid(byte flag) {
        this.csid = (byte) ((flag & 0xff & 0xff) & 0x3f); // 按位与 11 为 1 ，有0 为 0
        switch (this.csid){
            case 0:
                System.err.println("rtmphead =  0");;
                this.csid = byteBuf.readByte() + 64;
                break;
            case 1:
                System.err.println("rtmphead =  0");;
                this.csid = byteBuf.readByte() * 256 + byteBuf.readByte() + 64;
                break;
        }
    }

    private void init() {
        lackMessage = false;
        byte flag = byteBuf.readByte();
        setCsid(flag);
        this.fmt = (byte) ((flag & 0xff & 0xff) >> 6);
        this.head_len = chunk_message_length[this.fmt];
        setChunkHeaderData();
        setMessageData();
    }

    public RtmpPacket(ChannelHandlerContext ctx, ByteBuf byteBuf) {
       this.byteBuf = byteBuf;
       this.prepare();
    }

    public void reset(ByteBuf byteBuf) { //新的数据，重新设置
        if(this.byteBuf.readableBytes() == 0) {
            this.byteBuf.release();
            this.byteBuf = byteBuf;
        }
        copyByteBuf.release();
        prepare();
    }

    private void prepare() {
        copyByteBuf = byteBuf.copy();
        init();
    }


    /**
     * 数据不足时候数据拷贝
     */
    public void lackMessage(ByteBuf byteBuf) {
        int allMessageLength = byteBuf.readableBytes() + copyByteBuf.readableBytes();
        int chunkLen = 0;
        if(isSubpackage) { //存在 分包情况，以chunkSzie 作为判断依据
            if(this.messageLength - this.readIndex >= chunkLength) {
                chunkLen = chunkLength + this.head_len + 1;
            } else {
                chunkLen = this.messageLength - this.readIndex + this.head_len + 1;
            }
        } else {
            chunkLen = this.messageLength + this.head_len + 1;
        }

        if(!chunkByteBufLength(chunkLen, allMessageLength)) { //数据太长，继续拷贝余下部分
            System.out.println("重复拷贝");
            return;
        }
        ByteBuf tmpByteBuf = ByteBufAllocator.DEFAULT.buffer(allMessageLength);
        tmpByteBuf.writeBytes(copyByteBuf);
        tmpByteBuf.writeBytes(byteBuf);
        this.byteBuf = tmpByteBuf;
        init();
    }

    private void setTimestamp() {
        byte[] timestampByte = new byte[Common.TIMESTAMP_BYTE_LENGTH];
        byteBuf.readBytes(timestampByte);
        timestamp += Common.byteToInt24(timestampByte); // 为了解码 时候 客户端使用 所以 进行类加
    }

    private void setMessageLength() {
        byte[] msg_len = new byte[Common.TIMESTAMP_BYTE_LENGTH];
        byteBuf.readBytes(msg_len);
        this.messageLength = Common.byteToInt24(msg_len);
    }

    private void setMessageType() {
        byte type = byteBuf.readByte();
        switch (type) {
            case 0x01:
                this.messageType = RtmpMessage.ChunkSize;
                break;
            case 0x03:
                this.messageType = RtmpMessage.BYTES_READ;
                break;
            case 0x04:
                this.messageType = RtmpMessage.PING;
                break;
            case 0x05:
                this.messageType = RtmpMessage.SERVER_WINDOW;
                break;
            case 0x08:
                this.messageType = RtmpMessage.AUDIO;
                this.audioTimestamp = timestamp;
                break;
            case 0x09:
                this.messageType = RtmpMessage.VIDEO;
                this.videoTimestamp = timestamp;
                break;
            case 0x12:
                this.messageType = RtmpMessage.NOTIFY;
                break;
            case 0x14:
                this.messageType = RtmpMessage.Control;
                break;
            default:
                this.messageType = RtmpMessage.NOT;
                System.out.println("未知消息");
        }
    }

    private void setStreamId(){
        byte[] streamByte = new byte[Common.STREAM_ID_LENGTH];
        byteBuf.readBytes(streamByte);
        this.streamId = Common.byteSmallToInt(streamByte); //只有 stream 是小端模式
    }

    /**
     * 检查是否数据不足
     * @param len
     * @return
     */
    private boolean chunkByteBufLength(int len,int dataLen) {
        if(dataLen < len) {
            //if(!lackMessage){
                byte[] lackData = new byte[this.byteBuf.readableBytes()];
                this.byteBuf.readBytes(lackData);
           // }
            lackMessage = true;
//            System.out.println("chunkByteBufLength 数据不足");
            return false;
        }
        return true;
    }

    /**
     * 根据头部设置 头部数据
     */
    private void setChunkHeaderData() {
        if(!chunkByteBufLength(this.head_len,this.byteBuf.readableBytes())) {
            return;
        }

        if(this.head_len >= 3)
            this.setTimestamp();

        if(this.head_len >= 7){
            this.setMessageLength();
            this.setMessageType();
        }
        if(this.head_len >= 11)
            this.setStreamId();

        if(this.timestamp == Common.TIMESTAMP_MAX_NUM) {
            System.out.println("额外数据");
            byte[] timestampByte = new byte[Common.EXTEND_TIMESTAMP_LENGTH];
            byteBuf.readBytes(timestampByte);
            this.extTimestamp = Common.byteToInt(timestampByte);
        }
    }

    /**
     * 当数据包超过chunkSize 设置时候，需要分包，每个包 head_len为 0
     */
    private void subPackage() {
        isSubpackage = true;
        int chunkLen = 0;
        if(this.messageLength - this.readIndex > chunkLength) {
            chunkLen = chunkLength;
        } else {
            chunkLen = this.messageLength - this.readIndex;
        }
        if(!chunkByteBufLength(chunkLen,byteBuf.readableBytes())) {
            return;
        }
        byte[] chunkMessageData = new byte[chunkLen];
        byteBuf.readBytes(chunkMessageData);
        for(int i = 0;i < chunkLen;i++) {
            messageData[readIndex++] = chunkMessageData[i];
        }
        if(this.readIndex == this.messageLength) {
            this.readIndex = 0;
            isSubpackage = false;
        }
    }

    /**
     * 设置消息数据
     */
    private void setMessageData() {
        if(!isSubpackage) {
            messageData = new byte[this.messageLength];
        }

        if(this.messageLength > chunkLength) { //大数据,rtmp 分包提取
            subPackage();
            return;
        }

        if(!chunkByteBufLength(this.messageLength,byteBuf.readableBytes())) {
            return;
        }
        byteBuf.readBytes(messageData);
    }
}


