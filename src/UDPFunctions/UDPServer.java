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

    public void placeBid(String message, DatagramSocket ds, InetAddress clientIP, int clientPort) {
        String[] tokens = message.split(",");
        if (tokens.length != 4) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#UNKNOWN Reason: Invalid format");
            return;
        }

        String rqNum = tokens[0].trim();  // Client's RQ#
        String itemName = tokens[1].trim();
        String bidderName = tokens[2].trim();
        double bidAmount;

        try {
            bidAmount = Double.parseDouble(tokens[3].trim());
        } catch (NumberFormatException e) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + rqNum + " Reason: Invalid bid amount");
            return;
        }

        auctionLock.lock();
        try {
            String auctionLine = FileUtils.getAuctionLine(ACTIVE_AUCTIONS_FILE, itemName);
            if (auctionLine == null) {
                System.out.println("DEBUG: Received bid for item '" + itemName + "', but auction not found.");
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + rqNum + " Reason: Item not found");
                return;
            }

            String[] auctionTokens = auctionLine.split(",");
            if (auctionTokens.length != 9) {
                System.err.println("DEBUG: Auction line for " + itemName + " is malformed.");
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + rqNum + " Reason: Auction data corrupted");
                return;
            }

            double currentBid = Double.parseDouble(auctionTokens[3].trim());
            if (bidAmount <= currentBid) {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + rqNum + " Reason: Bid too low");
                return;
            }

            String updatedAuctionLine = String.format("%s,%s,%s,%.2f,%.2f,%s,%s,%s,RQ#%s",
                    auctionTokens[0].trim(),
                    auctionTokens[1].trim(),
                    auctionTokens[2].trim(),
                    Double.parseDouble(auctionTokens[3].trim()),
                    bidAmount,
                    bidderName,
                    auctionTokens[6].trim(),
                    auctionTokens[7].trim(),
                    auctionTokens[8].trim().replace("RQ#", "")
            );

            if (FileUtils.updateAuctionLine(ACTIVE_AUCTIONS_FILE, itemName, updatedAuctionLine)) {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-ACCEPTED RQ#" + rqNum);
                ItemRegistry updatedItem = ItemRegistry.fromCSV(updatedAuctionLine);
                broadcastBidUpdate(updatedItem, ds);

            } else {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + rqNum + " Reason: File update error");
            }
        } finally {
            auctionLock.unlock();
        }
    }

    public void broadcastBidUpdate(ItemRegistry item, DatagramSocket ds) {
        String message = String.format("BID_UPDATE,RQ#%d,%s,%.2f,%s,%d",
                item.getRequestNumber(),
                item.getItemName(),
                item.getCurrentPrice(),
                item.getHighestBidder(),
                item.getTimeRemaining() / 60000);

        List<RegistrationInfo> subscribers = FileUtils.getSubscribersForItem(SUBSCRIPTION_FILE, item.getItemName());

        for (RegistrationInfo buyer : subscribers) {
            try {
                InetAddress address = InetAddress.getByName(buyer.getIpAddress());
                NetworkUtils.sendMessageToClient(ds, address, buyer.getUdpPort(), message);
            } catch (Exception e) {
                System.err.println("Error sending BID_UPDATE to " + buyer.getUniqueName());
            }
        }

        // Optional: Notify seller too (you’d need to store who the seller is during list_item)
        RegistrationInfo seller = FileUtils.getUserByName(FILE_PATH, item.getSellerName());
        if (seller != null) {
            try {
                InetAddress sellerAddress = InetAddress.getByName(seller.getIpAddress());
                NetworkUtils.sendMessageToClient(ds, sellerAddress, seller.getUdpPort(), message);
            } catch (Exception e) {
                System.err.println("Error sending BID_UPDATE to seller " + seller.getUniqueName());
            }
        }
    }

    public void startAuctionBroadcast(ItemRegistry item, DatagramSocket ds) {
        long auctionEndTime = System.currentTimeMillis() + item.getDuration();

        while (System.currentTimeMillis() < auctionEndTime) {
            try {
                Thread.sleep(30_000);  // Sleep 30s before sending update

                String auctionLine = FileUtils.getAuctionLine("src/resources/activeAuctions.txt", item.getItemName());
                if (auctionLine == null) return; // item removed

                // ✅ Create updated item object
                ItemRegistry updatedItem = ItemRegistry.fromCSV(auctionLine);

                List<RegistrationInfo> subscribedBuyers = FileUtils.getSubscribersForItem("src/resources/subscriptions.txt", item.getItemName());

                String message = String.format("AUCTION_UPDATE %d,%s,%s,%.2f,%d",
                        updatedItem.getRequestNumber(),
                        updatedItem.getItemName(),
                        updatedItem.getDescription(),
                        updatedItem.getCurrentPrice(),  // ✅ now reflects updated price
                        updatedItem.getTimeRemaining() / 60000
                );

                for (RegistrationInfo buyer : subscribedBuyers) {
                    try {
                        InetAddress address = InetAddress.getByName(buyer.getIpAddress());
                        NetworkUtils.sendMessageToClient(ds, address, buyer.getUdpPort(), message);
                    } catch (UnknownHostException e) {
                        System.err.println("Error: Unable to resolve IP for " + buyer.getUniqueName());
                    }
                }
            } catch (InterruptedException e) {
                System.err.println("Auction broadcast interrupted: " + e.getMessage());
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
        if (tokens.length != 6) {
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

        String sellerName = tokens[5].trim();

        // Create new auction item. Its toCSV() should return a CSV line:
        // itemName,description,startingPrice,currentBid,duration,RQ#requestNumber
        ItemRegistry newItem = new ItemRegistry(itemName, description, startingPrice, duration, requestNumber, sellerName);

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

    public void handleSubscribe(String message, DatagramSocket ds, InetAddress clientIP, int clientPort) {
        String[] tokens = message.split(",", 4);
        if (tokens.length != 4) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "SUBSCRIPTION-DENIED RQ#UNKNOWN Reason: Invalid format");
            return;
        }

        String rqNum = tokens[1].trim();
        String itemName = tokens[2].trim();
        String buyerName = tokens[3].trim();

        // 🔐 Check if item exists in active auctions
        if (FileUtils.getAuctionLine("src/resources/activeAuctions.txt", itemName) == null) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "SUBSCRIPTION-DENIED RQ#" + rqNum + " Reason: Item not found");
            return;
        }

        RegistrationInfo buyer = new RegistrationInfo(buyerName, "buyer", clientIP.getHostAddress(), clientPort, 0);

        if (FileUtils.isAlreadySubscribed("src/resources/subscriptions.txt", itemName, buyer)) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "SUBSCRIPTION-DENIED RQ#" + rqNum + " Reason: Already subscribed");
            return;
        }

        boolean written = FileUtils.addSubscription("src/resources/subscriptions.txt", itemName, buyer);
        if (written) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "SUBSCRIBED RQ#" + rqNum);
        } else {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "SUBSCRIPTION-DENIED RQ#" + rqNum + " Reason: Internal server error");
        }
    }



    public void handleDeSubscribe(String message, DatagramSocket ds, InetAddress clientIP, int clientPort) {
        String[] tokens = message.split(",", 4);
        if (tokens.length != 4) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "UNSUBSCRIBE-DENIED RQ#UNKNOWN Reason: Invalid format");
            return;
        }

        String rqNum = tokens[1].trim();
        String itemName = tokens[2].trim();
        String buyerName = tokens[3].trim();

        RegistrationInfo buyer = new RegistrationInfo(buyerName, "buyer", clientIP.getHostAddress(), clientPort, 0);

        boolean removed = FileUtils.removeSubscription("src/resources/subscriptions.txt", itemName, buyer);

        if (removed) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "UNSUBSCRIBED RQ#" + rqNum);
        } else {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "UNSUBSCRIBE-DENIED RQ#" + rqNum + " Reason: Subscription not found");
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
                        server.handleSubscribe(msg, ds, clientAddress, clientPort);
                        break;

                    case "de-subscribe":
                        server.handleDeSubscribe(msg, ds, clientAddress, clientPort);
                        break;

                    case "bid":
                        server.placeBid(msg, ds, clientAddress, clientPort);
                        break;

                    default:
                        System.err.println("DEBUG: Unknown action: " + action);
                        break;
                }
            });
        }
    }
}
