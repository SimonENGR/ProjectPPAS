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
                    String[] parts = line.split(" ", 2); // Split the message into parts

                    if (parts.length < 2) {
                        System.err.println("Error: Received message has an invalid format (missing auction data): " + line);
                        return;
                    }

                    String auctionData = parts[1]; // The second part contains auction info like "RQ#189,pants,black,150.00,tommas"
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
                    if (role.equalsIgnoreCase("buyer") && highestBidderName.equals(uniqueName)) {
                        // Buyer prompt: Congratulations and credit card info
                        System.out.println("Congratulations, you won the auction!");
                        promptForCreditCard(reader, out, "buyer");
                        promptForMailingAddress(reader, out, "buyer" );
                    }
                    // Handle the seller's prompt
                    else if (role.equalsIgnoreCase("seller")) {
                        // Seller prompt: Item sold message and credit card info
                        if (finalPrice >= 0 && !highestBidderName.equalsIgnoreCase("None")) {
                            System.out.println("Your item has been sold to " + highestBidderName + "!");
                            promptForCreditCard(reader, out, "seller");
                            promptForMailingAddress(reader, out, "seller");
                        } else {
                            System.out.println("Your item did not meet the reserve price and was not sold.");
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling TCP message: " + e.getMessage());
        }
    }
}