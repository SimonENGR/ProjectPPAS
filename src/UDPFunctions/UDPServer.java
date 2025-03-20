package UDPFunctions;

import Utils.RegistrationInfo;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.atomic.AtomicInteger;

public class UDPServer {

    private static final int MAX_USERS = 10;
    private static final String FILE_PATH = "src/resources/accounts.txt";

    // Thread-safe counter for request numbers.
    private static AtomicInteger requestCounter = new AtomicInteger(1);

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

    private boolean isCapacityReached() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            return false;
        }
        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            while (br.readLine() != null) {
                count++;
            }
        } catch (IOException e) {
            System.err.println("Error reading accounts file: " + e.getMessage());
            return true;
        }
        return count >= MAX_USERS;
    }

    private boolean isDuplicateName(String uniqueName) {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            return false;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length >= 2 && tokens[1].trim().equalsIgnoreCase(uniqueName)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading accounts file: " + e.getMessage());
            return true;
        }
        return false;
    }

    public void registerAccount(RegistrationInfo regInfo, int requestNumber) {
        if (isCapacityReached()) {
            System.err.println("Registration denied: maximum user capacity reached.");
            return;
        }
        if (isDuplicateName(regInfo.getUniqueName())) {
            System.err.println("Registration denied: unique name '" + regInfo.getUniqueName() + "' is already registered.");
            return;
        }

        String filePath = "src/resources/accounts.txt";

        String entry = regInfo.getUniqueName() + "," + regInfo.getRole() + ",RQ#" + requestNumber;

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
        DatagramSocket ds = new DatagramSocket(420);
        byte[] receive = new byte[65535];

        DatagramPacket dpReceive;

        UDPServer server = new UDPServer();

        System.out.println("Server listening on port 420...");

        while (true) {
            dpReceive = new DatagramPacket(receive, receive.length);
            ds.receive(dpReceive);

            String msg = new String(dpReceive.getData(), 0, dpReceive.getLength());
            System.out.println("Received message: " + msg);

            if (msg.equalsIgnoreCase("bye")) {
                System.out.println("Client sent bye...EXITING");
                break;
            }

            // Increment request number here, since a request has been received
            int requestNumber = requestCounter.getAndIncrement();

            try {
                RegistrationInfo regInfo = server.parseRegistrationMessage(msg);
                System.out.println("Parsed Registration Information: " + regInfo);
                server.registerAccount(regInfo, requestNumber);
            } catch (IllegalArgumentException e) {
                System.err.println("Error parsing registration message: " + e.getMessage());
            }

            receive = new byte[65535];
        }
        ds.close();
    }
}
