package User;

import io.netty.channel.ChannelHandlerContext;

import java.util.List;
import java.util.Map;

public class Publish {
    public String path;
    public ChannelHandlerContext publish;
    public Map<String,Object> MetaData;
}
