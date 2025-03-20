package UDPFunctions;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UDPServer {


    public static void main(String[] args) throws IOException {
        // Create a DatagramSocket to listen on port 420
        DatagramSocket ds = new DatagramSocket(420);
        byte[] receive = new byte[65535];

        DatagramPacket dpReceive = null;

        while (true) {
            // Create a DatagramPacket to receive data
            dpReceive = new DatagramPacket(receive, receive.length);
            ds.receive(dpReceive);

            // Convert byte array to string and print received data
            String msg = new String(receive, 0, dpReceive.getLength());
            System.out.println("Client: " + msg);

            // Exit if client sends "bye"
            if (msg.equals("bye")) {
                System.out.println("Client sent bye...EXITING");
                break;
            }

            // Clear buffer
            receive = new byte[65535];
        }
        ds.close();
    }
}
