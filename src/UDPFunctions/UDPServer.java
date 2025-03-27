package UDPFunctions;

import Utils.RegistrationInfo;
import Utils.ItemRegistry;
import Utils.FileUtils;
import Utils.NetworkUtils;
import Utils.MessageParser;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

public class UDPServer {

    private static final int MAX_USERS = 10;
    private static final String FILE_PATH = "src/resources/accounts.txt";
    private static final String ITEM_FILE = "src/resources/items.txt";

    private static AtomicInteger requestCounter = new AtomicInteger(1);

    public void registerAccount(RegistrationInfo regInfo, int requestNumber, DatagramSocket ds, DatagramPacket dpReceive) {
        String confirmationMessage;
        boolean success = true;

        if (FileUtils.isCapacityReached(FILE_PATH, MAX_USERS)) {
            confirmationMessage = "Register-denied RQ#" + requestNumber + " Reason: Capacity reached";
            success = false;
        } else if (FileUtils.isDuplicateName(FILE_PATH, regInfo.getUniqueName())) {
            confirmationMessage = "Register-denied RQ#" + requestNumber + " Reason: Duplicate name";
            success = false;
        } else {
            String entry = regInfo.getUniqueName() + "," + regInfo.getRole() + ",RQ#" + requestNumber;
            if (FileUtils.appendLineToFile(FILE_PATH, entry)) {
                System.out.println("Account registered: " + entry);
                confirmationMessage = "Registered,RQ#" + requestNumber;
            } else {
                confirmationMessage = "Register-denied RQ#" + requestNumber + " Reason: Internal server error";
                success = false;
            }
        }

        InetAddress clientIP = dpReceive.getAddress();
        int clientPort = dpReceive.getPort();

        NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, confirmationMessage);

        if (!success) {
            System.err.println("Registration failed for user: " + regInfo.getUniqueName());
        }
    }

    public void deregisterAccount(String uniqueName, DatagramSocket ds, InetAddress clientIP, int clientPort, int requestNumber) {
        String confirmationMessage = FileUtils.removeAccountByName(FILE_PATH, uniqueName, requestNumber);
        NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, confirmationMessage);
    }

    public void listItem(String message, int requestNumber, DatagramSocket ds, InetAddress clientIP, int clientPort) {
        String[] tokens = message.split(",");
        if (tokens.length != 5) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "LIST-DENIED RQ#" + requestNumber + " Reason: Invalid format");
            return;
        }

        String itemName = tokens[1].trim();
        String description = tokens[2].trim();
        double startingPrice;
        long duration;

        try {
            startingPrice = Double.parseDouble(tokens[3].trim());
            long durationMinutes = Long.parseLong(tokens[4].trim());
            duration = durationMinutes * 60_000;
        } catch (NumberFormatException e) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "LIST-DENIED RQ#" + requestNumber + " Reason: Invalid price or duration");
            return;
        }

        if (FileUtils.isItemLimitReached(ITEM_FILE, 10)) {  // ← 10 is just an example
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort,
                    "LIST-DENIED RQ#" + requestNumber + " Reason: Item limit reached");
            return;
        }

        if (FileUtils.isDuplicateItem(ITEM_FILE, itemName)) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "LIST-DENIED RQ#" + requestNumber + " Reason: Item already listed");
            return;
        }

        ItemRegistry newItem = new ItemRegistry(itemName, description, startingPrice, duration, requestNumber);
        if (FileUtils.appendLineToFile(ITEM_FILE, newItem.toString())) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "ITEM_LISTED RQ#" + requestNumber);
            NetworkUtils.broadcastToBuyers("NEW_ITEM RQ#" + requestNumber + " " + itemName + " " + startingPrice, ds, FILE_PATH);
        } else {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "LIST-DENIED RQ#" + requestNumber + " Reason: Internal server error");
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

            if (MessageParser.isGetAllItemsRequest(msg)) {
                String items = FileUtils.readFileAsString(ITEM_FILE);
                NetworkUtils.sendMessageToClient(ds, dpReceive.getAddress(), dpReceive.getPort(), items);
                receive = new byte[65535];
                continue;
            }

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
                    RegistrationInfo regInfo = MessageParser.parseRegistrationMessage(msg);
                    System.out.println("Parsed Registration Information: " + regInfo);
                    server.registerAccount(regInfo, requestNumber, ds, dpReceive);
                } catch (IllegalArgumentException e) {
                    System.err.println("Error parsing registration message: " + e.getMessage());
                    String errorMessage = "Register-denied RQ#" + requestNumber + " Reason: " + e.getMessage();
                    NetworkUtils.sendMessageToClient(ds, dpReceive.getAddress(), dpReceive.getPort(), errorMessage);
                }
            } else if (action.equals("deregister")) {
                int requestNumber = requestCounter.getAndIncrement();
                String uniqueName = tokens[1].trim();
                server.deregisterAccount(uniqueName, ds, dpReceive.getAddress(), dpReceive.getPort(), requestNumber);
            } else if (action.equals("list_item")) {
                int requestNumber = requestCounter.getAndIncrement();
                server.listItem(msg, requestNumber, ds, dpReceive.getAddress(), dpReceive.getPort());
            }
            else {
                System.err.println("Unknown action: " + action);
            }

            receive = new byte[65535];
        }
        ds.close();
    }
}
