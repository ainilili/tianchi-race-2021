package io.openmessaging.mq;


import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Queue {

    private long offset;

    private final List<Data> records;

    private final Cache cache;

    private boolean reading;

    public Queue(Cache cache) {
        this.cache = cache;
        this.offset = -1;
        this.records = new ArrayList<>();
        Monitor.queueCount ++;
    }

    public long nextOffset(){
        return ++ offset;
    }

    public static final ThreadPoolExecutor ES = (ThreadPoolExecutor) Executors.newFixedThreadPool(256);

    public boolean write(FileWrapper aof, long position, ByteBuffer buffer, Data pMem){
        if (pMem != null){
            records.add(pMem);
            return false;
        }
        Data data =  Buffers.allocateReadBuffer();
        if (data == null){
            data = Threads.get().allocateReadBuffer();
            if (data == null){
                data = cache.allocate(buffer.limit());
                if (data == null && reading){
                    data = Buffers.allocateExtraData();
                }
            }
        }
        if (data != null){
            data.set(buffer);
        }else{
            data = new SSD(aof, position, buffer.limit());
        }
        records.add(data);
        return false;
    }

    public ByteBuffer[] read(long offset, int num){
        Threads.Context ctx = Threads.get();
        if (!reading){
            for (long i = 0; i < offset; i ++){
                Data data = records.get((int) i);
                if (data instanceof PMem){
                    ctx.recyclePMem(data);
                }else if (data instanceof Dram){
                    ctx.recycleReadBuffer(data);
                }
            }
            reading = true;
        }

        int end = (int) Math.min(offset + num, records.size());
        int size = (int) (end - offset);
        ByteBuffer[] buffers = new ByteBuffer[size];
        CountDownLatch cdl = new CountDownLatch(size);
        for (int i = (int) offset; i < end; i ++){
            final int index = i;
            ES.execute(()->{
                Data data = records.get(index);
                buffers[(int) (index - offset)] = data.get(ctx);
                cdl.countDown();
            });
        }
        try {
            cdl.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (int i = (int) offset; i < end; i ++){
            Data data = records.get(i);
            if (data instanceof PMem){
                ctx.recyclePMem(data);
            }else if (data instanceof Dram){
                ctx.recycleReadBuffer(data);
            }
        }
        return buffers;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public List<Data> getRecords() {
        return records;
    }
}
