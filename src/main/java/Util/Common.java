package Util;

import java.util.ArrayList;
import java.util.List;

/**
 * 公共配置工具类
 */
public class Common {
    public static int C0_LENGTH = 1;
    public static int C0_INDEX = 0;
    public static int C1_LENGTH = 1536;
    public static byte C0 = 0x03;
    public static byte S0 = 0x03;
    public static int HANDSHAKE_LENGTH = 3073;
    public static int RANDOM_LENGTH = 1536 - 8;
    // chunk head
    public static int MSG_LEN_LENGTH = 3;
    public static int TIMESTAMP_BYTE_LENGTH = 3;
    public static int MST_TYPE_LENGTH = 1;
    public static int STREAM_ID_LENGTH = 4;
    public static int TIMESTAMP_MAX_NUM = 16777215;
    public static int EXTEND_TIMESTAMP_LENGTH = 4;
    // chunk message
    public static int MESSAGE_MT_LENGTH = 1;
    public static int MESSAGE_PAYLOAD_LENGTH = 3;
    public static int MESSAGE_TIMESTAMP_LENGTH = 4;
    public static int MESSAGE_STREAM_ID_LENGTH = 3;
    // AMF
    public static int AFM_TYPE_LENGTH = 1; // afm 数据类型长度
    public static int AFM_DATA_LENGTH = 2; // afm 表示一个数据的长度
    public static int AFM_NUMBER_LENGTH = 8; //afm 一个 number 的长度，其实为 double

    public static String APP_NAME = "live";

    /**
     * int 转换 为 byte
     * @param val
     * @return
     */
    public static byte[] intToByte(int val){
        byte[] b = new byte[4];
        b[0] = (byte)(val & 0xff);
        b[1] = (byte)((val >> 8) & 0xff);
        b[2] = (byte)((val >> 16) & 0xff);
        b[3] = (byte)((val >> 24) & 0xff);
        return b;
    }

    /**
     * 16 byte int
     * @param bytes
     * @return
     */
    public static int byteToInt16(byte[] bytes) {
        return  0x00 << 24| 0x00 << 16 | (bytes[0] & 0xff) << 8 | bytes[1] & 0xff;
    }

    /**
     * 24 byte
     * @param bytes
     * @return
     */
    public static int byteToInt24(byte[] bytes) {
        return  0x00 << 24| (bytes[0] & 0xff) << 16 | (bytes[1] & 0xff) << 8 | bytes[2] & 0xff;
    }

    /**
     * 将byte 转换为int 直接大端转换
     * @param bytes
     * @return
     */
    public static int byteToInt(byte[] bytes) {
        return  (bytes[0] & 0xff) << 24| (bytes[1] & 0xff) << 16 | (bytes[2] & 0xff) << 8 | bytes[3] & 0xff;
    }

    /**
     * 将byte 转换为 int 小端转换
     * @param bytes
     * @return
     */
    public static int byteSmallToInt(byte[] bytes) {
        return  (bytes[3] & 0xff) << 24| (bytes[2] & 0xff) << 16 | (bytes[1] & 0xff) << 8 | bytes[0] & 0xff;
    }

    /**
     * 将一个8位字节数组转换为双精度浮点数。<br>
     * 注意，函数中不会对字节数组长度进行判断，请自行保证传入参数的正确性。
     *
     * @param b
     *            字节数组
     * @return 双精度浮点数
     */
    public static double bytesToDouble(byte[] b) {
        return Double.longBitsToDouble(bytesToLong(b));
    }

    /**
     * 将一个8位字节数组转换为长整数。<br>
     * 注意，函数中不会对字节数组长度进行判断，请自行保证传入参数的正确性。
     *
     * @param b
     *            字节数组
     * @return 长整数
     */
    public static long bytesToLong(byte[] b) {
        int doubleSize = 8;
        long l = 0;
        for (int i = 0; i < doubleSize; i++) {
            // 如果不强制转换为long，那么默认会当作int，导致最高32位丢失
            l |= ((long) b[i] << (8 * i)) & (0xFFL << (8 * i));
        }

        return l;
    }

    /**
     * int 转换 为 byte 数组
     * @param num
     * @return
     */
    public static byte[] int2Bytes(int num) {
        byte[] bytes = new byte[4];
        //通过移位运算，截取低8位的方式，将int保存到byte数组
        bytes[0] = (byte)(num >>> 24);
        bytes[1] = (byte)(num >>> 16);
        bytes[2] = (byte)(num >>> 8);
        bytes[3] = (byte)num;
        return bytes;
    }

    public static byte[] double2Bytes(double d) {

        long value = Double.doubleToRawLongBits(d);

        byte[] byteRet = new byte[8];

        for (int i = 0; i < 8; i++) {

            byteRet[i] = (byte) ((value >> 8 * i) & 0xff);

        }

        return byteRet;

    }


    /**
     * 将 List 转换为 数组
     * @param val
     * @return
     */
    public static byte[] conversionByteArray(List<Byte> val) {
        byte[] s1 = new byte[val.size()];
        for(int i = 0; i < val.size();i++) {
            s1[i] = val.get(i);
        }
        return s1;
    }

    /**
     * 格式打印 二进制数据
     * @param bytes
     * @return
     */
    public static String bytes2hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        String tmp = null;
        int i = 1;
        for (byte b : bytes) {
            // 将每个字节与0xFF进行与运算，然后转化为10进制，然后借助于Integer再转化为16进制
            tmp = Integer.toHexString(0xFF & b);
            if (tmp.length() == 1) {
                tmp = "0" + tmp;
            }
            tmp += " ";
            if(i % 20 == 0) {
                tmp += "\r\n";
            }
            sb.append(tmp);
            i++;
        }
        return sb.toString();
    }


    /**
     * 数组倒置
     * @param Array
     * @return
     */
    public static byte[] reverseArray(byte[] Array) {
        byte[] new_array = new byte[Array.length];
        for (int i = 0; i < Array.length; i++) {
            // 反转后数组的第一个元素等于源数组的最后一个元素：
            new_array[i] = Array[Array.length - i - 1];
        }
        return new_array;
    }

}
