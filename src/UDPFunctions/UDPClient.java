package UDPFunctions;

import TCPFunctions.TCPResponder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class UDPClient {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<String> negotiationQueue = new LinkedBlockingQueue<>(); // Add this to the class fields
    private Scanner sc = new Scanner(System.in);
    private String uniqueName = "", role = "";

    public UDPClient(InetAddress serverAddress, int serverPort) throws SocketException {
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(60000);
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;

        new Thread(this::listenForResponses).start();
    }

    private void listenForResponses() {
        byte[] buffer = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (true) {
            try {
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength()).trim();

                // Handle broadcasts
                if (message.startsWith("AUCTION_UPDATE") ||
                        message.startsWith("BID_UPDATE") ||
                        message.startsWith("PRICE_ADJUSTMENT")) {

                    System.out.println("Broadcast: " + message);

                } else if (message.startsWith("NEGOTIATE_REQ")) {
                    System.out.println("Broadcast: " + message);
                    negotiationQueue.offer(message); // Store it for option 3
                } else {
                    responseQueue.offer(message); // All other messages handled by main flow
                }
            } catch (IOException e) {
                // Timeout or other expected errors
            }
        }
    }

    private void sendAndReceive(String message) throws IOException {
        byte[] buf = message.getBytes();
        socket.send(new DatagramPacket(buf, buf.length, serverAddress, serverPort));

        boolean expectRQ = message.matches(".*\\b\\d{3,5}\\b.*");
        String rqTag = expectRQ ? extractRequestNumber(message) : "";
        long startTime = System.currentTimeMillis();

        while (true) {
            try {
                String response = responseQueue.poll();
                if (response == null) {
                    if (System.currentTimeMillis() - startTime > 5000) {
                        System.err.println("Timed out waiting for response.");
                        return;
                    }
                    Thread.sleep(100);
                    continue;
                }
                if (!expectRQ || response.contains(rqTag)) {
                    System.out.println("Server Response: " + response);
                    return;
                } else {
                    if (response.startsWith("AUCTION_UPDATE") || response.startsWith("BID_UPDATE") || response.startsWith("PRICE_ADJUSTMENT")) {
                        System.out.println("Broadcast: " + response);
                    } else {
                        System.out.println("Skipping unrelated message: " + response);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while waiting for response.");
                return;
            }
        }
    }

    private String extractRequestNumber(String message) {
        String[] tokens = message.split(",");
        for (String token : tokens) {
            if (token.matches("\\d{3,5}")) {
                return "RQ#" + token;
            }
        }
        return "";
    }

    private void startTCPListener(int tcpPort) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
                System.out.println(" TCP listener started on port " + tcpPort);

                while (true) {
                    try (Socket clientSocket = serverSocket.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

                        String line;
                        while ((line = in.readLine()) != null) {
                            System.out.println("\n TCP Message Received: " + line);
                            System.out.print(">> "); // re-prompt
                        }

                    } catch (IOException e) {
                        System.err.println(" Error in client TCP connection: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println(" Failed to start TCP listener: " + e.getMessage());
            }
        }).start();
    }

    public void run() throws IOException {
        String clientIP = InetAddress.getLocalHost().getHostAddress();
        boolean registered = false;

        while (!registered) {
            System.out.println("\n=== Welcome to the Auction System ===");
            System.out.println("1. Register new account");
            System.out.println("2. Deregister existing account");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");

            switch (sc.nextLine().trim()) {
                case "1":
                    while (!registered) {
                        System.out.print("Enter your unique name: ");
                        uniqueName = sc.nextLine().trim();

                        System.out.print("Enter your role (buyer/seller): ");
                        role = sc.nextLine().trim().toLowerCase();

                        System.out.print("Enter your UDP port: ");
                        String udpPort = sc.nextLine().trim();

                        System.out.print("Enter your TCP port: ");
                        String tcpPort = sc.nextLine().trim();

                        String regMessage = String.format("register,%s,%s,%s,%s,%s", uniqueName, role, clientIP, udpPort, tcpPort);

                        // Capture the response string and only proceed if registration is accepted
                        byte[] buf = regMessage.getBytes();
                        socket.send(new DatagramPacket(buf, buf.length, serverAddress, serverPort));

                        long startTime = System.currentTimeMillis();
                        String response = null;

                        while (System.currentTimeMillis() - startTime < 5000 && response == null) {
                            try {
                                String msg = responseQueue.poll();
                                if (msg != null && (msg.toLowerCase().startsWith("registered") || msg.toLowerCase().startsWith("register-denied"))) {
                                    response = msg;
                                    System.out.println("Server Response: " + response);
                                } else if (msg != null) {
                                    System.out.println("Skipping unrelated message: " + msg);
                                } else {
                                    Thread.sleep(100);
                                }
                            } catch (InterruptedException e) {
                                System.err.println("Interrupted while waiting for registration response.");
                                return;
                            }
                        }


                        if (response != null && response.toLowerCase().startsWith("registered")) {
                            registered = true;

                            // Use TCPResponder instead of startTCPListener
                            // Pass the tcpPort entered during registration
                            TCPResponder tcpResponder = new TCPResponder(Integer.parseInt(tcpPort), this.role, this.uniqueName);
                            tcpResponder.start();  // Start TCPResponder to handle further TCP communication

                        } else {
                            System.out.println("Message is: " + response);
                            System.out.println(" Registration failed (duplicate name or capacity). Try again.");
                        }
                    }
                    break;

                case "2":
                    System.out.print("Enter the unique name to deregister: ");
                    String deregMsg = "deregister," + sc.nextLine().trim();
                    sendAndReceive(deregMsg);
                    break;

                case "3":
                    System.out.println("Exiting client...");
                    socket.close();
                    sc.close();
                    return;

                default:
                    System.out.println("Invalid option. Try again.");
            }
        }

        if (role.equals("seller")) {
            handleSellerActions();
        } else if (role.equals("buyer")) {
            handleBuyerActions();
        }
    }

    private void handleSellerActions() throws IOException {
        System.out.println("\n=== Seller Menu ===");
        while (true) {
            System.out.println("\n--- Seller Actions ---");
            System.out.println("1. List an item");
            System.out.println("2. Listen for auction updates");
            System.out.println("3. Handle negotiation requests"); // <== this line
            System.out.println("4. Exit");

            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1":
                    System.out.print("Enter item name: ");
                    String itemName = sc.nextLine().trim();
                    System.out.print("Enter item description: ");
                    String description = sc.nextLine().trim();
                    System.out.print("Enter starting price: ");
                    String startingPrice = sc.nextLine().trim();
                    System.out.print("Enter duration (minutes): ");
                    String duration = sc.nextLine().trim();
                    String msg = String.format("list_item,%s,%s,%s,%s,%s", itemName, description, startingPrice, duration, uniqueName);
                    sendAndReceive(msg);
                    break;

                case "2":
                    listenForAnnouncements(); // Same method used by buyer
                    break;

                case "3":
                    handlePendingNegotiations();
                    break;

                case "4":
                    System.out.println("Exiting seller menu...");
                    return;

                default:
                    System.out.println("Invalid option. Please select 1, 2, or 3.");
            }
        }
    }

    private void handlePendingNegotiations() {
        if (negotiationQueue.isEmpty()) {
            System.out.println(" No pending negotiation requests.");
            return;
        }

        while (!negotiationQueue.isEmpty()) {
            String message = negotiationQueue.poll();
            if (message == null) break;

            // Parse: NEGOTIATE_REQ RQ#83 itemName price timeLeft
            String[] tokens = message.split("\\s+");
            if (tokens.length < 5) {
                System.out.println(" Invalid negotiation request: " + message);
                continue;
            }

            String rqNum = tokens[1];
            String itemName = tokens[2];
            String price = tokens[3];
            String timeLeft = tokens[4];

            System.out.println("\n NEGOTIATION REQUEST RECEIVED");
            System.out.printf("Item: %s | Current Price: %s | Time Left: %s minutes%n", itemName, price, timeLeft);
            System.out.println("1. Accept and offer a new price");
            System.out.println("2. Refuse");
            System.out.print("Choose an option (1/2): ");

            String decision = sc.nextLine().trim();
            if (decision.equals("1")) {
                System.out.print("Enter your new price: ");
                String newPrice = sc.nextLine().trim();
                String response = String.format("ACCEPT %s %s %s", rqNum, itemName, newPrice);

                try {
                    socket.send(new DatagramPacket(response.getBytes(), response.length(), serverAddress, serverPort));
                } catch (IOException e) {
                    System.err.println(" Failed to send ACCEPT message: " + e.getMessage());
                }

            } else {
                String response = String.format("REFUSE %s %s REJECT", rqNum, itemName);

                try {
                    socket.send(new DatagramPacket(response.getBytes(), response.length(), serverAddress, serverPort));
                } catch (IOException e) {
                    System.err.println(" Failed to send REFUSE message: " + e.getMessage());
                }
            }
        }
    }

    private void handleBuyerActions() throws IOException {
        System.out.println("\n=== Buyer Options ===");
        System.out.print("Do you want to see item listings? (yes/no): ");
        if (sc.nextLine().trim().equalsIgnoreCase("yes")) {
            sendAndReceive("get_all_items");
        }

        while (true) {
            System.out.println("\n--- Buyer Actions ---");
            System.out.println("1. Subscribe to item");
            System.out.println("2. Unsubscribe from item");
            System.out.println("3. Listen for auction announcements");
            System.out.println("4. Place a bid");
            System.out.println("5. Exit");
            System.out.print("Select option: ");

            switch (sc.nextLine().trim()) {
                case "1":
                    System.out.print("Enter item name to subscribe: ");
                    String subItem = sc.nextLine().trim();
                    String subscribeMsg = String.format("subscribe,%d,%s,%s", (int)(Math.random() * 10000), subItem, uniqueName);
                    sendAndReceive(subscribeMsg);
                    break;

                case "2":
                    System.out.print("Enter item name to unsubscribe: ");
                    String unsubItem = sc.nextLine().trim();
                    String unsubMsg = String.format("de-subscribe,%d,%s,%s", (int)(Math.random() * 10000), unsubItem, uniqueName);
                    sendAndReceive(unsubMsg);
                    break;

                case "3":
                    listenForAnnouncements();
                    break;

                case "4":
                    System.out.print("Enter item name to bid on: ");
                    String bidItem = sc.nextLine().trim();
                    System.out.print("Enter your bid amount: ");
                    String bidAmount = sc.nextLine().trim();
                    String bidMsg = String.format("bid,%s,%s,%s", bidItem, uniqueName, bidAmount);
                    sendAndReceive(bidMsg);
                    break;

                case "5":
                    System.out.println("Exiting...");
                    return;

                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    private void listenForAnnouncements() {
        System.out.println("Listening for auction announcements (press Enter to stop)...");
        Thread listener = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String message = responseQueue.take();
                    System.out.println("Broadcast: " + message);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        listener.setDaemon(true);
        listener.start();
        sc.nextLine(); // Wait for user to press Enter
        listener.interrupt();
    }

    public static void main(String[] args) throws IOException {
        InetAddress serverIP = InetAddress.getLocalHost();

        int udpPort = 420; // This should match the server's UDP port

        // Start the UDP client
        UDPClient client = new UDPClient(serverIP, udpPort);
        client.run();
    }
}