package User;

import Util.Common;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;
import java.util.Map;

public class Publish {
    public String path;
    public ChannelHandlerContext publish;
    public Map<String,Object> MetaData;
    public boolean keyFrame = false;
    public byte[] keyFrameMessage = null;
    public int chunk_size = Common.DEFAULT_CHUNK_MESSAGE_LENGTH;

}
