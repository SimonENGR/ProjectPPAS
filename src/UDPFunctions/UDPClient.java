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

        System.out.println("Enter registration details:");

        System.out.print("Unique Name: ");
        String uniqueName = sc.nextLine();

        System.out.print("Role (buyer/seller): ");
        String role = sc.nextLine().toLowerCase();

        System.out.print("IP Address: ");
        String clientIp = sc.nextLine();

        System.out.print("UDP Port: ");
        String udpPort = sc.nextLine();

        System.out.print("TCP Port: ");
        String tcpPort = sc.nextLine();

        // Build registration message in the format:
        // register,uniqueName,role,ipAddress,udpPort,tcpPort
        String registrationMsg = String.format("register,%s,%s,%s,%s,%s",
                uniqueName, role, clientIp, udpPort, tcpPort);
        byte[] regBuf = registrationMsg.getBytes();

        DatagramPacket dpReg = new DatagramPacket(regBuf, regBuf.length, ip, serverPort);
        ds.send(dpReg);
        System.out.println("Registration message sent: " + registrationMsg);

        while (true) {
            String inp = sc.nextLine();
            byte[] buf = inp.getBytes();
            DatagramPacket dpSend = new DatagramPacket(buf, buf.length, ip, serverPort);
            ds.send(dpSend);
            if (inp.equalsIgnoreCase("bye")) {
                System.out.println("Client sent bye...EXITING");
                break;
            }
        }

        ds.close();
        sc.close();
    }
}
