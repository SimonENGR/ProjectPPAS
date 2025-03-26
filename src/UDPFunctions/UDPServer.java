package UDPFunctions;

import Utils.RegistrationInfo;
import Utils.ItemRegistry;
import java.io.IOException;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.InetAddress;
import java.util.List;
import java.util.ArrayList;

public class UDPServer {

    private static final int MAX_USERS = 10;
    private static final String FILE_PATH = "src/resources/accounts.txt";
    private static final String ITEM_FILE = "src/resources/items.txt";

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

    public void registerAccount(RegistrationInfo regInfo, int requestNumber, DatagramSocket ds, DatagramPacket dpReceive) {
        String confirmationMessage;
        boolean success = true;

        if (isCapacityReached()) {
            confirmationMessage = "Register-denied RQ#" + requestNumber + " Reason: Capacity reached";
            success = false;
        } else if (isDuplicateName(regInfo.getUniqueName())) {
            confirmationMessage = "Register-denied RQ#" + requestNumber + " Reason: Duplicate name";
            success = false;
        } else {
            String entry = regInfo.getUniqueName() + "," + regInfo.getRole() + ",RQ#" + requestNumber;
            try (FileWriter fw = new FileWriter(FILE_PATH, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {

                out.println(entry);
                System.out.println("Account registered: " + entry);
                confirmationMessage = "Registered,RQ#" + requestNumber;
            } catch (IOException e) {
                confirmationMessage = "Register-denied RQ#" + requestNumber + " Reason: Internal server error";
                success = false;
                System.err.println("Error writing to accounts file: " + e.getMessage());
            }
        }

        // Send confirmation or denial message to client
        try {
            InetAddress clientIP = dpReceive.getAddress();
            int clientPort = dpReceive.getPort();

            byte[] buf = confirmationMessage.getBytes();
            DatagramPacket dpSend = new DatagramPacket(buf, buf.length, clientIP, clientPort);

            try {
                Thread.sleep(200);  // 200ms delay (adjust if needed)
            } catch (InterruptedException e) {
                System.err.println("Sleep interrupted: " + e.getMessage());
            }

            System.out.println("Sending registration message to client: " + confirmationMessage);
            ds.send(dpSend);
            System.out.println("Sending response to " + clientIP + ":" + clientPort);
        } catch (IOException e) {
            System.err.println("Error sending response to client: " + e.getMessage());
        }
        if (!success) {
            System.err.println("Registration failed for user: " + regInfo.getUniqueName());
        }
    }

    public void deregisterAccount(String uniqueName, DatagramSocket ds, InetAddress clientIP, int clientPort, int requestNumber) {
        File inputFile = new File(FILE_PATH);
        File tempFile = new File("src/resources/temp_accounts.txt");

        boolean found = false;
        String confirmationMessage;

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
            confirmationMessage = "Deregister-denied RQ#" + requestNumber + " Reason: Internal server error";
            sendMessageToClient(ds, clientIP, clientPort, confirmationMessage);
            return;
        }

        // Replace the original file with the temp file.
        if (!inputFile.delete() || !tempFile.renameTo(inputFile)) {
            confirmationMessage = "Deregister-denied RQ#" + requestNumber + " Reason: File processing error";
            sendMessageToClient(ds, clientIP, clientPort, confirmationMessage);
            return;
        }

        if (found) {
            confirmationMessage = "Deregistered RQ#" + requestNumber;
            System.out.println("Account deregistered: " + uniqueName);
        } else {
            confirmationMessage = "Deregister-denied RQ#" + requestNumber + " Reason: Account not found";
            System.err.println("Account not found: " + uniqueName);
        }

        // Send response to client
        sendMessageToClient(ds, clientIP, clientPort, confirmationMessage);
    }

    private void sendMessageToClient(DatagramSocket ds, InetAddress clientIP, int clientPort, String message) {
        try {
            byte[] buf = message.getBytes();
            DatagramPacket dpSend = new DatagramPacket(buf, buf.length, clientIP, clientPort);
            System.out.println("Sending message to client: " + message);
            ds.send(dpSend);
        } catch (IOException e) {
            System.err.println("Error sending response to client: " + e.getMessage());
        }
    }

    private boolean isDuplicateItem(String itemName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(ITEM_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens[0].trim().equalsIgnoreCase(itemName)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading items file: " + e.getMessage());
        }
        return false;
    }

    private boolean writeItemToFile(ItemRegistry item) {
        try (FileWriter fw = new FileWriter(ITEM_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            out.println(item.toString());
            return true;
        } catch (IOException e) {
            System.err.println("Error writing to items file: " + e.getMessage());
            return false;
        }
    }

    public void listItem(String message, int requestNumber, DatagramSocket ds, InetAddress clientIP, int clientPort) {
        String[] tokens = message.split(",");
        if (tokens.length != 5) {
            sendMessageToClient(ds, clientIP, clientPort, "LIST-DENIED RQ#" + requestNumber + " Reason: Invalid format");
            return;
        }

        String itemName = tokens[1].trim();
        String description = tokens[2].trim();
        double startingPrice;
        long duration;

        try {
            startingPrice = Double.parseDouble(tokens[3].trim());
            duration = Long.parseLong(tokens[4].trim());
        } catch (NumberFormatException e) {
            sendMessageToClient(ds, clientIP, clientPort, "LIST-DENIED RQ#" + requestNumber + " Reason: Invalid price or duration");
            return;
        }

        if (isDuplicateItem(itemName)) {
            sendMessageToClient(ds, clientIP, clientPort, "LIST-DENIED RQ#" + requestNumber + " Reason: Item already listed");
            return;
        }

        ItemRegistry newItem = new ItemRegistry(itemName, description, startingPrice, duration);
        if (writeItemToFile(newItem)) {
            sendMessageToClient(ds, clientIP, clientPort, "ITEM_LISTED RQ#" + requestNumber);
            broadcastToBuyers("NEW_ITEM RQ#" + requestNumber + " " + itemName + " " + startingPrice, ds);
        } else {
            sendMessageToClient(ds, clientIP, clientPort, "LIST-DENIED RQ#" + requestNumber + " Reason: Internal server error");
        }
    }

    private void broadcastToBuyers(String message, DatagramSocket ds) {
        List<RegistrationInfo> buyers = getAllRegisteredBuyers(); // Read from file
        for (RegistrationInfo buyer : buyers) {
            if (buyer.getRole().equalsIgnoreCase("buyer")) {
                try {
                    byte[] buf = message.getBytes();
                    DatagramPacket packet = new DatagramPacket(buf, buf.length,
                            InetAddress.getByName(buyer.getIpAddress()), buyer.getUdpPort());
                    ds.send(packet);
                } catch (IOException e) {
                    System.err.println("Failed to send update to buyer: " + e.getMessage());
                }
            }
        }
    }

    private List<RegistrationInfo> getAllRegisteredBuyers() {
        List<RegistrationInfo> buyers = new ArrayList<>();
        File file = new File("src/resources/accounts.txt");

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length >= 3) {
                    String uniqueName = tokens[0].trim();
                    String role = tokens[1].trim();
                    int udpPort = Integer.parseInt(tokens[2].split("#")[1].trim());

                    if (role.equalsIgnoreCase("buyer")) {
                        buyers.add(new RegistrationInfo(uniqueName, role, "127.0.0.1", udpPort, 0)); // IP and TCP port are placeholders
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading accounts file: " + e.getMessage());
        }

        return buyers;
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
                int requestNumber = requestCounter.getAndIncrement();
                try {
                    RegistrationInfo regInfo = server.parseRegistrationMessage(msg);
                    System.out.println("Parsed Registration Information: " + regInfo);
                    server.registerAccount(regInfo, requestNumber, ds,dpReceive);
                } catch (IllegalArgumentException e) {
                    System.err.println("Error parsing registration message: " + e.getMessage());
                    String errorMessage = "Register-denied RQ#" + requestNumber + " Reason: " + e.getMessage();
                    // Get client's IP and port from the received packet.
                    InetAddress clientIP = dpReceive.getAddress();
                    int clientPort = dpReceive.getPort();
                    try {
                        DatagramPacket dpSend = new DatagramPacket(errorMessage.getBytes(), errorMessage.getBytes().length, clientIP, clientPort);
                        ds.send(dpSend);
                    } catch (IOException ex) {
                        System.err.println("Error sending error message: " + ex.getMessage());
                    }
                }
            } else if (action.equals("deregister")) {
                int requestNumber = requestCounter.getAndIncrement();
                InetAddress clientIP = dpReceive.getAddress();
                int clientPort = dpReceive.getPort();
                String uniqueName = tokens[1].trim();
                server.deregisterAccount(uniqueName, ds, clientIP, clientPort, requestNumber);
            } else {
                System.err.println("Unknown action: " + action);
            }

            if (action.equals("list_item")) {
                int requestNumber = requestCounter.getAndIncrement();
                server.listItem(msg, requestNumber, ds, dpReceive.getAddress(), dpReceive.getPort());
            }

            receive = new byte[65535];
        }
        ds.close();
    }
}