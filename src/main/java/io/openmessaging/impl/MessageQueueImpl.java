package io.openmessaging.impl;

import io.openmessaging.MessageQueue;
import io.openmessaging.cache.Cache;
import io.openmessaging.consts.Const;
import io.openmessaging.model.*;
import io.openmessaging.utils.ArrayUtils;
import io.openmessaging.utils.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageQueueImpl extends MessageQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageQueueImpl.class);

    private final Config config;
    private Cache cache;
    private Aof aof;
    private final Map<String, Topic> topics;
    private static final Map<Integer, ByteBuffer> EMPTY = new HashMap<>();
    private final AtomicInteger id;
    private CyclicBarrier cyclicBarrier;

    public MessageQueueImpl() {
        this(new Config(
                "/essd/",
                "/pmem/nico",
                Const.G * 59,
                (int) ((Const.G * 51) / (Const.K * 512)),
                Const.K * 512,
                1,
                40,
                Const.K * 320)
        );
    }

    public MessageQueueImpl(Config config) {
        LOGGER.info("start");
        this.config = config;
        this.topics = new ConcurrentHashMap<>();
        this.id = new AtomicInteger(1);
        try {
//            Group group = new Group(new FileWrapper(new RandomAccessFile(config.getDataDir() + "tmp.db", "rw")), null);
//            this.cache = new Cache(config.getHeapDir(), config.getHeapSize(), config.getLruSize(), config.getPageSize(), group);
            this.aof = new Aof(new FileWrapper(new RandomAccessFile(config.getDataDir() + "aof.log", "rw")), config);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.cyclicBarrier = new CyclicBarrier(config.getMaxCount(), ()->{
            try {
                this.aof.getWrapper().write(this.aof.getBuffers().toArray(Aof.EMPTY));
                this.aof.getWrapper().getChannel().force(false);
                this.aof.getBuffers().clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        Thread thread = new Thread(()->{
            try {
                Thread.sleep(1000 * 60 * 4);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(-1);
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void cleanDB(){
        File root = new File(config.getDataDir());
        if (root.exists() && root.isDirectory()){
            if (ArrayUtils.isEmpty(root.listFiles())) return;
            for (File file: Objects.requireNonNull(root.listFiles())){
                if (file.exists() && ! file.isDirectory() && file.delete()){ }
            }

        }
    }

    public void loadDB(){

    }

    public void lsPmem(){
        File root = new File("/pmem/");
        if (root.exists() && root.isDirectory()){
            if (ArrayUtils.isEmpty(root.listFiles())) return;
            for (File file: Objects.requireNonNull(root.listFiles())){
                LOGGER.info("file {}", file.getPath());
            }
        }
    }

    long size = 0;
    int count = 0;
    int offset = 0;
    @Override
    public long append(String topic, int queueId, ByteBuffer data) {
        try {
//            if ("topic78".equals(topic) && queueId == 1369){
//                LOGGER.info(" offset {}, queueId {}, data{}", offset ++, queueId, data);
//            }
//            LOGGER.info("{}, {}, {}", Thread.currentThread().getId(), topic, queueId);
            ++count;
            size += data.capacity();
            if (count % 100000 == 0){
                LOGGER.info("write count {}, size {}, topic size{}", count, size, topics.size());
            }
//            if (count > 10000000){
//                LOGGER.info("stop count {}, size {}", count, size);
//                throw new RuntimeException("stop");
//            }
            return getTopic(topic).write(queueId, data);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return 0;
    }

    int readCount = 0;
    int times = 0;
    boolean read;
    @Override
    public Map<Integer, ByteBuffer> getRange(String name, int queueId, long offset, int fetchNum) {
        try {
            if (!read){
                read = true;
                LOGGER.info("start read");
            }
//            readCount += fetchNum;
//            if (readCount > 200000){
//                int time = ++times;
//                LOGGER.info("read count {}, times {}", readCount, time);
//                readCount = 0;
//            }
            Topic topic = getTopic(name);
            List<ByteBuffer> results = topic.read(queueId, offset, fetchNum);
            if (CollectionUtils.isEmpty(results)){
                return EMPTY;
            }
            Map<Integer, ByteBuffer> byteBuffers = new HashMap<>();
            for(int i = 0; i < results.size(); i ++){
                byteBuffers.put(i, results.get(i));
            }
            return byteBuffers;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized Topic getTopic(String name) throws IOException {
        return topics.computeIfAbsent(name, k -> {
            try {
                int id = Integer.parseInt(name.substring(5));
                return new Topic(name, id, config, cache, aof, cyclicBarrier);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }


}
