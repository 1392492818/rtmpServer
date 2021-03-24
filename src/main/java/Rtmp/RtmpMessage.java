package Rtmp;

public enum RtmpMessage {
    ChunkSize, //chunk 大小设置
    ABORT,
    BYTES_READ,
    PING,
    SERVER_WINDOW,
    AUDIO,
    VIDEO,
    NOTIFY, // 设置元消息
    Control, //命令控制
    NOT
}
