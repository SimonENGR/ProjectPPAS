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
        String role = "";
        String uniqueName = "";
        String udpPort = "";
        String tcpPort = "";
        String clientIP = InetAddress.getLocalHost().getHostAddress();

        while (!registered) {
            System.out.println("\n=== Welcome to the Auction System ===");
            System.out.println("1. Register new account");
            System.out.println("2. Deregister existing account");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");
            String option = sc.nextLine().trim();

            if (option.equals("1")) {
                System.out.print("Enter your unique name: ");
                uniqueName = sc.nextLine().trim();

                System.out.print("Enter your role (buyer/seller): ");
                role = sc.nextLine().trim().toLowerCase();

                System.out.print("Enter your UDP port: ");
                udpPort = sc.nextLine().trim();

                System.out.print("Enter your TCP port: ");
                tcpPort = sc.nextLine().trim();

                String regMessage = "register," + uniqueName + "," + role + "," + clientIP + "," + udpPort + "," + tcpPort;
                byte[] buf = regMessage.getBytes();
                DatagramPacket dpSend = new DatagramPacket(buf, buf.length, serverIP, 420);
                ds.send(dpSend);
                System.out.println("Registration message sent: " + regMessage);

                byte[] responseBuffer = new byte[65535];
                DatagramPacket dpReceive = new DatagramPacket(responseBuffer, responseBuffer.length);
                ds.receive(dpReceive);
                String response = new String(dpReceive.getData(), 0, dpReceive.getLength());
                System.out.println("Received: \"" + response + "\"");

                if (response.toLowerCase().contains("registered")) {
                    registered = true;
                } else {
                    System.out.println("Registration failed, please try again.\n");
                }
            } else if (option.equals("2")) {
                System.out.print("Enter the unique name to deregister: ");
                String nameToRemove = sc.nextLine().trim();
                String deregMsg = "deregister," + nameToRemove;
                byte[] buf = deregMsg.getBytes();
                DatagramPacket dpSend = new DatagramPacket(buf, buf.length, serverIP, 420);
                ds.send(dpSend);

                byte[] responseBuffer = new byte[65535];
                DatagramPacket dpReceive = new DatagramPacket(responseBuffer, responseBuffer.length);
                ds.receive(dpReceive);
                String response = new String(dpReceive.getData(), 0, dpReceive.getLength());
                System.out.println("Server: " + response);
            } else if (option.equals("3")) {
                System.out.println("Exiting client...");
                ds.close();
                sc.close();
                return;
            } else {
                System.out.println("Invalid option. Try again.");
            }
        }

        // === POST-REGISTRATION LOGIC ===
        if (role.equals("seller")) {
            System.out.println("\n=== Item Listing (Seller) ===");
            while (true) {
                System.out.print("Enter item name (or type 'exit' to stop listing items): ");
                String itemName = sc.nextLine().trim();
                if (itemName.equalsIgnoreCase("exit")) {
                    break;
                }
            System.out.print("Enter item description: ");
            String description = sc.nextLine().trim();

            System.out.print("Enter starting price: ");
            String startingPrice = sc.nextLine().trim();

            System.out.print("Enter duration (in minutes): ");
            String duration = sc.nextLine().trim();

            String listItemMessage = "list_item," + itemName + "," + description + "," + startingPrice + "," + duration;
            byte[] buf = listItemMessage.getBytes();
            DatagramPacket dpSend = new DatagramPacket(buf, buf.length, serverIP, 420);
            ds.send(dpSend);
            System.out.println("Item listing message sent: " + listItemMessage);

            byte[] responseBuffer = new byte[65535];
            DatagramPacket dpReceive = new DatagramPacket(responseBuffer, responseBuffer.length);
            ds.receive(dpReceive);
            String response = new String(dpReceive.getData(), 0, dpReceive.getLength());
            System.out.println("Server response: " + response);
        }


        } else if (role.equals("buyer")) {
            System.out.println("\n=== Buyer Options ===");
            System.out.print("Do you want to see the item listings? (yes/no): ");
            String showListings = sc.nextLine().trim().toLowerCase();

            if (showListings.equals("yes")) {
                String getAllItems = "get_all_items";
                byte[] requestBuf = getAllItems.getBytes();
                DatagramPacket requestPacket = new DatagramPacket(requestBuf, requestBuf.length, serverIP, 420);
                ds.send(requestPacket);

                byte[] responseBuf = new byte[65535];
                DatagramPacket responsePacket = new DatagramPacket(responseBuf, responseBuf.length);
                ds.receive(responsePacket);
                String itemList = new String(responsePacket.getData(), 0, responsePacket.getLength());
                System.out.println("\n=== Current Items Listed ===\n" + itemList);
            }

            while (true) {
                System.out.println("\n--- Buyer Actions ---");
                System.out.println("1. Subscribe to item");
                System.out.println("2. Unsubscribe from item");
                System.out.println("3. Listen for auction announcements");
                System.out.println("4. Place a bid");
                System.out.println("5. Exit");
                System.out.print("Select option: ");

                String action = sc.nextLine().trim();

                switch (action) {
                    case "1": // Subscribe to an item
                        System.out.print("Enter Item Name to subscribe to: ");
                        String itemName = sc.nextLine().trim();
                        int subRQ = (int) (Math.random() * 10000);
                        String subscribeMsg = "subscribe," + subRQ + "," + itemName + "," + uniqueName;
                        ds.send(new DatagramPacket(subscribeMsg.getBytes(), subscribeMsg.length(), serverIP, 420));

                        byte[] subBuf = new byte[65535];
                        DatagramPacket subResponse = new DatagramPacket(subBuf, subBuf.length);
                        ds.receive(subResponse);
                        System.out.println("Server: " + new String(subResponse.getData(), 0, subResponse.getLength()));
                        break;

                    case "2": // Unsubscribe from an item
                        System.out.print("Enter Item Name to unsubscribe from: ");
                        itemName = sc.nextLine().trim();
                        int unsubRQ = (int) (Math.random() * 10000);
                        String unsubMsg = "de-subscribe," + unsubRQ + "," + itemName + "," + uniqueName;
                        ds.send(new DatagramPacket(unsubMsg.getBytes(), unsubMsg.length(), serverIP, 420));

                        byte[] unsubBuf = new byte[65535];
                        DatagramPacket unsubResponse = new DatagramPacket(unsubBuf, unsubBuf.length);
                        ds.receive(unsubResponse);
                        System.out.println("Server: " + new String(unsubResponse.getData(), 0, unsubResponse.getLength()));
                        break;

                    case "3": // Listen for auction announcements
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
                        break;

                    case "4": // Place a bid
                        System.out.print("Enter the item name to bid on: ");
                        itemName = sc.nextLine().trim();
                        System.out.print("Enter your bid amount: ");
                        String bidAmount = sc.nextLine().trim();

                        String bidMessage = "bid," + itemName + "," + uniqueName + "," + bidAmount;
                        //debugger
                        System.out.println("Sending bid request: " + bidMessage);

                        ds.send(new DatagramPacket(bidMessage.getBytes(), bidMessage.length(), serverIP, 420));

                        byte[] bidBuf = new byte[65535];
                        DatagramPacket bidResponse = new DatagramPacket(bidBuf, bidBuf.length);
                        ds.receive(bidResponse);
                        String response = new String(bidResponse.getData(), 0, bidResponse.getLength());
                        System.out.println("Server Response: " + response);
                        break;

                    case "5": // Exit the loop
                        System.out.println("Exiting...");
                        return; // Exits the method

                    default:
                        System.out.println("Invalid option. Please select a number between 1 and 5.");
                }
            }
        }



        System.out.println("Client exiting...");
        ds.close();
        sc.close();
    }
}