package UDPFunctions;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

public class UDPClient {
    public static void main(String[] args) throws IOException {
        Scanner sc = new Scanner(System.in);

        // Create DatagramSocket object for communication
        DatagramSocket ds = new DatagramSocket(12345); // Ensure the client listens on the same port

        InetAddress ip = InetAddress.getLocalHost(); // Get server's IP (local machine)

        System.out.println("Client started. Type your messages below:");

        byte[] buf = null;

        while (true) {
            // Take input from user
            System.out.println("Client listening on IP: " + ip + ", Port: " + ds.getLocalPort());
            System.out.print("Enter message: ");
            String inp = sc.nextLine();
            buf = inp.getBytes(); // Encode input to bytes

            // Create a DatagramPacket and send data to the server
            DatagramPacket dpSend = new DatagramPacket(buf, buf.length, ip, 420);
            System.out.println("Sending message to server: " + inp);
            ds.send(dpSend);

            // Exit the loop if the user inputs "bye"
            if (inp.equalsIgnoreCase("bye")) {
                System.out.println("Client sent bye...EXITING");
                break;
            }

            // Wait for the server's response
            byte[] receiveBuf = new byte[65535];
            DatagramPacket dpReceive = new DatagramPacket(receiveBuf, receiveBuf.length);

            try {
                System.out.println("Waiting for server response...");
                ds.receive(dpReceive);

                // Extract and print the server's response
                String receivedMessage = new String(dpReceive.getData(), 0, dpReceive.getLength());
                System.out.println("Server Response Received: " + receivedMessage);
            } catch (IOException e) {
                System.err.println("Error receiving server response: " + e.getMessage());
            }
        }

        // Close resources
        ds.close();
        sc.close();
    }
}
