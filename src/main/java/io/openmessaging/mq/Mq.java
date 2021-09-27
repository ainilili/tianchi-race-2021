package io.openmessaging.mq;

import com.intel.pmem.llpl.AnyMemoryBlock;
import com.intel.pmem.llpl.Heap;
import io.openmessaging.MessageQueue;
import io.openmessaging.consts.Const;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Mq extends MessageQueue{

    private Heap heap;

    private final Config config;

    private final Map<Key, Data> records;

    private final LinkedBlockingQueue<Key> keys;

    private final Map<String, Map<Integer, AtomicLong>> offsets;

    private final Barrier barrier;

    private final FileWrapper aof;

    private final FileWrapper tpf;

    private final AtomicLong size;

    private final ReadWriteLock lock;

    private long toSSDTimes;

    private long fromSSDTimes;

    private static final Logger LOGGER = LoggerFactory.getLogger(Mq.class);

    private static final ThreadPoolExecutor POOLS = (ThreadPoolExecutor) Executors.newFixedThreadPool(200);

    public Mq(Config config) throws FileNotFoundException {
        this.config = config;
        this.records = new ConcurrentHashMap<>();
        this.offsets = new ConcurrentHashMap<>();
        this.keys = new LinkedBlockingQueue<>();
        this.aof = new FileWrapper(new RandomAccessFile(config.getDataDir() + "aof", "rw"));
        this.tpf = new FileWrapper(new RandomAccessFile(config.getDataDir() + "tpf", "rw"));
        this.barrier = new Barrier(config.getMaxCount(), this.aof);
        this.size = new AtomicLong();
        this.lock = new ReentrantReadWriteLock();
        if (config.getHeapDir() != null){
            this.heap = Heap.exists(config.getHeapDir()) ? Heap.openHeap(config.getHeapDir()) : Heap.createHeap(config.getHeapDir(), config.getHeapSize());
        }
        if (config.getLiveTime() > 0){
            startKiller();
        }
        startMonitor();
    }

    void startKiller(){
        new Thread(()->{
            try {
                Thread.sleep(config.getLiveTime());
                System.exit(-1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    void startMonitor(){
        Thread monitor = new Thread(()->{
            while (true){
                try {
                    Thread.sleep(Const.SECOND * 5);
                    LOGGER.info("performance >> current cache size {}, records size {}, to ssd times {}, from ssd times {}", size, records.size(), toSSDTimes, fromSSDTimes);
                    LOGGER.info("thread pool >> active count {}/{}", POOLS.getActiveCount(), POOLS.getPoolSize());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        monitor.setDaemon(true);
        monitor.start();
    }


    Data applyBlock(ByteBuffer buffer){
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        return new PMem(POOLS.submit(()->{
            AnyMemoryBlock block = heap.allocateCompactMemoryBlock(bytes.length);
            block.copyFromArray(bytes, 0, 0, bytes.length);
            return block;
        }), bytes);
    }

    Data applyData(ByteBuffer buffer){
        return heap == null ? new Dram(buffer) : applyBlock(buffer);
    }

    void append(Data data) {
        keys.add(data.getKey());
        records.put(data.getKey(), data);
        size.addAndGet(data.size());
        if (size.get() > config.getCacheMaxSize()){
            lock.writeLock().lock();
            if (size.get() > config.getCacheMaxSize()){
                try {
                    long start = System.currentTimeMillis();
                    clear();
                    long end = System.currentTimeMillis();
                    LOGGER.info("clear spend {} ms", end - start);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            lock.writeLock().unlock();
        }
    }

    void clear() throws IOException {
        long size = 0;
        Map<String, Map<Integer, List<Data>>> clears = new HashMap<>();
        while(size < config.getCacheClearSize()){
            Key key = keys.poll();
            if (key == null){
                break;
            }
            Data data = records.remove(key);
            if (data == null) {
                continue;
            }
            size += data.size();
            clears.computeIfAbsent(key.getTopic(), k -> new HashMap<>())
                .computeIfAbsent(key.getQueueId(), k -> new ArrayList<>())
                .add(data);
        }

        for (Map.Entry<String, Map<Integer, List<Data>>> clear: clears.entrySet()){
            String topic = clear.getKey();
            for (Map.Entry<Integer, List<Data>> entry: clear.getValue().entrySet()){
                int queueId = entry.getKey();
                List<Data> list = entry.getValue();

                long startOffset = list.get(0).getKey().getOffset();
                long endOffset = list.get(list.size() - 1).getKey().getOffset();

                List<ByteBuffer> buffers = new ArrayList<>(list.size());
                long capacity = 0;
                List<Long > sizes = new ArrayList<>();
                for (Data data: list){
                    capacity += data.size();
                    buffers.add(data.get());
                    sizes.add(data.size());
                    data.clear();
                    this.size.addAndGet(- data.size());
                }
                toSSDTimes ++;
                long position = tpf.write(buffers.toArray(Barrier.EMPTY));
                SSD ssd = new SSD(startOffset, position, capacity, sizes);
                ssd.setKey(new Key(topic, queueId, -1L));
                for (long i = startOffset; i <= endOffset; i ++){
                    records.put(new Key(topic, queueId, i), ssd);
                }
            }
        }
    }

    long nextOffset(String topic, int queueId){
        return offsets.computeIfAbsent(topic, k -> new HashMap<>()).computeIfAbsent(queueId, k -> new AtomicLong(-1)).addAndGet(1);
    }

    long s = 0;
    int c = 0;
    public long append(String topic, int queueId, ByteBuffer buffer) {
        try {
            ++c;
            s += buffer.capacity();
            if (c % 100000 == 0){
                LOGGER.info("append count {}, size {}", c, s);
            }
            return _append(topic, queueId, buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public Map<Integer, ByteBuffer> getRange(String topic, int queueId, long offset, int fetchNum) {
        try {
            return _getRange(topic, queueId, offset, fetchNum);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new HashMap<>();
    }

    public long _append(String topic, int queueId, ByteBuffer buffer) throws IOException {
        long offset = nextOffset(topic, queueId);
        Data data = applyData(buffer);
        data.setKey(new Key(topic, queueId, offset));
        append(data);

        ByteBuffer header = ByteBuffer.allocateDirect(topic.getBytes().length + 4)
                .put(topic.getBytes())
                .putShort((short) queueId)
                .putShort((short) buffer.capacity());
        header.flip();
        buffer.flip();
        barrier.write(header, buffer);
        barrier.await(30, TimeUnit.SECONDS);
        return offset;
    }

    public Map<Integer, ByteBuffer> _getRange(String topic, int queueId, long offset, int fetchNum) throws IOException {
        lock.readLock().lock();
        long startOffset = offset;
        long endOffset = startOffset + fetchNum - 1;
        Map<Integer, ByteBuffer> results = new HashMap<>();
        for (;startOffset <= endOffset; startOffset++){
            Data data = records.remove(new Key(topic, queueId, startOffset));
            if (data == null){
                break;
            }
            if (data instanceof SSD){
                SSD ssd = (SSD) data;
                fromSSDTimes ++;
                List<ByteBuffer> buffers = ssd.load(startOffset, tpf);
                long tempOffset = startOffset;
                for (ByteBuffer buffer: buffers){
                    Data record = applyData(buffer);
                    if (tempOffset == offset){
                        data = record;
                    }
                    records.put(new Key(topic, queueId, tempOffset), record);
                    tempOffset ++;
                }
            }
            results.put((int) (startOffset - offset), data.get());
            data.clear();
            this.size.addAndGet(- data.size());
        }
        lock.readLock().unlock();
        return results;
    }

    @Override
    public String toString() {
        return "Mq{" +
                "size=" + size +
                '}';
    }

}