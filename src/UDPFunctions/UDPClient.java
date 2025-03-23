package UDPFunctions;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

public class UDPClient {
    public static void main(String args[]) throws IOException {
        Scanner sc = new Scanner(System.in);

        // Create DatagramSocket object
        DatagramSocket ds = new DatagramSocket();
        InetAddress ip = InetAddress.getLocalHost(); // Get server IP
        int serverPort = 420;

        byte[] buf = null;

        while (true) {
            // Take input from user
            System.out.println("Please enter Registration Details: ");
            String inp = sc.nextLine();
            buf = inp.getBytes(); // Encode to Bytes

            // Create a DatagramPacket and send data
            DatagramPacket dpSend = new DatagramPacket(buf, buf.length, ip, 420);
            ds.send(dpSend);

            // Exit loop if user inputs "bye"
            if (inp.equals("bye")) {
                System.out.println("Client sent bye...EXITING");
                break;
            }
        }

        ds.close();
        sc.close();
    }
}
