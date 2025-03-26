package UDPFunctions;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

public class UDPClient {
    public static void main(String args[]) throws IOException {
        Scanner sc = new Scanner(System.in);
        DatagramSocket ds = new DatagramSocket();
        InetAddress serverIP = InetAddress.getLocalHost(); // Get server IP

        boolean registered = false;
        String role = "";
        String uniqueName = "";
        String udpPort = "";
        String tcpPort = "";
        String clientIP = InetAddress.getLocalHost().getHostAddress();

        // ---------------------------
        // Registration Phase (Loop)
        // ---------------------------
        while (!registered) {
            System.out.println("=== Registration ===");
            System.out.println("Client listening on port: " + ds.getLocalPort());
            System.out.print("Enter your unique name: ");
            uniqueName = sc.nextLine().trim();

            System.out.print("Enter your role (buyer/seller): ");
            role = sc.nextLine().trim().toLowerCase();

            System.out.print("Enter your UDP port: ");
            udpPort = sc.nextLine().trim();

            System.out.print("Enter your TCP port: ");
            tcpPort = sc.nextLine().trim();

            // Build registration message in the expected format:
            // "register,uniqueName,role,ipAddress,udpPort,tcpPort"
            String regMessage = "register," + uniqueName + "," + role + "," + clientIP + "," + udpPort + "," + tcpPort;
            byte[] buf = regMessage.getBytes();
            DatagramPacket dpSend = new DatagramPacket(buf, buf.length, serverIP, 420);
            ds.send(dpSend);
            System.out.println("Registration message sent: " + regMessage);

            // Wait for server response
            byte[] responseBuffer = new byte[65535];
            DatagramPacket dpReceive = new DatagramPacket(responseBuffer, responseBuffer.length);

            try {
                ds.receive(dpReceive);  // ✅ Wait for a response from the server
                String response = new String(dpReceive.getData(), 0, dpReceive.getLength());

                // ✅ Debugging output
                System.out.println("Received raw response: \"" + response + "\"");

                if (response.toLowerCase().contains("registered")) {
                    registered = true;
                } else {
                    System.out.println("Registration failed, please try again.\n");
                }
            } catch (IOException e) {
                System.err.println("Error receiving response from server: " + e.getMessage());
            }
        }

        // ---------------------------
        // Post-Registration Action
        // ---------------------------
        if (role.equals("seller")) {
            // Seller: prompt for item details to list an item.
            System.out.println("\n=== Item Listing (Seller) ===");
            System.out.print("Enter item name: ");
            String itemName = sc.nextLine().trim();

            System.out.print("Enter item description: ");
            String description = sc.nextLine().trim();

            System.out.print("Enter starting price: ");
            String startingPrice = sc.nextLine().trim();

            System.out.print("Enter duration (in milliseconds): ");
            String duration = sc.nextLine().trim();

            // Build list item message in the format:
            // "list_item,itemName,description,startingPrice,duration"
            String listItemMessage = "list_item," + itemName + "," + description + "," + startingPrice + "," + duration;
            byte[] buf = listItemMessage.getBytes();
            DatagramPacket dpSend = new DatagramPacket(buf, buf.length, serverIP, 420);
            ds.send(dpSend);
            System.out.println("Item listing message sent: " + listItemMessage);

            // Wait for server response regarding item listing.
            byte[] responseBuffer = new byte[65535];
            DatagramPacket dpReceive = new DatagramPacket(responseBuffer, responseBuffer.length);
            ds.receive(dpReceive);
            String response = new String(dpReceive.getData(), 0, dpReceive.getLength());
            System.out.println("Server response: " + response);

        } else if (role.equals("buyer")) {
            // Buyer: ask if they want to see item listings.
            System.out.println("\n=== Buyer Options ===");
            System.out.print("Do you want to see the item listings? (yes/no): ");
            String showListings = sc.nextLine().trim().toLowerCase();

            if (showListings.equals("yes")) {
                System.out.println("Waiting for item listings (broadcast messages) from the server. Type 'bye' to exit.");

                // Loop to continuously receive broadcast messages.
                while (true) {
                    byte[] responseBuffer = new byte[65535];
                    DatagramPacket dpReceive = new DatagramPacket(responseBuffer, responseBuffer.length);
                    ds.receive(dpReceive);
                    String response = new String(dpReceive.getData(), 0, dpReceive.getLength());

                    if (response.equalsIgnoreCase("bye")) {
                        System.out.println("Exiting broadcast listener.");
                        break;
                    }

                    System.out.println("Broadcast message: " + response);
                }
            }
        }

        System.out.println("Client exiting...");
        ds.close();
        sc.close();
    }
}
