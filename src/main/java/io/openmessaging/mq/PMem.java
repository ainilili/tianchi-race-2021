package io.openmessaging.mq;

import com.intel.pmem.llpl.AnyMemoryBlock;
import com.intel.pmem.llpl.Heap;
import io.openmessaging.utils.CollectionUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class PMem extends Data {

    private final Block block;

    private byte[] ext;

    private int size;

    public PMem(Block block, long position, int capacity) {
        super(capacity);
        this.position = position;
        this.block = block;
        this.size = capacity;
    }

    @Override
    public ByteBuffer get() {
        Monitor.readMemCount ++;
        int extSize = ext == null ? 0 : ext.length;
        byte[] data = block.read(position, size - extSize);
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put(data);
        if (extSize > 0){
            buffer.put(ext);
        }
        buffer.flip();
        return buffer;
    }

    @Override
    public void set(ByteBuffer buffer) {
        Monitor.writeMemCount ++;
        this.size = buffer.limit();
        byte[] bytes = new byte[Math.min(buffer.limit(), capacity)];
        buffer.get(bytes);
        block.write(position, bytes);

        if (buffer.remaining() > 0){
            ext = new byte[buffer.remaining()];
            buffer.get(ext);
        }
    }

    @Override
    public void clear() {
        this.ext = null;
    }

}
