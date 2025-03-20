package UDPFunctions;

import Utils.RegistrationInfo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UDPServer {

    public RegistrationInfo parseRegistrationMessage(String message) throws IllegalArgumentException {
        // Expected message format: "uniqueName,role,ipAddress,udpPort,tcpPort"
        String[] tokens = message.split(",");

        if (tokens.length != 5) {
            throw new IllegalArgumentException("Invalid registration message format");
        }

        String uniqueName = tokens[0].trim();
        String role = tokens[1].trim().toLowerCase(); // Normalize role
        String ipAddress = tokens[2].trim();

        int udpPort;
        int tcpPort;
        try {
            udpPort = Integer.parseInt(tokens[3].trim());
            tcpPort = Integer.parseInt(tokens[4].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("UDP and TCP ports must be integers", e);
        }

        return new RegistrationInfo(uniqueName, role, ipAddress, udpPort, tcpPort);
    }


    public static void main(String[] args) throws IOException {
        // Create a DatagramSocket to listen on port 420
        DatagramSocket ds = new DatagramSocket(420);
        byte[] receive = new byte[65535];

        DatagramPacket dpReceive = null;

        while (true) {
            // Create a DatagramPacket to receive data
            dpReceive = new DatagramPacket(receive, receive.length);
            ds.receive(dpReceive);

            // Convert byte array to string and print received data
            String msg = new String(receive, 0, dpReceive.getLength());
            System.out.println("Client: " + msg);

            // Exit if client sends "bye"
            if (msg.equals("bye")) {
                System.out.println("Client sent bye...EXITING");
                break;
            }

            // Clear buffer
            receive = new byte[65535];
        }
        ds.close();
    }
}
