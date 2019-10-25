package User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReceiveGroup {
    public static Map<String, List<Receive>> channel = new HashMap<String, List<Receive>>();

//    /**
//     * 设置 channel
//     * @param path
//     * @param client
//     */
//    public synchronized static void setChannel(String path, List<Receive> client) {
//        channel.put(path,client);
//    }

    /**
     * 设置 channel
     * @param path
     * @param client
     */
    public synchronized static void setChannel(String path,Receive client) {
        List<Receive> list = new ArrayList<Receive>();
        if(channel.containsKey(path)){
            list = channel.get(path);
            list.add(client);
        } else {
            list = new ArrayList<Receive>();
            list.add(client);
        }
        channel.put(path,list);
    }

    /**
     * 获取 channel
     * @param path
     * @return
     */
    public static List<Receive> getChannel(String path) {
        if(channel.containsKey(path)){
            return channel.get(path);
        }
        return null;
    }
}
