package io.openmessaging.cache;

import com.sun.org.apache.bcel.internal.generic.IF_ACMPEQ;
import io.openmessaging.utils.BufferUtils;
import io.openmessaging.utils.CollectionUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class Dram extends Storage {

    private List<ByteBuffer> data;

    private long beginOffset;

    private int idx;

    public Dram(boolean direct) {
        this.idx = -1;
        this.direct = direct;
        this.data = new ArrayList<>();
    }

    @Override
    public List<ByteBuffer> read(long startOffset, long endOffset) {
        if (CollectionUtils.isEmpty(data)){
            return null;
        }
        int startIndex = (int) (startOffset - beginOffset);
        int endIndex = (int) (endOffset - beginOffset);
        return new ArrayList<>(data.subList(startIndex, endIndex + 1));
    }

    @Override
    public List<ByteBuffer> load() {
        return data;
    }

    @Override
    public void write(ByteBuffer byteBuffer) {
        data.add(ByteBuffer.wrap(byteBuffer.array()));
    }

    @Override
    public void reset(int idx, List<ByteBuffer> buffers, long beginOffset) {
        this.idx = idx;
        this.data = buffers;
        this.beginOffset = beginOffset;
    }

    @Override
    public long getIdx() {
        return idx;
    }

    @Override
    public void clean() {
        this.data = null;
    }


}
