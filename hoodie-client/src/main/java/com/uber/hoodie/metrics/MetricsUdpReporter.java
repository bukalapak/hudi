package com.uber.hoodie.metrics;

import com.uber.hoodie.config.HoodieWriteConfig;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;

/**
 * Class that connects to the UDP socket, and send metrics to that server.
 *
 * @author david.christianto
 */
public class MetricsUdpReporter extends MetricsReporter {

    private static Logger logger = LogManager.getLogger(MetricsGraphiteReporter.class);

    private final HoodieWriteConfig config;
    private DatagramSocket socket;
    private InetAddress address;

    public MetricsUdpReporter(HoodieWriteConfig config) {
        this.config = config;
        try {
            this.socket = new DatagramSocket();
            this.address = InetAddress.getByName(config.getUdpServerHost());
        } catch (SocketException | UnknownHostException ex) {
            logger.error("Failed to init socket or invalid UDP server host!", ex);
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void report() {
    }

    @Override
    public Closeable getReporter() {
        return null;
    }

    /**
     * Method used to send message to UDP server.
     *
     * @param message Message to send.
     */
    public void sendMessage(String message) {
        try {
            byte[] buf = message.getBytes();
            socket.send(new DatagramPacket(buf, buf.length, address, config.getUdpServerPort()));
        } catch (IOException ex) {
            logger.error("Failed to send message to UDP server!", ex);
        }
    }
}
