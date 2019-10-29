public class test {
    public static void main(String[] args) {
        String str = "04 00 09 94 00 01 87 08 39 05 00 00 af 01 21 1a cb dd b7 f3 8c 03 8f 88 b3 51 ae 7c f3 5c fb 64 e7 35 3b e3 2f c6 aa f9 92 66 55 5f 31 56 de 8a cb 16 7f bf 8e 37 a5 63 b8 aa ef 5c 56 62 74 75 7c de 1d 8d 56 db 2c 98 89 a7 06 fa 05 1f ce 6a 13 be c9 35 31 28 4c 89 73 e2 3d 13 da b3 a5 dd db 44 87 a4 7c c4 48 32 9f 7f 3e e0 e9 6d 4f 4e 35 73 96 70 9a 4a 08 62 60 97 94 2d 8e 79 5b c6 cb 16 99 26 1e ca 5a 09 f2 5b 26 26 c4 48 88 10 57 74 ac c1 d8 c9 a6 83 13 81 20 21 48 23 59 dd 0c 50 02 67 ca b4 35 a4 1c 2c 9e e8 6b 89 00 ae f8 1c 2c 21 1d 5d 85 1f 08 0c 17 7c df 50 54 98 28 ab 5a e8 e3 a3 33 42 bd 7c f9 57 56 cb 9b b9 de 1c 07 07 f0 7d d0 c0 2b a3 ed 16 58 ce 4d cb d5 13 4b 9f 51 c7 22 ae 1e 6f 33 63 38 ad 7e 0d f5 ac 7d 66 8d 7e 08 fe 5f 84 67 b2 df b2 b2 ea aa de c5 84 96 d1 3b e6 29 a8 c1 67 0f c4 98 a8 31 51 1e e7 5e 79 ae fe 32 6b e7 5e 7e dc 5e 78 d5 5f 33 c6 b5 94 bd ca 46 4b 62 09 6f 8a a8 64 ad 4c 6a ef 15 b0 a4 ac f5 93 9d 05 ad f4 9c 10 67 32 5e 9e 60 36 78 7e 16 fe 39 a8 42 38 35 1c e8 46 96 28 24 2a fa d1 f8 68 43 bd 9e 58 df ab 7d cb a1 45 e5 69 4f 5c a5 ea d4 bd ae 8b 7c 82 6f 66 8a 13 17 97 d4 9e f2 48 72 f4 d8 8c 4e 97 8f 72 8e 21 ad 81 6b 5f b8 ba 92 78 10 b8 c4 be f2 58 b5 70 78";
        String[] arr = str.split(" ");
        String test = "";
        int index = 0;
        for(int i =0; i < arr.length;i++) {
            test += arr[i] + " ";
            index++;
            if(index % 20 == 0) {
                System.out.println(test);
                test = "";
                index = 0;
            }

        }
        System.out.println(test);
    }
}
