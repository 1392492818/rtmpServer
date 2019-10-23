package User;

import java.util.HashMap;
import java.util.Map;

public class PublishGroup {
    public static  Map<String, Publish> channel = new HashMap<String, Publish>();

    /**
     * 设置 channel
     * @param path
     * @param client
     */
    public synchronized static void setChannel(String path, Publish client) {
        PublishGroup.channel.put(path,client);
    }

    /**
     * 获取 channel
     * @param path
     * @return
     */
    public static Publish getChannel(String path) {
        if(channel.containsKey(path)){
            return channel.get(path);
        }
        return null;
    }
}
