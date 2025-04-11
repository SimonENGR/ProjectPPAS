package TCPFunctions;

import Utils.FileUtils;
import Utils.RegistrationInfo;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class TCPResponder {
    private int port;
    private String role;
    private String uniqueName;

    public TCPResponder(int port,String role, String uniqueName) {
        this.port = port;
        this.role = role;
        this.uniqueName = uniqueName;
    }

    public void start() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("TCPResponder listening on port " + port);

                while (true) {
                    Socket socket = serverSocket.accept();

                    new Thread(() -> handleConnection(socket)).start();
                }
            } catch (IOException e) {
                System.err.println("TCPResponder error: " + e.getMessage());
            }
        }).start();
    }

    private void promptForCreditCard(BufferedReader reader, PrintWriter out, String role) {
        try {
            System.out.print("Enter simulated credit card info as XXXX XXXX: ");
            String ccInfo = reader.readLine().trim();

            if (!ccInfo.matches("^\\d{4} \\d{4}$")) {
                System.out.println("Invalid credit card format. Please enter it as XXXX XXXX.");
                return;
            }

            System.out.print("Enter expiry date as XX XX: ");
            String expiryDate = reader.readLine().trim();

            if (!expiryDate.matches("^\\d{2} \\d{2}$")) {
                System.out.println("Invalid expiry date format. Please enter it as XX XX.");
                return;
            }

            out.println("CARD_INFO," + ccInfo + "," + expiryDate);
            System.out.println("Credit card info sent to server.");
        } catch (IOException e) {
            System.err.println("Error reading credit card info: " + e.getMessage());
        }
    }


    private void promptForMailingAddress(BufferedReader reader, PrintWriter out, String role) {
        try {
            System.out.print("Enter your address for mailing: ");
            String mailingAddress = reader.readLine().trim();

            if (!mailingAddress.isEmpty()) {
                out.println("MAILING_ADDRESS," + mailingAddress);
                System.out.println("Mailing address entered: " + mailingAddress);

                if (role.equalsIgnoreCase("buyer")) {
                    System.out.println("Sending mailing address to seller...");
                    out.println("SELLER," + mailingAddress);
                }
            } else {
                System.out.println("Invalid address. Please try again.");
            }
        } catch (IOException e) {
            System.err.println("Error reading mailing address: " + e.getMessage());
        }
    }

    private void handleConnection(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        ) {

            String line;

            boolean auctionEnded = false;

            while ((line = in.readLine()) != null) {
                System.out.println("\nTCP Message from Server: " + line);


                // Only respond if it's an auction-ended message
                if (line.contains("AUCTION_ENDED")) {
                    String auctionData = line.substring("AUCTION_ENDED ".length());
                    String[] auctionDetails = auctionData.split(",");

                    if (auctionDetails.length < 5) {
                        System.err.println("Error: Auction data is incomplete: " + auctionData);
                        return;  // Exit early if the message format is invalid
                    }

                    String auctionRequestNumber = auctionDetails[0];  // e.g., RQ#189
                    String itemName = auctionDetails[1];               // e.g., pants
                    String itemDescription = auctionDetails[2];        // e.g., black
                    double finalPrice = Double.parseDouble(auctionDetails[3]); // e.g., 150.00
                    String highestBidderName = auctionDetails[4];
                    int duration = 0;  // Always 0 for auction end

                    // Output the parsed details for verification
                    System.out.println("Auction Request Number: " + auctionRequestNumber);
                    System.out.println("Item Name: " + itemName);
                    System.out.println("Item Description: " + itemDescription);
                    System.out.println("Final Price: " + finalPrice);
                    System.out.println("Highest Bidder: " + highestBidderName);
                    System.out.println("Auction Duration: " + duration);

                    // Handle the buyer's prompt if the user is the highest bidder
                    if (line.contains("AUCTION_ENDED")) {
                        System.out.println("Auction ended: " + line);
                    }

                }
                else if (line.startsWith("INFORM_Req")) {
                    String[] tokens = line.split(",");
                    if (tokens.length != 4) {
                        System.err.println("Invalid INFORM_Req format: " + line);
                        return;
                    }

                    String rqNum = tokens[1];
                    String itemName = tokens[2];
                    String finalPrice = tokens[3];

                    System.out.println("\n=== Finalizing Purchase ===");
                    System.out.println("Item: " + itemName + ", Final Price: $" + finalPrice);

                    System.out.print("Enter credit card number (XXXX XXXX): ");
                    String ccNumber = reader.readLine().trim();

                    System.out.print("Enter expiry date (MM YY): ");
                    String expiry = reader.readLine().trim();

                    System.out.print("Enter shipping address (e.g., 123/Maple/City/Country — no commas): ");
                    String address = reader.readLine().trim();

                    // Send INFORM_Res to server
                    String response = String.format("INFORM_Res,%s,%s,%s,%s,%s", rqNum, uniqueName, ccNumber, expiry, address);
                    out.println(response);
                }
                else if (line.startsWith("Shipping_Info")) {
                    String[] tokens = line.split(",", 4);
                    if (tokens.length != 4) {
                        System.err.println("Invalid Shipping_Info format: " + line);
                        return;
                    }

                    String rq = tokens[1];
                    String buyerName = tokens[2];
                    String address = tokens[3];

                    System.out.println("\n=== Shipping Info Received ===");
                    System.out.println("Send your item to: " + buyerName);
                    System.out.println("Shipping Address: " + address);
                    System.out.println("Thank you for using the Auction System.");
                }

            }
        } catch (IOException e) {
            System.err.println("Error handling TCP message: " + e.getMessage());
        }
    }
}