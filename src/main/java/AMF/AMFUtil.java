package AMF;


import Util.Common;

import java.lang.reflect.Field;
import java.util.*;


public class AMFUtil {
    /**
     * 加载 amf string
     * @param amfClass
     * @return
     */
    public static String load_amf_string(AMFClass amfClass) {
        String msg = "";
        if(amfClass.message[amfClass.pos] != AMF.String) {
            return msg;
        }
        amfClass.pos += 1;
        if(amfClass.pos + 2 > amfClass.message.length){
            System.out.println("string len 解析数据长度不足，数据错误");
            return msg;
        }
        byte[] strLenByte = {amfClass.message[amfClass.pos],amfClass.message[amfClass.pos+1]};
        int strLen = Common.byteToInt16(strLenByte);
        amfClass.pos += 2;
        if(amfClass.pos + strLen > amfClass.message.length){
            System.out.println("string message 解析数据长度不足，数据错误");
            return msg;
        }
        byte[] str = new byte[strLen];
        int index = 0;
        for(int i = amfClass.pos;i < amfClass.pos + strLen;i++){ //链接字符串
            str[index] = amfClass.message[i];
            index++;
        }
        amfClass.pos += strLen;
        msg = new String(str);
        return msg;
    }

    /**
     * amf string 写入
     * @param str
     * @return
     */
    public static byte[] writeString(String str) {
        List<Byte> data = new ArrayList<Byte>();
        data.add(AMF.String);
        byte[] strByte = str.getBytes();
        int len = strByte.length;
        byte[] lenByte = Common.intToByte(len); //转换 之后为小端模式，直接拿前两位 就可以了
        data.add(lenByte[1]); // rtmp 数据都为 大端模式
        data.add(lenByte[0]);
        for(byte val: strByte) {
            data.add(val);
        }
        return Common.conversionByteArray(data);
    }

    /**
     *
     * @param key
     * @return
     */
    public static byte[] writeKey(String key) {
        List<Byte> data = new ArrayList<Byte>();
        byte[] strByte = key.getBytes();
        int len = strByte.length;
        byte[] lenByte = Common.intToByte(len); //转换 之后为小端模式，直接拿前两位 就可以了
        data.add(lenByte[1]); // rtmp 数据都为 大端模式
        data.add(lenByte[0]);
        for(byte val: strByte) {
            data.add(val);
        }
        return Common.conversionByteArray(data);
    }

    /**
     * 编写boolean
     * @param val
     * @return
     */
    public static byte[] writeBoolean(boolean val) {
        byte[] data = new byte[2];
        data[0] = AMF.Boolean;
        data[1]  = (byte) (val ? 0x01: 0x00);
        return data;
    }

    public static byte[] writeObject(Map<String,Object> objectData) {
        List<Byte> data = new ArrayList<Byte>();
        data.add(AMF.UntypedObject);
        for (Map.Entry<String, Object> entry : objectData.entrySet()) {
            try {
                byte[]  valueByte = writeValue(entry.getValue()); //如果解析不出 数据，直接跳过去
                String key = entry.getKey();
                byte[] keyByte = writeKey(key);
                for(byte i : keyByte){
                    data.add(i);
                }
                for(byte i: valueByte){
                    data.add(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        data.add((byte) 0x00);
        data.add((byte) 0x00);
        data.add((byte) 0x09);
        return  Common.conversionByteArray(data);
    }


    /**
     * 获取 object value
     * @param param
     * @return
     */
    public static byte[] writeValue(Object param) throws Exception {
        byte[] data;
        if (param instanceof Double) {
            double d = ((Double) param).doubleValue();
            data = writeNumber(d);
        } else if (param instanceof String) {
            String s = (String) param;
            data = writeString(s);
        } else if (param instanceof Boolean) {
            boolean b = ((Boolean) param).booleanValue();
            data = writeBoolean(b);
        } else if(param instanceof Map){
            Map<String,Object> map = objectToMap(param);
            data = writeObject(map);
        } else {
           throw  new Exception("数据异常，没有找到解析value");
        }
        return data;
    }

    public static Map objectToMap(Object obj) {
        Map<String, Object> map = new HashMap<String, Object>();
        map = objectToMap(obj, false);
        return map;
    }

    public static Map objectToMap(Object obj, boolean keepNullVal) {
        if (obj == null) {
            return null;
        }

        Map<String, Object> map = new HashMap<String, Object>();
        try {
            Field[] declaredFields = obj.getClass().getDeclaredFields();
            for (Field field : declaredFields) {
                field.setAccessible(true);
                if (keepNullVal == true) {
                    map.put(field.getName(), field.get(obj));
                } else {
                    if (field.get(obj) != null && !"".equals(field.get(obj).toString())) {
                        map.put(field.getName(), field.get(obj));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    /**
     * amf number 写法
     * @param val
     * @return
     */
    public static byte[] writeNumber(double val){
        List<Byte> data = new ArrayList<Byte>();
        data.add(AMF.Number);
        byte[] doubleData = Common.reverseArray(Common.double2Bytes(val));
        for(byte i : doubleData){
            data.add(i);
        }
        return Common.conversionByteArray(data);
    }

    /**
     * amf null 写入方法
     * @return
     */
    public static byte writeNull() {
        return AMF.Null;
    }

    /**
     * 提取 amf 里面的 boolean
     * @param amfClass
     * @return
     */
    public static boolean load_amf_boolean(AMFClass amfClass) {
        if(amfClass.message[amfClass.pos] != AMF.Boolean) {
            System.out.println("boolean获取失败");
            return false;
        }
        amfClass.pos += 1;
        boolean flag = amfClass.message[amfClass.pos] != 0;
        amfClass.pos += 1;
        return flag;
    }

    /**
     * 加载 key
     * @param amfClass
     * @return
     */
    public static String load_amf_key(AMFClass amfClass) {
        String msg = "";
        if(amfClass.pos + 2 > amfClass.message.length){
            System.out.println("key len 解析数据长度不足，数据错误");
            return msg;
        }
        byte[] strLenByte = {amfClass.message[amfClass.pos],amfClass.message[amfClass.pos+1]};
        int strLen = Common.byteToInt16(strLenByte);
        amfClass.pos += 2;
        if(amfClass.pos + strLen > amfClass.message.length){
            System.out.println("key message 解析数据长度不足，数据错误");
            return msg;
        }
        byte[] str = new byte[strLen];
        int index = 0;
        for(int i = amfClass.pos;i < amfClass.pos + strLen;i++){ //链接字符串
            str[index] = amfClass.message[i];
            index++;
        }
        amfClass.pos += strLen;
        msg = new String(str);
        return msg;
    }

    /**
     * 解析 amf number
     * @param amfClass
     * @return
     */
    public static double load_amf_number(AMFClass amfClass){
        byte type = amfClass.message[amfClass.pos];
        if(type != AMF.Number){
            return -1;
        }
        amfClass.pos += 1;
        if(amfClass.pos + Common.AFM_NUMBER_LENGTH > amfClass.message.length) {
            return -1;
        }
        byte[] number = new byte[Common.AFM_NUMBER_LENGTH];
        int index = 0;
        for(int i = amfClass.pos; i < amfClass.pos + Common.AFM_NUMBER_LENGTH;i++) {
            number[index] = amfClass.message[i];
            index++;
        }
//        System.out.println (Common.bytes2hex(Common.reverseArray(number)));
        amfClass.pos += Common.AFM_NUMBER_LENGTH;
        return Common.bytesToDouble(Common.reverseArray(number));
    }


//    public static Object load_amf_value(AMFClass amfClass){
//        byte type = amfClass.message[amfClass.pos];
//        switch (type) {
//            case AMF.Number:
//                return load_amf_number(amfClass);
//            case AMF.String:
//                return load_amf_string(amfClass);
//            case AMF.UntypedObject:
//                return load_amf_object(amfClass);
//            case AMF.Boolean:
//                return load_amf_boolean(amfClass);
//                default:
//                    System.out.println("其他消息" + type);
//                    return null;
//        }
//    }
    /**
     * 解析 object value
     * @param amfClass
     * @return
     */
    public static Object load_amf(AMFClass amfClass) {
        byte type = amfClass.message[amfClass.pos];
        switch (type) {
            case AMF.Number:
                return load_amf_number(amfClass);
            case AMF.String:
                return load_amf_string(amfClass);
            case AMF.UntypedObject:
                return load_amf_object(amfClass);
            case AMF.Boolean:
                return load_amf_boolean(amfClass);
            case AMF.Null:
                amfClass.pos++;
                return null;
            default:
                System.out.println("其他消息" + type);
                return null;
        }
    }

    /**
     * 解析 amf object  数据
     * @param amfClass
     * @return
     */
    public static Map<String,Object> load_amf_object(AMFClass amfClass) {
        Map<String,Object> amfData = new HashMap<String, Object>();
        byte type = amfClass.message[amfClass.pos];
        if(type !=  AMF.UntypedObject){
            return null;
        }
        amfClass.pos += 1;
        while (true){
            String key = load_amf_key(amfClass);
            if(key.length() == 0){
                break;
            }
            Object value = load_amf(amfClass);
            amfData.put(key,value);
//            System.out.println(key +"==="+value);
        }
        System.out.println(amfClass.pos);
        amfClass.pos += 1;// object 最后 结束为 00 00 09 取到最后是00 所以 +1 跳过 09 这个 字节
        return amfData;
    }


}
