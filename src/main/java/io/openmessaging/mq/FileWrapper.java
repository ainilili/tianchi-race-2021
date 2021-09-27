package io.openmessaging.mq;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;

public class FileWrapper {

    private final FileChannel channel;

    public FileWrapper(RandomAccessFile file) {
        this.channel = file.getChannel();
    }

    private void position(long pos) throws IOException {
        if (channel.position() != pos){
            channel.position(pos);
        }
    }

    public synchronized long write(ByteBuffer[] buffers) throws IOException {
        long pos = channel.position();
        channel.write(buffers);
        return pos;
    }

    public void force() throws IOException {
        this.channel.force(false);
    }

    public synchronized int read(long position, ByteBuffer dst) throws IOException {
        return channel.read(dst, position);
    }

    public synchronized int read(ByteBuffer dst) throws IOException {
        return channel.read(dst);
    }

    public FileChannel getChannel(){
        return channel;
    }

}