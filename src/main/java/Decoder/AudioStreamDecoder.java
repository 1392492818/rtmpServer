package Decoder;

import Util.AACDecoderSpecific;
import Util.AudioSpecificConfig;
import Util.Common;

public class AudioStreamDecoder {
    private int index = 0;
    AudioSpecificConfig ascAudioSpecificConfig = new AudioSpecificConfig();
    AACDecoderSpecific aacDecoderSpecific = new AACDecoderSpecific();


    public void decode(byte[] data) {
        byte type = data[index++];
        byte AACPacketType = data[index++];
//        System.out.println(Common.bytes2hex(data));
        if (AACPacketType == 0x00) {
            System.out.println(Common.bytes2hex(data));
            aacDecoderSpecific.nAudioFortmatType = (byte) ((type & 0xff & 0xff) >> 4);
            aacDecoderSpecific.nAudioSampleType = (byte) ((type & 0x0c & 0xff & 0xff) >> 2);
            aacDecoderSpecific.nAudioSizeType = (byte) ((type & 0x02 & 0xff & 0xff) >> 1);
            aacDecoderSpecific.nAudioStereo = (byte) (type & 0x01 & 0xff & 0xff);
        }
//        System.out.println(aacDecoderSpecific.nAudioFortmatType);

        byte[] packet = new byte[data.length - index];
//        System.out.println("packet length" + packet.length);
//        System.out.println("data length" + data.length);
        if (aacDecoderSpecific.nAudioFortmatType == 0x0a) {
            for (int i = 0; i < data.length - 2; i++) {
                packet[i] = data[index++];
            }
            if (AACPacketType == 0x00) {
//                Common.appendMethodA("D:\\test2.aac",packet);
//                unsigned short audioSpecificConfig = 0;
                short audioSpecificConfig = (short) ((data[2] & 0xff) << 8);
                audioSpecificConfig += 0x00ff & data[3];
                ascAudioSpecificConfig.nAudioObjectType = (byte) ((audioSpecificConfig & 0xF800) >> 11);
                ascAudioSpecificConfig.nSampleFrequencyIndex = (byte) ((audioSpecificConfig & 0x0780) >> 7);
                ascAudioSpecificConfig.nChannels = (byte) ((audioSpecificConfig & 0x78) >> 3);
                ascAudioSpecificConfig.nFrameLengthFlag = (byte) ((audioSpecificConfig & 0x04) >> 2);
                ascAudioSpecificConfig.nDependOnCoreCoder = (byte) ((audioSpecificConfig & 0x02) >> 1);
                ascAudioSpecificConfig.nExtensionFlag = (byte) (audioSpecificConfig & 0x01);
                System.out.println("nAudioObjectType" + ascAudioSpecificConfig.nAudioObjectType);
                System.out.println("nSampleFrequencyIndex" + ascAudioSpecificConfig.nSampleFrequencyIndex);
                System.out.println("nChannels" + ascAudioSpecificConfig.nChannels);
//                Common.appendMethodA("D:\\fuweicong.aac",Common.CreateADTS(ascAudioSpecificConfig,packet.length));
//                Common.appendMethodA("D:\\fuweicong.aac",packet);
            } else {
                Common.appendMethodA("D:\\fuweicong.aac",Common.CreateADTS(ascAudioSpecificConfig,packet.length + 7));
                Common.appendMethodA("D:\\fuweicong.aac",packet);
            }
        }
        index = 0;
    }




}
