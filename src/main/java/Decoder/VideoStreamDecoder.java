package Decoder;

import Util.Common;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import sun.rmi.runtime.Log;

public class VideoStreamDecoder {
    //协议按照编写从头到尾排列
    private byte streamType; // 1byte
    private byte avPacketType; // 1byte
    private byte[] compositionTime; // 3byte 全为0 无意义
    private byte configurationVersion; //1byte 版本
    private byte AVCProfileIndication; //1byte 0x4d sps[1]
    private byte profile_compatibility;//1byte 0x00 sps[2]
    private byte AVCLevelIndication; // 1byte 0x2a sps[3]
    private byte lengthSizeMinusOne; // FLV中NALU包长数据所使用的字节数，包长= （lengthSizeMinusOne & 3） + 1
    private byte numOfSequenceParameterSets; // SPS个数，通常为0xe1 个数= numOfSequenceParameterSets & 01F
    private byte[] spsLength; //2 byte sps长度
    private byte[] spsData;//sps内容 sequenceParameterSetNALUnits
    private byte numOfPictureParameterSets; //pps个数
    private byte[] ppsLength; //2 byte pps长度
    private byte[] ppsData; // pps 内容 pictureParameterSetNALUnits
    private byte[] message;
    private int index = 0; //程序计算的下表
    public VideoStreamDecoder(byte[] data) {
        this.message = data;
        this.streamType = this.message[index++];
        this.avPacketType = data[index++];
        this.compositionTime = new byte[]{this.message[index++],this.message[index++],this.message[index++]};
        if(this.streamType == 0x17) {
//            System.out.println("关键帧");
        }
        if(this.avPacketType == 0x01) {
            H264NALUDecoder();
        } else if(this.avPacketType == 0x00) {
            AvcSequenceHeaderDecoder();
        }
    }
    private void AvcSequenceHeaderDecoder() {

        this.configurationVersion = this.message[index++];
        this.AVCProfileIndication = this.message[index++];
        this.profile_compatibility = this.message[index++];
        this.AVCLevelIndication = this.message[index++];
        this.lengthSizeMinusOne = this.message[index++];

        this.numOfSequenceParameterSets = this.message[index++];
        this.spsLength = new byte[]{this.message[index++],this.message[index++]};
        int spsLength = Common.byteBigToInt16(this.spsLength);
        this.spsData = new byte[spsLength];
        if(spsLength > this.message.length - index){
//            System.out.println("sps数据长度有问题");
            return;
        }
        for(int i = 0;i < spsLength;i++) {
            this.spsData[i] = this.message[index++];
        }
        this.numOfPictureParameterSets = this.message[index++];
        this.ppsLength = new byte[]{this.message[index++],this.message[index++]};
        int ppsLength = Common.byteBigToInt16(this.ppsLength);
        this.ppsData = new byte[ppsLength];
        if(ppsLength > this.message.length - index){
//            System.out.println("pps数据长度有问题");
            return;
        }
        for(int i = 0;i < ppsLength;i++) {
            this.ppsData[i] = this.message[index++];
        }
//        System.out.println("pps length" + ppsLength +" message length " + message.length + " index " + index);
//
//        Common.appendMethodA("D:\\test2.h264",new byte[]{0x00,0x00,0x00,0x01});
//        Common.appendMethodA("D:\\test2.h264",spsData);
//        Common.appendMethodA("D:\\test2.h264",new byte[]{0x00,0x00,0x01});
//        Common.appendMethodA("D:\\test2.h264",ppsData);
    }

    private void H264NALUDecoder() {
        while (index <= this.message.length - 1){
            byte[] naluLength = new byte[]{this.message[index++],this.message[index++],this.message[index++],this.message[index++]};
            int len = Common.byteToInt(naluLength);
            byte[] nalu = new byte[len];
            for(int i = 0;i < len;i++) {
                nalu[i] = this.message[index++];
            }
//            Common.appendMethodA("D:\\test2.h264",new byte[]{0x00,0x00,0x01});
////            Common.appendMethodA("D:\\test2.h264",naluLength);
//
//            Common.appendMethodA("D:\\test2.h264",nalu);
        }
    }


}
