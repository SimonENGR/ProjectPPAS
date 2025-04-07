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
        if (role.equalsIgnoreCase("buyer")) {
            System.out.println("Congratulations, you won the auction!");
        } else if (role.equalsIgnoreCase("seller")) {
            System.out.println("Your item has been sold to the highest bidder!");
        }

        // Prompt for credit card information
        System.out.print("Enter simulated credit card info as XXXX XXXX - XX XX: ");
        String ccInfo = scanner.nextLine();

        // Split and validate the card info
        String[] ccParts = ccInfo.split(" - ");
        if (ccParts.length == 2 && ccParts[0].matches("\\d{4} \\d{4}") && ccParts[1].matches("\\d{2} \\d{2}")) {
            String cardNumber = ccParts[0];  // Credit card number in XXXX XXXX format
            String expiryDate = ccParts[1];  // Expiry date in XX XX format

            // Send the card information to the server
            out.println("CARD_INFO," + cardNumber + "," + expiryDate);
            System.out.println("Credit card info sent to server.");
        } else {
            System.out.println("Invalid format. Please enter the credit card info as XXXX XXXX - XX XX.");
        }
    }

    private void handleConnection(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                Scanner scanner = new Scanner(System.in)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("\nTCP Message from Server: " + line);

                // Only respond if it's an auction-ended message
                if (line.contains("AUCTION_ENDED")) {
                    String[] parts = line.split(" "); // Split the message into parts
                    String auctionRequestNumber = parts[1];  // e.g., 161
                    String itemName = parts[2];               // e.g., panties
                    String itemDescription = parts[3];        // e.g., red
                    double finalPrice = Double.parseDouble(parts[4]); // e.g., 26.00
                    String highestBidderName = parts[5];      // e.g., qwer
                    int duration = Integer.parseInt(parts[6]); // e.g., 0 (auction duration)

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
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling TCP message: " + e.getMessage());
        }
    }
}