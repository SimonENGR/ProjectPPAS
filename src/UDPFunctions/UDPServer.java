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
        // Expected message format: "register,uniqueName,role,ipAddress,udpPort,tcpPort"
        String[] tokens = message.split(",");

        if (tokens.length != 6) {
            throw new IllegalArgumentException("Invalid registration message format");
        }

        String uniqueName = tokens[1].trim();
        String role = tokens[2].trim().toLowerCase(); // Normalize role
        String ipAddress = tokens[3].trim();

        int udpPort;
        int tcpPort;
        try {
            udpPort = Integer.parseInt(tokens[4].trim());
            tcpPort = Integer.parseInt(tokens[5].trim());
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
                if (tokens.length >= 1 && tokens[0].trim().equalsIgnoreCase(uniqueName)) {
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

    public void deregisterAccount(String uniqueName) {
        File inputFile = new File(FILE_PATH);
        File tempFile = new File("src/resources/temp_accounts.txt");

        boolean found = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {

            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                // Assuming entry format: "uniqueName,role,RQ#<number>"
                String[] tokens = currentLine.split(",");
                if (tokens.length >= 1 && tokens[0].trim().equalsIgnoreCase(uniqueName)) {
                    // Skip the line that matches the uniqueName (i.e., deregister it)
                    found = true;
                    continue;
                }
                writer.println(currentLine);
            }
        } catch (IOException e) {
            System.err.println("Error processing accounts file: " + e.getMessage());
            return;
        }

        // Replace the original file with the temp file.
        if (!inputFile.delete()) {
            System.err.println("Could not delete original accounts file.");
            return;
        }
        if (!tempFile.renameTo(inputFile)) {
            System.err.println("Could not rename temporary file.");
            return;
        }

        if (found) {
            System.out.println("Account deregistered: " + uniqueName);
        } else {
            System.err.println("Account not found: " + uniqueName);
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

            String[] tokens = msg.split(",");
            if (tokens.length < 2) {
                System.err.println("Invalid message format.");
                continue;
            }

            String action = tokens[0].trim().toLowerCase();

            if (action.equals("register")) {
                // Increment request number for registration.
                int requestNumber = requestCounter.getAndIncrement();
                try {
                    RegistrationInfo regInfo = server.parseRegistrationMessage(msg);
                    System.out.println("Parsed Registration Information: " + regInfo);
                    server.registerAccount(regInfo, requestNumber);
                } catch (IllegalArgumentException e) {
                    System.err.println("Error parsing registration message: " + e.getMessage());
                }
            } else if (action.equals("deregister")) {
                // For deregistration, we expect format: "deregister,uniqueName"
                String uniqueName = tokens[1].trim();
                server.deregisterAccount(uniqueName);
            } else {
                System.err.println("Unknown action: " + action);
            }

            receive = new byte[65535];
        }
        ds.close();
    }
}
