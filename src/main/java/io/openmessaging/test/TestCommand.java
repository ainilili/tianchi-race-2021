package io.openmessaging.test;

import io.openmessaging.MessageQueue;
import io.openmessaging.consts.Const;
import io.openmessaging.impl.MessageQueueImpl;
import io.openmessaging.model.Config;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Scanner;

public class TestCommand {

    public static void main(String[] args) {
        MessageQueueImpl messageQueue = new MessageQueueImpl(new Config("D:\\test\\nio\\", 64 * Const.K, 1));
        messageQueue.loadDB();
        Scanner in = new Scanner(System.in);
        String topic = "test_command";
        while(true){
            String line = in.nextLine();
            String[] commands = line.split(" ");
            long start = System.currentTimeMillis();
            if (commands[0].equals("set")){
                long offset = messageQueue.append(topic, 1, ByteBuffer.wrap(commands[1].getBytes()));
                System.out.print("new offset " + offset);
            }else{
                Map<Integer, ByteBuffer> result = messageQueue.getRange(topic, 1, Integer.parseInt(commands[1]), 3);
                System.out.print("result " + (result == null ? "null" : new String(result.get(0).array())));
            }
            long end = System.currentTimeMillis();
            System.out.println(", " + commands[0] + " spend " + (end - start) + "ms");
        }


    }

}
