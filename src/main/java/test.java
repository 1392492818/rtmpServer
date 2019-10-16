public class test {
    public static void main(String[] args) {
        byte bt = (byte) 0xc4;
        byte b22 = (byte) ((bt & 0xff & 0xff) >> 6);
        byte[] str = {
                0x72, 0x65 ,0x6c, 0x65 ,0x61, 0x73, 0x65, 0x53, 0x74,
                0x72, 0x65 ,0x61 ,0x6d
        };
        System.out.println(new String(str));


        System.out.println("无符号数: \t"+b22);
    }
}
