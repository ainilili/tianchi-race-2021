package io.openmessaging.mq;

public class Monitor {

    public static long queueCount = 0;
    public static long appendSize = 0;
    public static long appendCount = 0;
    public static long readMemCount = 0;
    public static long writeMemCount = 0;
    public static long writeDramCount = 0;
    public static long writeExtraDramCount = 0;
    public static long readSSDCount = 0;
    public static long swapSSDToPmemCount = 0;
    public static long allocateIdleCount = 0;
    public static long missingIdleCount = 0;
    public static long extSize = 0;


    public static String information(){
        return "queueCount: " + queueCount +
                ", appendSize: " + appendSize +
                ", appendCount: " + appendCount +
                ", readMemCount: " + readMemCount +
                ", writeMemCount: " + writeMemCount +
                ", writeDramCount: " + writeDramCount +
                ", writeExtraDramCount: " + writeExtraDramCount +
                ", readSSDCount: " + readSSDCount +
                ", swapSSDToPmemCount: " + swapSSDToPmemCount +
                ", allocateIdleCount: " + allocateIdleCount +
                ", missingIdleCount: " + missingIdleCount +
                ", aep tasks: " + Mq.AEP_TASKS.size() +
                ", extSize: " + extSize
                ;
    }
}
