package TCPFunctions;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class TCPResponder {
    private int port;
    private String role;
    private String uniqueName;

    public TCPResponder(int port) {
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
                    String auctionDetails = parts[1]; // e.g., RQ#161,panties,red,26.00
                    String highestBidderName = "John Doe"; // Replace with actual highest bidder name

                    // Check if the role is "seller" or "buyer" and display corresponding message
                    if (role.equalsIgnoreCase("buyer")) {
                        System.out.println("Congratulations, you've won the auction for " + auctionDetails + "!");
                    } else if (role.equalsIgnoreCase("seller")) {
                        System.out.println("Your item has been sold to " + highestBidderName + "!");
                    }

                    // Prompt for credit card information (card number and expiry date with a dash in between)
                    System.out.print("Make sure to enter simulated credit card info as XXXX XXXX - XX XX: ");
                    String ccInfo = scanner.nextLine();

                    // Split the input into card number and expiry date
                    String[] ccParts = ccInfo.split(" - ");

                    // Validate the input format
                    if (ccParts.length == 2 && ccParts[0].matches("\\d{4} \\d{4}") && ccParts[1].matches("\\d{2} \\d{2}")) {
                        String cardNumber = ccParts[0];  // Credit card number in XXXX XXXX format
                        String expiryDate = ccParts[1];  // Expiry date in XX XX format

                        // Now you can use cardNumber and expiryDate separately
                        System.out.println("Credit card number: " + cardNumber);
                        System.out.println("Expiry date: " + expiryDate);

                        // Send the card information to the server (this part is handled by your logic)
                        out.println("CARD_INFO," + cardNumber + "," + expiryDate);
                        System.out.println("Credit card info sent to server.");
                    } else {
                        System.out.println("Invalid format. Please enter the credit card info as XXXX XXXX - XX XX.");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling TCP message: " + e.getMessage());
        }
    }
}