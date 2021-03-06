package io.openmessaging.impl;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class Aof {

    private final FileChannel channel;

    public Aof(RandomAccessFile file) {
        this.channel = file.getChannel();
    }

    public long position() throws IOException {
        return channel.position();
    }

    public synchronized long write(ByteBuffer[] buffers) throws IOException {
        long pos = channel.position();
        channel.write(buffers);
        return pos;
    }

    public long writeWithoutSync(ByteBuffer buffer) throws IOException {
        long pos = channel.position();
        channel.write(buffer);
        return pos;
    }

    public synchronized long write(ByteBuffer buffer) throws IOException {
        long pos = channel.position();
        channel.write(buffer);
        return pos;
    }

    public void write(long position, ByteBuffer buffer) throws IOException {
        channel.write(buffer, position);
    }

    public void force() throws IOException {
        this.channel.force(false);
    }

    public int read(long position, ByteBuffer dst) throws IOException {
        return channel.read(dst, position);
    }

    public synchronized int read(ByteBuffer dst) throws IOException {
        return channel.read(dst);
    }

    public FileChannel getChannel(){
        return channel;
    }

}
