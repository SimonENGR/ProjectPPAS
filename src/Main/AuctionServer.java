package Main;

import TCPFunctions.TCPServer;
import UDPFunctions.UDPServer;

import java.io.IOException;

public class AuctionServer {
    public static void main(String[] args) {
        int udpPort = 420; // For client requests and auction updates
        int tcpPort = 443;   // For end-of-auction result notifications

        // Start UDP server
        Thread udpThread = new Thread(() -> {
            try {
                UDPServer udpServer = new UDPServer();
                udpServer.start(udpPort); // Your existing method to start UDP logic
            } catch (IOException e) {
                System.err.println("UDP Server failed to start: " + e.getMessage());
            }
        });
        udpThread.start();

        // Start TCP server
        Thread tcpThread = new Thread(() -> {
            try {
                TCPServer tcpServer = new TCPServer("0.0.0.0", tcpPort); // 0.0.0.0 to bind all IPs
                tcpServer.start(); // Blocking method that accepts connections
            } catch (Exception e) {
                System.err.println("TCP Server failed to start: " + e.getMessage());
            }
        });

        // Run both concurrently
        udpThread.start();
        tcpThread.start();

        System.out.println("AuctionServer is running both UDP and TCP servers.");
    }
}
