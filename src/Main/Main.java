package Main;
import TCPFunctions.*;
import UDPFunctions.*;

public class Main {
    private static final String SERVER_IP = "127.0.0.1"; // Change this to your server's IP
    private static final int SERVER_PORT = 420;


    public static void main(String[] args) {
       //TCPClient code
        System.out.println("Connecting to server at " + SERVER_IP + ":" + SERVER_PORT + "...");
        //TCPClient client = new TCPClient(InetAddress.getByName(SERVER_IP), SERVER_PORT);
        // client.start();
    }
}
