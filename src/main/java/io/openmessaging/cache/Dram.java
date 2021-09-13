package io.openmessaging.cache;

import io.openmessaging.model.FileWrapper;
import io.openmessaging.model.Segment;

import java.nio.ByteBuffer;
import java.util.List;

public class Dram extends AbstractMedium{

    private final List<ByteBuffer> data;

    private final long beginOffset;

    public Dram(List<ByteBuffer> data, long beginOffset) {
        this.data = data;
        this.beginOffset = beginOffset;
    }

    @Override
    public List<ByteBuffer> read(long startOffset, long endOffset) {
        return data.subList((int) (startOffset - beginOffset), (int) (endOffset - beginOffset + 1));
    }

    @Override
    public void write(ByteBuffer buffer) {
        data.add(buffer);
    }

    @Override
    public void clean() {
        data.clear();
    }
}