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

    private void promptForCreditCard(Scanner scanner, PrintWriter out, String role) {
        // Check if the role is "buyer" or "seller" and modify the message accordingly
//        if (role.equalsIgnoreCase("buyer")) {
//            System.out.println("Congratulations, you won the auction!");
//        } else if (role.equalsIgnoreCase("seller")) {
//            System.out.println("Your item has been sold to the highest bidder!");
//        }

        // Prompt for credit card information
        System.out.print("Enter simulated credit card info as XXXX XXXX: ");
        String ccInfo = scanner.nextLine().trim();

        // Validate the credit card input
        if (ccInfo.matches("^\\d{4} \\d{4}$")) {
            System.out.println("Credit card info entered: " + ccInfo);
        } else {
            System.out.println("Invalid credit card format. Please enter it as XXXX XXXX.");
            return; // Exit or handle as needed
        }

        // Prompt for expiry date
        System.out.print("Enter expiry date as XX XX: ");
        String expiryDate = scanner.nextLine().trim();

        // Validate the expiry date input
        if (expiryDate.matches("^\\d{2} \\d{2}$")) {
            System.out.println("Expiry date entered: " + expiryDate);
        } else {
            System.out.println("Invalid expiry date format. Please enter it as XX XX.");
            return; // Exit or handle as needed
        }

        // Send the card information to the server
        out.println("CARD_INFO," + ccInfo + "," + expiryDate);
        System.out.println("Credit card info sent to server.");
    }

    private void promptForMailingAddress(Scanner scanner, PrintWriter out, String role) {
        // Prompt for mailing address based on the role
        System.out.print("Enter your address for mailing: ");
        String mailingAddress = scanner.nextLine().trim();

        if (mailingAddress != null && !mailingAddress.isEmpty()) {
            // Send the mailing address to the server
            out.println("MAILING_ADDRESS," + mailingAddress);
            System.out.println("Mailing address entered: " + mailingAddress);

            // If buyer, send address to seller for shipping purposes
            if (role.equalsIgnoreCase("buyer")) {
                System.out.println("Sending mailing address to seller...");
                // Simulate sending to seller (this could be modified to use TCP communication)
                out.println("SELLER," + mailingAddress);
            }
        } else {
            System.out.println("Invalid address. Please try again.");
        }
    }

    private void handleConnection(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                Scanner scanner = new Scanner(System.in)
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
                        promptForCreditCard(scanner, out, "buyer");
                        promptForMailingAddress(scanner, out, "buyer" );
                    }
                    // Handle the seller's prompt
                    else if (role.equalsIgnoreCase("seller")) {
                        // Seller prompt: Item sold message and credit card info
                        if (finalPrice >= 0) {
                            System.out.println("Your item has been sold to " + highestBidderName + "!");
                        } else {
                            System.out.println("Your item did not meet the reserve price and was not sold.");
                        }
                        promptForCreditCard(scanner, out, "seller");
                        promptForMailingAddress(scanner, out, "seller");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling TCP message: " + e.getMessage());
        }
    }
}