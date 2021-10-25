package io.openmessaging.mq;

import io.openmessaging.consts.Const;
import io.openmessaging.utils.BufferUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

public class Queue {

    private long offset;

    private final List<Data> records;

    private boolean reading;

    public Queue() {
        this.offset = -1;
        this.records = new ArrayList<>(30);
        Monitor.queueCount ++;
    }

    public long nextOffset(){
        return ++ offset;
    }

    public void write(FileWrapper aof, long position, ByteBuffer buffer, Data pMem){
        if (pMem != null){
            records.add(pMem);
            return;
        }
        Threads.Context ctx = Threads.get();
        Data data = ctx.allocateReadBuffer(buffer.limit());
        if (data == null){
            Monitor.missingDramSize ++;
            data = ctx.allocatePMem(buffer.limit());
            if (data == null){
                data = Buffers.allocateReadBuffer(buffer.limit());
            }
        }
        if (data != null){
            data.set(buffer);
            records.add(data);
            return;
        }
        records.add(new SSD(aof, position, buffer.limit()));
    }

    public Map<Integer, ByteBuffer> read(long offset, int num) throws IOException {
        Threads.Context ctx = Threads.get();
        if (!reading){
            for (long i = 0; i < offset; i ++){
                Data data = records.get((int) i);
                if (data.isDram()){
                    ctx.recycleReadBuffer(data);
                }else if (data.isPMem()){
                    ctx.recyclePMem(data);
                }
            }
            reading = true;
        }

        long nextReadOffset = (int) Math.min(offset + num, records.size());
        int size = (int) (nextReadOffset - offset);
        FutureMap results = ctx.getResults();
        results.setMaxIndex(size - 1);

        Semaphore semaphore = ctx.getSemaphore();
        for (int i = (int) offset; i < nextReadOffset; i ++){
            Data data = records.get(i);
            int index = (int) (i - offset);
            ctx.getPools().execute(()->{
                try {
                    results.put(index, data.get(ctx));
                    if (data.isDram()){
                        ctx.recycleReadBuffer(data);
                    }else if (data.isPMem()){
                        ctx.recyclePMem(data);
                    }
                } finally {
                    semaphore.release();
                }
            });
        }
        // 预加载
        long nextLoadSize = Math.min(this.offset - nextReadOffset + 1, 20);
        for (int i = (int) nextReadOffset; i < nextReadOffset + nextLoadSize; i ++){
            if (i >= records.size()){
                break;
            }
            int index = i;
            Data data = records.get(index);
            if (data.isSSD()){
                Monitor.readPreSSDCount ++;
                ctx.getPools().execute(()->{
                    ByteBuffer buffer = data.get(ctx);
                    Data bufferData = ctx.allocatePMem(buffer.limit());
                    if (bufferData != null){
                        bufferData.set(buffer);
                        records.set(index, bufferData);
                    }
                });
            }
        }
        try {
            semaphore.acquire(size);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return results;
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
