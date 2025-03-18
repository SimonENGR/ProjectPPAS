import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class TCPClient {
    private static final String SERVER_IP = "127.0.0.1"; // Change this to your server's IP
    private static final int SERVER_PORT = 443;         // Change this to your desired port

    private Socket socket;
    private Scanner scanner;
    private PrintWriter out;
    private BufferedReader in; // Reader to receive messages from the server

    public TCPClient(InetAddress serverAddress, int serverPort) throws Exception {
        this.socket = new Socket(serverAddress, serverPort);
        this.scanner = new Scanner(System.in);
        this.out = new PrintWriter(this.socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(this.socket.getInputStream())); // Initialize reader
    }

    public void start() {
        System.out.println("Connected to server at " + socket.getInetAddress() + ":" + socket.getPort());
        System.out.println("Type your message and press Enter to send.");

        // Start a new thread to listen for incoming messages
        new Thread(() -> {
            try {
                String serverMessage;
                while ((serverMessage = in.readLine()) != null) {
                    System.out.println("\n[Server]: " + serverMessage);
                    System.out.print("You: "); // Show input prompt again
                }
            } catch (IOException e) {
                System.out.println("Server connection closed.");
            }
        }).start();

        try {
            while (true) {
                System.out.print("You: "); // Input prompt
                String input = scanner.nextLine();
                if (input.equalsIgnoreCase("exit")) {
                    System.out.println("Closing connection...");
                    break;
                }
                out.println(input);
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            close();
        }
    }

    private void close() {
        try {
            if (out != null) out.close();
            if (in != null) in.close(); // Close input stream
            if (scanner != null) scanner.close();
            if (socket != null) socket.close();
            System.out.println("Connection closed.");
        } catch (IOException e) {
            System.out.println("Error closing resources: " + e.getMessage());
        }
    }
}
