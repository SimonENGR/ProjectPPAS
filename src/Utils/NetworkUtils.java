package Utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

public class NetworkUtils {

    public static void sendMessageToClient(DatagramSocket ds, InetAddress clientIP, int clientPort, String message) {
        try {
            byte[] buf = message.getBytes();
            DatagramPacket dpSend = new DatagramPacket(buf, buf.length, clientIP, clientPort);

            // Optional: delay to prevent flooding
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                System.err.println("Sleep interrupted: " + e.getMessage());
            }

            System.out.println("Sending message to client: " + message);
            ds.send(dpSend);
            System.out.println("Sent response to " + clientIP + ":" + clientPort);
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    public static void broadcastToBuyers(String message, DatagramSocket ds, String accountFilePath) {
        List<RegistrationInfo> buyers = FileUtils.getAllRegisteredBuyers(accountFilePath);

        for (RegistrationInfo buyer : buyers) {
            if (buyer.getRole().equalsIgnoreCase("buyer")) {
                try {
                    byte[] buf = message.getBytes();
                    DatagramPacket packet = new DatagramPacket(buf, buf.length,
                            InetAddress.getByName(buyer.getIpAddress()), buyer.getUdpPort());
                    ds.send(packet);
                } catch (IOException e) {
                    System.err.println("Failed to send update to buyer: " + e.getMessage());
                }
            }
        }
    }
}
