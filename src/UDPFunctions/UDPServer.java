package UDPFunctions;

import Utils.RegistrationInfo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.BufferedWriter;

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

    // Registers the account by appending the client name and role to the accounts.txt file
    public void registerAccount(RegistrationInfo regInfo) {

        String filePath = "src/resources/accounts.txt";
        String entry = regInfo.getUniqueName() + "," + regInfo.getRole();
        try (FileWriter fw = new FileWriter(filePath, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            out.println(entry);
            System.out.println("Account registered: " + entry);
        } catch (IOException e) {
            System.err.println("Error writing to accounts file: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        // Create a DatagramSocket to listen on port 420
        DatagramSocket ds = new DatagramSocket(420);
        byte[] receive = new byte[65535];

        DatagramPacket dpReceive;

        UDPServer server = new UDPServer();  // Create an instance of UDPServer for parsing

        System.out.println("Server listening on port 420...");

        while (true) {
            // Create a DatagramPacket to receive data
            dpReceive = new DatagramPacket(receive, receive.length);
            ds.receive(dpReceive);

            // Convert byte array to string and print received data
            String msg = new String(dpReceive.getData(), 0, dpReceive.getLength());
            System.out.println("Received message: " + msg);

            // Exit if client sends "bye"
            if (msg.equalsIgnoreCase("bye")) {
                System.out.println("Client sent bye...EXITING");
                break;
            }

            // Try to parse the registration message
            try {
                RegistrationInfo regInfo = server.parseRegistrationMessage(msg);
                System.out.println("Parsed Registration Information: " + regInfo);
                server.registerAccount(regInfo);
            } catch (IllegalArgumentException e) {
                System.err.println("Error parsing registration message: " + e.getMessage());
                // Optionally, send an error response back to the client
            }

            // Clear the buffer for the next message
            receive = new byte[65535];
        }
        ds.close();
    }
}
