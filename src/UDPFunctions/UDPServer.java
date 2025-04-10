package UDPFunctions;

import TCPFunctions.TCPConnection;
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

        RegistrationInfo bidderInfo = FileUtils.getUserByName(FILE_PATH, bidderName);
        if (bidderInfo == null || !FileUtils.isAlreadySubscribed(SUBSCRIPTION_FILE, itemName, bidderInfo)) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort,
                    "BID-DENIED RQ#" + rqNum + " Reason: You must subscribe to this item before bidding");
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
            if (auctionTokens.length != 10) {
                System.err.println("DEBUG: Auction line for " + itemName + " is malformed.");
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + rqNum + " Reason: Auction data corrupted");
                return;
            }

            double currentBid = Double.parseDouble(auctionTokens[4].trim());
            if (bidAmount <= currentBid) {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "BID-DENIED RQ#" + rqNum + " Reason: Bid too low");
                return;
            }

            String updatedAuctionLine = String.format("%s,%s,%s,%.2f,%.2f,%s,%s,%s,%s,RQ#%s",
                    auctionTokens[0].trim(),
                    auctionTokens[1].trim(),
                    auctionTokens[2].trim(),
                    Double.parseDouble(auctionTokens[3].trim()),
                    bidAmount,
                    bidderName,
                    auctionTokens[6].trim(),
                    auctionTokens[7].trim(),
                    auctionTokens[8].trim(),
                    auctionTokens[9].trim().replace("RQ#", "")
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

    public void handleAcceptNegotiation(String message, DatagramSocket ds, InetAddress clientIP, int clientPort) {
        String[] tokens = message.split("\\s+");
        if (tokens.length != 4) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "ACCEPT-DENIED Reason: Invalid format");
            return;
        }

        String rqTag = tokens[1].trim(); // RQ#
        String itemName = tokens[2].trim();
        double newPrice;

        try {
            newPrice = Double.parseDouble(tokens[3].trim());
        } catch (NumberFormatException e) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "ACCEPT-DENIED " + rqTag + " Reason: Invalid price");
            return;
        }

        auctionLock.lock();
        try {
            String auctionLine = FileUtils.getAuctionLine(ACTIVE_AUCTIONS_FILE, itemName);
            if (auctionLine == null) {
                NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "ACCEPT-DENIED " + rqTag + " Reason: Item not found");
                return;
            }

            ItemRegistry item = ItemRegistry.fromCSV(auctionLine);
            item.adjustPrice(newPrice); // Update both startingPrice & currentPrice
            FileUtils.updateAuctionLine(ACTIVE_AUCTIONS_FILE, itemName, item.toCSV());

            // Notify all subscribed buyers of new price
            List<RegistrationInfo> subs = FileUtils.getSubscribersForItem(SUBSCRIPTION_FILE, itemName);
            String messageToSubs = String.format("PRICE_ADJUSTMENT RQ#%d %s %.2f %d",
                    item.getRequestNumber(), item.getItemName(), item.getCurrentPrice(), item.getTimeRemaining() / 60000);
            for (RegistrationInfo buyer : subs) {
                try {
                    InetAddress address = InetAddress.getByName(buyer.getIpAddress());
                    NetworkUtils.sendMessageToClient(ds, address, buyer.getUdpPort(), messageToSubs);
                } catch (Exception e) {
                    System.err.println("Error sending PRICE_ADJUSTMENT to " + buyer.getUniqueName());
                }
            }

            // Confirmation to seller
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "ACCEPTED " + rqTag);
        } finally {
            auctionLock.unlock();
        }
    }

    public void handleRefuseNegotiation(String message, DatagramSocket ds, InetAddress clientIP, int clientPort) {
        String[] tokens = message.split("\\s+");
        if (tokens.length < 3) {
            NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "REFUSE-DENIED Reason: Invalid format");
            return;
        }

        String rqTag = tokens[1].trim(); // RQ#
        NetworkUtils.sendMessageToClient(ds, clientIP, clientPort, "REFUSED " + rqTag);
    }

    public void startAuctionBroadcast(ItemRegistry item, DatagramSocket ds) {
        long auctionEndTime = System.currentTimeMillis() + item.getDuration();

        while (System.currentTimeMillis() < auctionEndTime) {
            try {
                Thread.sleep(30_000);  // Sleep 30s before sending update

                String auctionLine = FileUtils.getAuctionLine("src/resources/activeAuctions.txt", item.getItemName());
                if (auctionLine == null) return; // item removed

                // Create updated item object FIRST
                ItemRegistry updatedItem = ItemRegistry.fromCSV(auctionLine);

                // Check negotiation conditions
                if (!updatedItem.isNegotiationSent()
                        && updatedItem.getHighestBidder().equals("None")
                        && updatedItem.getTimeRemaining() <= (updatedItem.getDuration() / 2)) {

                    String negotiateMessage = String.format(
                            "NEGOTIATE_REQ RQ#%d %s %.2f %d",
                            updatedItem.getRequestNumber(),
                            updatedItem.getItemName(),
                            updatedItem.getCurrentPrice(),
                            updatedItem.getTimeRemaining() / 60000
                    );

                    RegistrationInfo seller = FileUtils.getUserByName(FILE_PATH, updatedItem.getSellerName());
                    if (seller != null) {
                        try {
                            InetAddress sellerAddress = InetAddress.getByName(seller.getIpAddress());
                            NetworkUtils.sendMessageToClient(ds, sellerAddress, seller.getUdpPort(), negotiateMessage);
                            System.out.println(" Sent NEGOTIATE_REQ to seller: " + seller.getUniqueName());
                        } catch (Exception e) {
                            System.err.println("Error sending negotiation request to seller: " + e.getMessage());
                        }
                    }

                    // Mark negotiation as sent and persist it
                    updatedItem.setNegotiationSent(true);
                    FileUtils.updateAuctionLine(ACTIVE_AUCTIONS_FILE, updatedItem.getItemName(), updatedItem.toCSV());
                }

                // Broadcast AUCTION_UPDATE
                List<RegistrationInfo> subscribedBuyers = FileUtils.getSubscribersForItem("src/resources/subscriptions.txt", item.getItemName());

                String message = String.format("AUCTION_UPDATE RQ#%d,Item: %s,Desc: %s,Price: %.2f,TimeLeft(min): %d",
                        updatedItem.getRequestNumber(),
                        updatedItem.getItemName(),
                        updatedItem.getDescription(),
                        updatedItem.getCurrentPrice(),
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

                RegistrationInfo seller = FileUtils.getUserByName(FILE_PATH, updatedItem.getSellerName());
                if (seller != null) {
                    try {
                        InetAddress sellerAddress = InetAddress.getByName(seller.getIpAddress());
                        NetworkUtils.sendMessageToClient(ds, sellerAddress, seller.getUdpPort(), message);
                        System.out.println("Sent AUCTION_UPDATE to seller: " + seller.getUniqueName());
                    } catch (Exception e) {
                        System.err.println("Error sending auction update to seller: " + e.getMessage());
                    }
                }
            } catch (InterruptedException e) {
                System.err.println("Auction broadcast interrupted: " + e.getMessage());
            }
        }

        String auctionLine = FileUtils.getAuctionLine("src/resources/activeAuctions.txt", item.getItemName());
        if (auctionLine == null) return; // item removed

        // Create updated item object FIRST
        ItemRegistry updatedItem = ItemRegistry.fromCSV(auctionLine);

        endAuction(updatedItem, ds);
    }

    public void endAuction(ItemRegistry item, DatagramSocket ds) {
        // Generate new RQ# for the purchase finalization
        int finalizeRqNum = requestCounter.getAndIncrement();
        String informReq = String.format("INFORM_Req,RQ#%d,%s,%.2f", finalizeRqNum, item.getItemName(), item.getCurrentPrice());

        List<RegistrationInfo> subscribedBuyers = FileUtils.getSubscribersForItem(SUBSCRIPTION_FILE, item.getItemName());

        String message = String.format("AUCTION_ENDED FIRST ANNOUNCEMENT %s %s %s %.2f %s %d",
                item.getRequestNumber(),
                item.getItemName(),
                item.getDescription(),
                item.getCurrentPrice(), // Final price (you might consider using the final bid)
                item.getHighestBidder(),
                0
        );


        // Notify all subscribed buyers about the auction result
        for (RegistrationInfo buyer : subscribedBuyers) {
            try {
                InetAddress address = InetAddress.getByName(buyer.getIpAddress());
                NetworkUtils.sendMessageToClient(ds, address, buyer.getUdpPort(), message);
            } catch (UnknownHostException e) {
                System.err.println("Error: Unable to resolve IP address for buyer " + buyer.getUniqueName() + ": " + buyer.getIpAddress());
            }
        }

        // Notify the seller over TCP
        RegistrationInfo seller = FileUtils.getUserByName(FILE_PATH, item.getSellerName());
        RegistrationInfo highestBidder = FileUtils.getUserByName(FILE_PATH, item.getHighestBidder());

        boolean saleValid = item.getCurrentPrice() >= item.getStartingPrice() && !item.getHighestBidder().equalsIgnoreCase("None");

        if (saleValid && seller != null && highestBidder != null) {
            try {
                TCPConnection informBuyer = new TCPConnection(highestBidder.getIpAddress(), highestBidder.getTcpPort());
                TCPConnection informSeller = new TCPConnection(seller.getIpAddress(), seller.getTcpPort());

                informBuyer.sendMessage(String.format("AUCTION_ENDED RQ#%d,%s,%s,%.2f,%s\nCongratulations! You won the auction!",
                        item.getRequestNumber(),
                        item.getItemName(),
                        item.getDescription(),
                        item.getCurrentPrice(),
                        item.getHighestBidder()));

                informSeller.sendMessage(String.format("AUCTION_ENDED RQ#%d,%s,%s,%.2f,%s\nItem sold to %s!",
                        item.getRequestNumber(),
                        item.getItemName(),
                        item.getDescription(),
                        item.getCurrentPrice(),
                        item.getHighestBidder(),
                        item.getHighestBidder()));

                informBuyer.sendMessage(informReq);
                informSeller.sendMessage(informReq);

                String buyerResponse = informBuyer.readLine();
                String sellerResponse = informSeller.readLine();

                if (buyerResponse == null || sellerResponse == null ||
                        !buyerResponse.startsWith("INFORM_Res") || !sellerResponse.startsWith("INFORM_Res")) {
                    informBuyer.sendMessage("CANCEL RQ#" + finalizeRqNum + " Reason: Invalid or no response");
                    informSeller.sendMessage("CANCEL RQ#" + finalizeRqNum + " Reason: Invalid or no response");
                    informBuyer.close();
                    informSeller.close();
                    return;
                }

                String[] buyerTokens = buyerResponse.split(",");
                String buyerName = buyerTokens[2];
                String buyerAddress = buyerTokens[5];

                try {
                    double finalPrice = item.getCurrentPrice();
                    double serverCut = finalPrice * 0.10;
                    double sellerCredit = finalPrice * 0.90;

                    System.out.println("[PAYMENT] Charging buyer's card: $" + String.format("%.2f", finalPrice));
                    System.out.println("[PAYMENT] Crediting seller with $" + String.format("%.2f", sellerCredit));
                    System.out.println("[PAYMENT] Server keeps $" + String.format("%.2f", serverCut));

                    if (finalPrice <= 0 || buyerName.equalsIgnoreCase("None")) {
                        throw new Exception("Simulated transaction failure");
                    }

                } catch (Exception e) {
                    System.err.println("Transaction failed: " + e.getMessage());
                    informBuyer.sendMessage("CANCEL,RQ#" + finalizeRqNum + ",Reason: Transaction failed");
                    informSeller.sendMessage("CANCEL,RQ#" + finalizeRqNum + ",Reason: Transaction failed");
                    informBuyer.close();
                    informSeller.close();
                    return;
                }

                String shippingInfo = String.format("Shipping_Info,RQ#%d,%s,%s", finalizeRqNum, buyerName, buyerAddress);
                informSeller.sendMessage(shippingInfo);

                informBuyer.close();

            } catch (IOException e) {
                System.err.println("Error finalizing transaction: " + e.getMessage());
            }
        } else {
            if (seller != null) {
                try {
                    TCPConnection tcpSellerConnection = new TCPConnection(seller.getIpAddress(), seller.getTcpPort());
                    String noSale = String.format("AUCTION_ENDED RQ#%d,%s,%s,%.2f,%s\nNo sale - item did not meet the reserve price.",
                            item.getRequestNumber(),
                            item.getItemName(),
                            item.getDescription(),
                            item.getCurrentPrice(),
                            item.getHighestBidder());
                    tcpSellerConnection.sendMessage(noSale);
                    tcpSellerConnection.close();
                } catch (IOException e) {
                    System.err.println("Error notifying seller of no sale: " + e.getMessage());
                }
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
        }else if (!regInfo.getRole().equalsIgnoreCase("buyer") && !regInfo.getRole().equalsIgnoreCase("seller")) {
            confirmationMessage = "Register-denied RQ#" + requestNumber + " Reason: Invalid role";
            success = false;
        } else {
            String entry = String.format("%s,%s,%s,%d,%d,RQ#%d",
                    regInfo.getUniqueName(),
                    regInfo.getRole(),
                    dpReceive.getAddress().getHostAddress(),
                    dpReceive.getPort(),
                    regInfo.getTcpPort(),
                    requestNumber);
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

        // Check if item exists in active auctions
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

    public void start(int port) throws IOException {
        DatagramSocket ds = new DatagramSocket(port);
        UDPServer server = new UDPServer();
        System.out.println("Server listening on port: " + port);

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
                String msg = new String(data).trim();
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


// Handle space-separated negotiation messages FIRST
                String spaceAction = msg.split("\\s+")[0].trim().toLowerCase();
                if (spaceAction.equals("accept")) {
                    server.handleAcceptNegotiation(msg, ds, clientAddress, clientPort);
                    return;
                } else if (spaceAction.equals("refuse")) {
                    server.handleRefuseNegotiation(msg, ds, clientAddress, clientPort);
                    return;
                }

// ONLY THEN: comma-split regular protocol messages
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