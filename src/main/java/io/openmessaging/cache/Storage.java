package io.openmessaging.cache;

import io.openmessaging.model.FileWrapper;
import io.openmessaging.model.Segment;

import java.nio.ByteBuffer;
import java.util.List;

public abstract class Storage {

    protected long expire;

    public abstract List<ByteBuffer> read(long startOffset, long endOffset);

    public abstract void write(byte[] bytes);

    public abstract void reset(int idx, List<ByteBuffer> buffers, long beginOffset);

    public abstract long getIdx();

    public abstract void clean();

    public void killed(){
        this.expire = System.currentTimeMillis() + 3000;
    }

    public long expire(){
        return expire;
    }

}
