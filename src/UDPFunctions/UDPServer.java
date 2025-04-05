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
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPServer {

    private static final int MAX_USERS = 10;
    private static final String FILE_PATH = "src/resources/accounts.txt";
    private static final String ITEM_FILE = "src/resources/items.txt";
    private static final String SUBSCRIPTION_FILE = "src/resources/subscriptions.txt";
    private static final String ACTIVE_AUCTIONS_FILE = "src/resources/activeAuctions.txt";


    private static AtomicInteger requestCounter = new AtomicInteger(FileUtils.readLastRequestNumber("src/resources/last_rq.txt") + 1);
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
                System.out.println("DEBUG: Received bid for item '" + itemName + "', but auction not found.");
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + requestNumber + " Reason: Item not found");
                return;
            }
            // Expected auction line format:
            // itemName,description,startingPrice,currentBid,duration,RQ#...
            String[] auctionTokens = auctionLine.split(",");
            if (auctionTokens.length < 6) {
                System.err.println("DEBUG: Auction line for " + itemName + " is malformed.");
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + requestNumber + " Reason: Auction data corrupted");
                return;
            }
            double currentBid = Double.parseDouble(auctionTokens[3].trim());
            // (Optional: add auction expiration check here if you decide to store timing info.)
            if (bidAmount <= currentBid) {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + requestNumber + " Reason: Bid too low");
                return;
            }
            // Create updated auction line (update current bid)
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
                Thread.sleep(30_000);  // Sleep for 30 seconds before sending another update

                List<RegistrationInfo> subscribedBuyers = FileUtils.getSubscribersForItem(SUBSCRIPTION_FILE, item.getItemName());

                String message = String.format("AUCTION_UPDATE %s %s %s %.2f %d",
                        item.getRequestNumber(),                   // RQ#
                        item.getItemName(),
                        item.getDescription(),
                        item.getCurrentPrice(),                   // This should be updated with current highest bid
                        item.getTimeRemaining() / 60000  // Time left in minutes
                );

                // Send updates to all subscribed buyers
                for (RegistrationInfo buyer : subscribedBuyers) {
                    try {
                        InetAddress address = InetAddress.getByName(buyer.getIpAddress());
                        NetworkUtils.sendMessageToClient(ds, address, buyer.getUdpPort(), message);
                    } catch (UnknownHostException e) {
                        System.err.println("Error: Unable to resolve IP address for buyer " + buyer.getUniqueName() + ": " + buyer.getIpAddress());
                    }
                }
            } catch (InterruptedException e) {
                System.err.println("Auction broadcast interrupted: " + e.getMessage());
            }
        }

        // Once auction ends, notify buyers and remove item
        endAuction(item, ds);
    }

    public void endAuction(ItemRegistry item, DatagramSocket ds) {
        List<RegistrationInfo> subscribedBuyers = FileUtils.getSubscribersForItem(SUBSCRIPTION_FILE, item.getItemName());

        String message = String.format("AUCTION_ENDED %s %s %s %.2f %d",
                item.getRequestNumber(),
                item.getItemName(),
                item.getDescription(),
                item.getStartingPrice(), // Final price (you might consider using the final bid)
                0
        );

        for (RegistrationInfo buyer : subscribedBuyers) {
            try {
                InetAddress address = InetAddress.getByName(buyer.getIpAddress());
                NetworkUtils.sendMessageToClient(ds, address, buyer.getUdpPort(), message);
            } catch (UnknownHostException e) {
                System.err.println("Error: Unable to resolve IP address for buyer " + buyer.getUniqueName() + ": " + buyer.getIpAddress());
            }
        }

        // Remove auction from file under lock
        auctionLock.lock();
        try {
            if (FileUtils.removeItemFromFile(ACTIVE_AUCTIONS_FILE, item.getItemName())) {
                System.out.println("DEBUG: Auction for item '" + item.getItemName() + "' removed from file.");
            } else {
                System.err.println("DEBUG: Failed to remove auction for item '" + item.getItemName() + "'.");
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
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort,
                    "LIST-DENIED RQ#" + requestNumber + " Reason: Item limit reached");
            return;
        }
        if (FileUtils.isDuplicateItem(ACTIVE_AUCTIONS_FILE, itemName)) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "LIST-DENIED RQ#" + requestNumber + " Reason: Item already listed");
            return;
        }

        // Create new auction item. Its toCSV() should return a CSV line:
        // itemName,description,startingPrice,currentBid,duration,RQ#requestNumber
        ItemRegistry newItem = new ItemRegistry(itemName, description, startingPrice, duration, requestNumber);

        // Write the new auction to the file under lock.
        auctionLock.lock();
        try {
            if (FileUtils.appendLineToFile(ACTIVE_AUCTIONS_FILE, newItem.toCSV())) {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "ITEM_LISTED RQ#" + requestNumber);
                broadcastAuctionAnnouncement(newItem.toCSV(), ds);
                // Start auction broadcast in a new thread so it doesn't block the main server loop.
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
        // Parse the auction CSV. Expected order:
        // itemName,description,startingPrice,currentBid,duration,RQ#
        ItemRegistry item;
        try {
            item = ItemRegistry.fromCSV(auctionCSV);
        } catch (Exception e) {
            System.err.println("Failed to parse auction CSV: " + e.getMessage());
            return;
        }

        String message = String.format("AUCTION_ANNOUNCE %s %s %s %.2f %d",
                item.getRequestNumber(),
                item.getItemName(),
                item.getDescription(),
                item.getCurrentPrice(),
                item.getTimeRemaining() / 60000);
        List<RegistrationInfo> subscribedBuyers = FileUtils.getSubscribersForItem(SUBSCRIPTION_FILE, item.getItemName());
        for (RegistrationInfo buyer : subscribedBuyers) {
            try {
                InetAddress address = InetAddress.getByName(buyer.getIpAddress());
                NetworkUtils.sendMessageToClient(ds, address, buyer.getUdpPort(), message);
            } catch (IOException e) {
                System.err.println("Error broadcasting to " + buyer.getIpAddress() + ": " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        DatagramSocket ds = new DatagramSocket(420);
        UDPServer server = new UDPServer();
        System.out.println("Server listening on port 420...");

        ExecutorService pool = Executors.newFixedThreadPool(10);

        while (true) {
            byte[] receive = new byte[65535];
            DatagramPacket dpReceive = new DatagramPacket(receive, receive.length);
            ds.receive(dpReceive);

            // Copy packet data for thread safety
            byte[] data = new byte[dpReceive.getLength()];
            System.arraycopy(dpReceive.getData(), 0, data, 0, dpReceive.getLength());

            InetAddress clientAddress = dpReceive.getAddress();
            int clientPort = dpReceive.getPort();

            pool.execute(() -> {
                String msg = new String(data);
                System.out.println("Received message: " + msg);

                if (MessageParser.isGetAllItemsRequest(msg)) {
                    String items = FileUtils.readFileAsString(ACTIVE_AUCTIONS_FILE);
                    NetworkUtils.sendMessageToClient(ds, clientAddress, clientPort, items);
                    return;
                }

                if (msg.equalsIgnoreCase("bye")) {
                    System.out.println("Client sent bye...EXITING");
                    return;
                }

                String[] tokens = msg.split(",");
                if (tokens.length < 2) {
                    System.err.println("DEBUG: Invalid message format.");
                    return;
                }

                String action = tokens[0].trim().toLowerCase();
                int requestNumber = requestCounter.getAndIncrement();
                FileUtils.writeLastRequestNumber("src/resources/last_rq.txt", requestNumber);

                switch (action) {
                    case "register":
                        try {
                            RegistrationInfo regInfo = MessageParser.parseRegistrationMessage(msg);
                            System.out.println("DEBUG: Parsed Registration Information: " + regInfo);
                            server.registerAccount(regInfo, requestNumber, ds, new DatagramPacket(data, data.length, clientAddress, clientPort));
                        } catch (IllegalArgumentException e) {
                            String errorMessage = "Register-denied RQ#" + requestNumber + " Reason: " + e.getMessage();
                            NetworkUtils.sendMessageToClient(ds, clientAddress, clientPort, errorMessage);
                        }
                        break;

                    case "deregister":
                        String uniqueName = tokens[1].trim();
                        server.deregisterAccount(uniqueName, ds, clientAddress, clientPort, requestNumber);
                        break;

                    case "list_item":
                        server.listItem(msg, requestNumber, ds, clientAddress, clientPort);
                        break;

                    case "subscribe":
                        server.handleSubscribe(msg, requestNumber, ds, clientAddress, clientPort);
                        break;

                    case "de-subscribe":
                        server.handleDeSubscribe(msg, requestNumber, ds, clientAddress, clientPort);
                        break;

                    case "bid":
                        server.placeBid(msg, requestNumber, ds, clientAddress, clientPort);
                        break;

                    default:
                        System.err.println("DEBUG: Unknown action: " + action);
                        break;
                }
            });
        }
    }
}
