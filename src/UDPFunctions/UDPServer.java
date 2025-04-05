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
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;

public class UDPServer {

    private static final int MAX_USERS = 10;
    private static final String FILE_PATH = "src/resources/accounts.txt";
    private static final String ITEM_FILE = "src/resources/items.txt";
    private static final String SUBSCRIPTION_FILE = "src/resources/subscriptions.txt";
    private static final String ACTIVE_AUCTIONS_FILE = "src/resources/activeAuctions.txt";


    private static AtomicInteger requestCounter = new AtomicInteger(FileUtils.readLastRequestNumber("src/resources/last_rq.txt") + 1);
    //private static ConcurrentHashMap<String, ItemRegistry> activeAuctions = new ConcurrentHashMap<>();

    private static ReentrantLock auctionLock = new ReentrantLock();

    public void placeBid(String message, int requestNumber, DatagramSocket ds, InetAddress clientIP, int clientPort) {
        String[] tokens = message.split(",");
        if (tokens.length != 4) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + requestNumber + " Reason: Invalid format");
            return;
        }
        String itemName = tokens[1].trim();
        String bidderName = tokens[2].trim();
        double bidAmount;
        try {
            bidAmount = Double.parseDouble(tokens[3].trim());
        } catch (NumberFormatException e) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + requestNumber + " Reason: Invalid bid amount");
            return;
        }

        auctionLock.lock();
        try {
            String auctionLine = FileUtils.getAuctionLine(ACTIVE_AUCTIONS_FILE, itemName);
            if (auctionLine == null) {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + requestNumber + " Reason: Item not found");
                return;
            }
            String[] auctionTokens = auctionLine.split(",");
            if (auctionTokens.length < 6) {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + requestNumber + " Reason: Auction data corrupted");
                return;
            }
            double currentBid = Double.parseDouble(auctionTokens[3].trim());
            if (bidAmount <= currentBid) {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + requestNumber + " Reason: Bid too low");
                return;
            }
            String updatedAuctionLine = String.format("%s,%s,%s,%.2f,%s,%s",
                    auctionTokens[0].trim(),
                    auctionTokens[1].trim(),
                    auctionTokens[2].trim(),
                    bidAmount,
                    auctionTokens[4].trim(),
                    auctionTokens[5].trim());
            if (FileUtils.updateAuctionLine(ACTIVE_AUCTIONS_FILE, itemName, updatedAuctionLine)) {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-ACCEPTED RQ#" + requestNumber);
                broadcastAuctionAnnouncement(updatedAuctionLine, ds);
            } else {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + requestNumber + " Reason: File update error");
            }
        } finally {
            auctionLock.unlock();
        }
    }

    public void startAuctionBroadcast(ItemRegistry item, DatagramSocket ds) {
        long auctionEndTime = System.currentTimeMillis() + item.getDuration();

        while (System.currentTimeMillis() < auctionEndTime) {
            try {
                Thread.sleep(30_000);

                List<RegistrationInfo> subscribedBuyers = FileUtils.getSubscribersForItem(SUBSCRIPTION_FILE, item.getItemName());

                String message = String.format("AUCTION_UPDATE %s %s %s %.2f %d",
                        item.getRequestNumber(),
                        item.getItemName(),
                        item.getDescription(),
                        item.getStartingPrice(),
                        (auctionEndTime - System.currentTimeMillis()) / 60000
                );

                for (RegistrationInfo buyer : subscribedBuyers) {
                    InetAddress address = InetAddress.getByName(buyer.getIpAddress());
                    NetworkUtils.sendMessageToClient(ds, address, buyer.getUdpPort(), message);
                }
            } catch (InterruptedException | UnknownHostException e) {
                System.err.println("Error during auction broadcast: " + e.getMessage());
            }
        }

        endAuction(item, ds);
    }

    public void endAuction(ItemRegistry item, DatagramSocket ds) {
        List<RegistrationInfo> subscribedBuyers = FileUtils.getSubscribersForItem(SUBSCRIPTION_FILE, item.getItemName());

        String message = String.format("AUCTION_ENDED %s %s %s %.2f %d",
                item.getRequestNumber(),
                item.getItemName(),
                item.getDescription(),
                item.getStartingPrice(),
                0
        );

        for (RegistrationInfo buyer : subscribedBuyers) {
            try {
                InetAddress address = InetAddress.getByName(buyer.getIpAddress());
                NetworkUtils.sendMessageToClient(ds, address, buyer.getUdpPort(), message);
            } catch (UnknownHostException e) {
                System.err.println("Error during auction end broadcast: " + e.getMessage());
            }
        }

        auctionLock.lock();
        try {
            if (FileUtils.removeItemFromFile(ACTIVE_AUCTIONS_FILE, item.getItemName())) {
                System.out.println("Auction for item '" + item.getItemName() + "' removed from file.");
            } else {
                System.err.println("Failed to remove auction for item '" + item.getItemName() + "'.");
            }
        } finally {
            auctionLock.unlock();
        }
    }

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

        if (FileUtils.isItemLimitReached(ACTIVE_AUCTIONS_FILE, 10)) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "LIST-DENIED RQ#" + requestNumber + " Reason: Item limit reached");
            return;
        }

        if (FileUtils.isDuplicateItem(ACTIVE_AUCTIONS_FILE, itemName)) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "LIST-DENIED RQ#" + requestNumber + " Reason: Item already listed");
            return;
        }

        ItemRegistry newItem = new ItemRegistry(itemName, description, startingPrice, duration, requestNumber);

        auctionLock.lock();
        try {
            if (FileUtils.appendLineToFile(ACTIVE_AUCTIONS_FILE, newItem.toCSV())) {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "ITEM_LISTED RQ#" + requestNumber);
                broadcastAuctionAnnouncement(newItem.toCSV(), ds);
                new Thread(() -> startAuctionBroadcast(newItem, ds)).start();
            } else {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "LIST-DENIED RQ#" + requestNumber + " Reason: Internal server error");
            }
        } finally {
            auctionLock.unlock();
        }
    }

    public void handleSubscribe(String message, int requestNumber, DatagramSocket ds, InetAddress clientIP, int clientPort) {
        String[] tokens = message.split(",", 4);
        if (tokens.length != 4) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "SUBSCRIPTION-DENIED RQ#" + requestNumber + " Reason: Invalid format");
            return;
        }

        String rqNum = tokens[1].trim();
        String itemName = tokens[2].trim();
        String buyerName = tokens[3].trim();

        RegistrationInfo buyer = new RegistrationInfo(buyerName, "buyer", clientIP.getHostAddress(), clientPort, 0);

        if (FileUtils.isAlreadySubscribed(SUBSCRIPTION_FILE, itemName, buyer)) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "SUBSCRIPTION-DENIED " + rqNum + " Reason: Already subscribed");
            return;
        }

        boolean written = FileUtils.addSubscription(SUBSCRIPTION_FILE, itemName, buyer);
        if (written) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "SUBSCRIBED " + rqNum);
        } else {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "SUBSCRIPTION-DENIED " + rqNum + " Reason: Internal server error");
        }
    }

    public void handleDeSubscribe(String message, int requestNumber, DatagramSocket ds, InetAddress clientIP, int clientPort) {
        String[] tokens = message.split(",", 4);
        if (tokens.length != 4) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "UNSUBSCRIBE-DENIED RQ#" + requestNumber + " Reason: Invalid format");
            return;
        }

        String rqNum = tokens[1].trim();
        String itemName = tokens[2].trim();
        String buyerName = tokens[3].trim();
        RegistrationInfo buyer = new RegistrationInfo(buyerName, "buyer", clientIP.getHostAddress(), clientPort, 0);

        boolean removed = FileUtils.removeSubscription("src/resources/subscriptions.txt", itemName, buyer);

        if (removed) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "UNSUBSCRIBED " + rqNum);
        } else {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "UNSUBSCRIBE-DENIED " + rqNum + " Reason: Subscription not found");
        }
    }

    public void broadcastAuctionAnnouncement(String auctionCSV, DatagramSocket ds) {
        String[] tokens = auctionCSV.split(",");
        if (tokens.length < 6) {
            System.err.println("Auction CSV is malformed, cannot broadcast.");
            return;
        }

        String message = String.format("AUCTION_ANNOUNCE %s %s %s %.2f %d",
                tokens[5].trim(), // RQ#
                tokens[0].trim(), // Item Name
                tokens[1].trim(), // Description
                Double.parseDouble(tokens[2].trim()), // Starting Price
                Long.parseLong(tokens[4].trim()) / 60000  // Duration in minutes
        );

        FileUtils.getAllSubscribers(SUBSCRIPTION_FILE).forEach(subscriber -> {
            try {
                InetAddress address = InetAddress.getByName(subscriber.getIpAddress());
                NetworkUtils.sendMessageToClient(ds, address, subscriber.getUdpPort(), message);
            } catch (UnknownHostException e) {
                System.err.println("Error broadcasting auction announcement: " + e.getMessage());
            }
        });
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
                String items = FileUtils.readFileAsString(ACTIVE_AUCTIONS_FILE);
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
                System.err.println("DEBUG: Invalid message format.");
                continue;
            }

            String action = tokens[0].trim().toLowerCase();
            int requestNumber = requestCounter.getAndIncrement();
            FileUtils.writeLastRequestNumber("src/resources/last_rq.txt", requestNumber);

            switch (action) {
                case "register":
                    try {
                        RegistrationInfo regInfo = MessageParser.parseRegistrationMessage(msg);
                        System.out.println("DEBUG: Parsed Registration Information: " + regInfo);
                        server.registerAccount(regInfo, requestNumber, ds, dpReceive);
                    } catch (IllegalArgumentException e) {
                        System.err.println("DEBUG: Error parsing registration message: " + e.getMessage());
                        String errorMessage = "Register-denied RQ#" + requestNumber + " Reason: " + e.getMessage();
                        NetworkUtils.sendMessageToClient(ds, dpReceive.getAddress(), dpReceive.getPort(), errorMessage);
                    }
                    break;

                case "deregister":
                    String uniqueName = tokens[1].trim();
                    server.deregisterAccount(uniqueName, ds, dpReceive.getAddress(), dpReceive.getPort(), requestNumber);
                    break;

                case "list_item":
                    server.listItem(msg, requestNumber, ds, dpReceive.getAddress(), dpReceive.getPort());
                    break;

                case "subscribe":
                    server.handleSubscribe(msg, requestNumber, ds, dpReceive.getAddress(), dpReceive.getPort());
                    break;

                case "de-subscribe":
                    server.handleDeSubscribe(msg, requestNumber, ds, dpReceive.getAddress(), dpReceive.getPort());
                    break;

                case "bid":
                    server.placeBid(msg, requestNumber, ds, dpReceive.getAddress(), dpReceive.getPort());
                    break;

                default:
                    System.err.println("DEBUG: Unknown action: " + action);
                    break;
            }
            receive = new byte[65535];
        }
        ds.close();
    }
}
