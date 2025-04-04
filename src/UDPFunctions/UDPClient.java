package UDPFunctions;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

public class UDPClient {
    public static void main(String[] args) throws IOException {
        Scanner sc = new Scanner(System.in);
        DatagramSocket ds = new DatagramSocket();
        InetAddress serverIP = InetAddress.getLocalHost();

        boolean registered = false;
        String role = "", uniqueName = "", udpPort = "", tcpPort = "";
        String clientIP = InetAddress.getLocalHost().getHostAddress();

        while (!registered) {
            System.out.println("\n=== Welcome to the Auction System ===");
            System.out.println("1. Register new account");
            System.out.println("2. Deregister existing account");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");

            switch (sc.nextLine().trim()) {
                case "1":
                    System.out.print("Enter your unique name: ");
                    uniqueName = sc.nextLine().trim();

                    System.out.print("Enter your role (buyer/seller): ");
                    role = sc.nextLine().trim().toLowerCase();

                    System.out.print("Enter your UDP port: ");
                    udpPort = sc.nextLine().trim();

                    System.out.print("Enter your TCP port: ");
                    tcpPort = sc.nextLine().trim();

                    String regMessage = String.format("register,%s,%s,%s,%s,%s", uniqueName, role, clientIP, udpPort, tcpPort);
                    sendAndReceive(ds, serverIP, regMessage);

                    registered = true;
                    break;

                case "2":
                    System.out.print("Enter the unique name to deregister: ");
                    String deregMsg = "deregister," + sc.nextLine().trim();
                    sendAndReceive(ds, serverIP, deregMsg);
                    break;

                case "3":
                    System.out.println("Exiting client...");
                    ds.close();
                    sc.close();
                    return;

                default:
                    System.out.println("Invalid option. Try again.");
            }
        }

        // === POST-REGISTRATION LOGIC ===
        if (role.equals("seller")) {
            handleSellerActions(sc, ds, serverIP);
        } else if (role.equals("buyer")) {
            handleBuyerActions(sc, ds, serverIP, uniqueName);
        }

        System.out.println("Client exiting...");
        ds.close();
        sc.close();
    }

    private static void handleSellerActions(Scanner sc, DatagramSocket ds, InetAddress serverIP) throws IOException {
        System.out.println("\n=== Item Listing (Seller) ===");
        while (true) {
            System.out.print("Enter item name (or type 'exit' to stop listing items): ");
            String itemName = sc.nextLine().trim();
            if (itemName.equalsIgnoreCase("exit")) break;

            System.out.print("Enter item description: ");
            String description = sc.nextLine().trim();

            System.out.print("Enter starting price: ");
            String startingPrice = sc.nextLine().trim();

            System.out.print("Enter duration (in minutes): ");
            String duration = sc.nextLine().trim();

            String listItemMessage = String.format("list_item,%s,%s,%s,%s", itemName, description, startingPrice, duration);
            sendAndReceive(ds, serverIP, listItemMessage);
        }
    }

    private static void handleBuyerActions(Scanner sc, DatagramSocket ds, InetAddress serverIP, String uniqueName) throws IOException {
        System.out.println("\n=== Buyer Options ===");
        System.out.print("Do you want to see the item listings? (yes/no): ");
        if (sc.nextLine().trim().equalsIgnoreCase("yes")) {
            sendAndReceive(ds, serverIP, "get_all_items");
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
                    System.out.print("Enter Item Name to subscribe to: ");
                    String subItem = sc.nextLine().trim();
                    String subscribeMsg = String.format("subscribe,%d,%s,%s", (int) (Math.random() * 10000), subItem, uniqueName);
                    sendAndReceive(ds, serverIP, subscribeMsg);
                    break;

                case "2":
                    System.out.print("Enter Item Name to unsubscribe from: ");
                    String unsubItem = sc.nextLine().trim();
                    String unsubMsg = String.format("de-subscribe,%d,%s,%s", (int) (Math.random() * 10000), unsubItem, uniqueName);
                    sendAndReceive(ds, serverIP, unsubMsg);
                    break;

                case "3":
                    listenForAnnouncements(ds, sc);
                    break;

                case "4":
                    System.out.print("Enter the item name to bid on: ");
                    String bidItem = sc.nextLine().trim();
                    System.out.print("Enter your bid amount: ");
                    String bidAmount = sc.nextLine().trim();

                    String bidMessage = String.format("bid,%s,%s,%s", bidItem, uniqueName, bidAmount);
                    System.out.println("Sending bid request: " + bidMessage);
                    sendAndReceive(ds, serverIP, bidMessage);
                    break;

                case "5":
                    System.out.println("Exiting...");
                    return;

                default:
                    System.out.println("Invalid option. Please select a number between 1 and 5.");
            }
        }
    }

    private static void sendAndReceive(DatagramSocket ds, InetAddress serverIP, String message) throws IOException {
        byte[] buf = message.getBytes();
        ds.send(new DatagramPacket(buf, buf.length, serverIP, 420));

        byte[] responseBuffer = new byte[65535];
        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);

        // Set a timeout to prevent freezing indefinitely
        ds.setSoTimeout(5000); // 5 seconds timeout

        try {
            ds.receive(responsePacket);
            String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
            System.out.println("Server Response: " + response);
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("Error: No response from server. Check server status.");
        }
    }

    private static void listenForAnnouncements(DatagramSocket ds, Scanner sc) {
        System.out.println("Listening for auction announcements (press Enter to stop)...");
        Thread listener = new Thread(() -> {
            while (true) {
                try {
                    byte[] buffer = new byte[65535];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    ds.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    if (msg.equalsIgnoreCase("bye")) break;
                    System.out.println("Broadcast: " + msg);
                } catch (IOException e) {
                    System.err.println("Listener error: " + e.getMessage());
                    break;
                }
            }
        });
        listener.setDaemon(true);
        listener.start();
        sc.nextLine(); // Wait for Enter to stop listening
    }
}
