package io.moquette.parser.netty.performance;


import org.eclipse.jetty.toolchain.perf.PlatformTimer;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

class  BenchmarkPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(BenchmarkPublisher.class);

    static class PublishCallback implements IMqttActionListener {
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
            LOG.error("PUB: Publish failed: " + exception);
            exception.printStackTrace();
            System.exit(2);
        }

        public void onSuccess(IMqttToken asyncActionToken) {
        }
    }

    static class ExitTopicCallback implements IMqttActionListener {

        private final long startedTime;
        private final long numToSend;
        private final IMqttAsyncClient connection;
        private final CountDownLatch stopLatch;

        ExitTopicCallback(long startedTime, long numToSend, IMqttAsyncClient connection, CountDownLatch stopLatch) {
            this.startedTime = startedTime;
            this.numToSend = numToSend;
            this.connection = connection;
            this.stopLatch = stopLatch;
        }

        public void onSuccess(IMqttToken asyncActionToken) {
            long stopTime = System.currentTimeMillis();
            long spentTime = stopTime - startedTime;
            LOG.info("PUB: published in {} ms", spentTime);

//            try {
//                this.connection.disconnect();
//            } catch (MqttException mex) {
//                LOG.error("Disconnect error", mex);
//            }
            double msgPerSec = (numToSend / spentTime) * 1000;
            LOG.info( "PUB: speed {} msg/sec", msgPerSec);
            this.stopLatch.countDown();
        }

        public void onFailure(IMqttToken asyncActionToken, Throwable th) {
            LOG.error("PUB: Publish failed: " + th);
            System.exit(2);
        }
    }

    private int qos = 0;
    private boolean retain = false;
    private final int numToSend;
    private final int messagesPerSecond;
    private final String dialog_id;
    private final IMqttAsyncClient client;
    private CountDownLatch m_latch;

    public BenchmarkPublisher(MqttAsyncClient client, int numToSend, int messagesPerSecond, String dialog_id) {
        this.client = client;
        this.numToSend = numToSend;
        this.messagesPerSecond = messagesPerSecond;
        this.dialog_id = dialog_id;
    }

    public void connect() throws MqttException {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        this.client.connect(connectOptions, null, new IMqttActionListener() {
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                LOG.error("PUB: connect fail", exception);
                System.exit(2);
            }

            public void onSuccess(IMqttToken asyncActionToken) {
                LOG.info("PUB: Successfully connected to server");
            }
        });

        this.m_latch = new CountDownLatch(1);
    }

    public void firePublishes() throws MqttException {
        long pauseMicroseconds = (int)((1.0 / messagesPerSecond) * 1000 * 1000);
        LOG.info("PUB: Pause over the each message sent {} microsecs", pauseMicroseconds);

        LOG.info("PUB: publishing..");
        CountDownLatch m_latch = new CountDownLatch(1);
        final long startTime = System.currentTimeMillis();

        //initialize the timer
        PlatformTimer timer = PlatformTimer.detect();
        IMqttActionListener pubCallback = new PublishCallback();
        for (int i=0; i < numToSend; i++) {
            long nanos = System.nanoTime();
            byte[] message = ("Hello world!!-" + nanos).getBytes();
            this.client.publish("/topic" + dialog_id, message, qos, retain, null, pubCallback);
            timer.sleep(pauseMicroseconds);
        }

        IMqttActionListener exitCallback = new ExitTopicCallback(startTime, numToSend, this.client, m_latch);
        long nanosExit = System.nanoTime();
        byte[] exitMessage = ("Hello world!!-" + nanosExit).getBytes();
        this.client.publish("/exit" + dialog_id, exitMessage, qos, retain, null, exitCallback);
    }


    public void waitFinish() throws InterruptedException {
        m_latch.await();
        try {
            this.client.disconnect();
        } catch (MqttException mex) {
            LOG.error("Disconnect error", mex);
        }
    }

}
