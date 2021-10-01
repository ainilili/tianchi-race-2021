package io.openmessaging.mq;

public class Monitor {

    public static long queueCount = 0;
    public static long appendSize = 0;
    public static long appendCount = 0;
    public static long readDistCount = 0;
    public static long readMemCount = 0;
    public static long writeMemCount = 0;
    public static long allocateIdleCount = 0;
    public static long missingIdleCount = 0;
    public static long writeSSDBlockCount = 0;
    public static long readSSDBlockCount = 0;

    public static String information(){
        return "queueCount: " + queueCount +
                ", appendSize: " + appendSize +
                ", appendCount: " + appendCount +
                ", readDistCount: " + readDistCount +
                ", readMemCount: " + readMemCount +
                ", writeMemCount: " + writeMemCount +
                ", allocateIdleCount: " + allocateIdleCount +
                ", missingIdleCount: " + missingIdleCount +
                ", writeSSDBlockCount: " + writeSSDBlockCount +
                ", readSSDBlockCount: " + readSSDBlockCount
                ;
    }
}
