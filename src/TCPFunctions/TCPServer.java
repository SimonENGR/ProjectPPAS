package TCPFunctions;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {
    private ServerSocket server;

    public TCPServer(String ipAddress, int port) throws Exception {
        if (ipAddress != null && !ipAddress.isEmpty())
            this.server = new ServerSocket(port, 1, InetAddress.getByName(ipAddress));
        else
            this.server = new ServerSocket(port, 1, InetAddress.getLocalHost());
    }

    public void start() throws Exception {
        System.out.println("\nRunning TCP Server: Host=" + getSocketAddress().getHostAddress() + " Port=" + getPort());
        listen();
    }

    public void listen() throws Exception {
        System.out.println("Waiting for TCP client connections...");

        while (true) {
            try (Socket client = this.server.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                 PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

                String clientAddress = client.getInetAddress().getHostAddress();
                System.out.println("New TCP connection from " + clientAddress);

                String data;
                while ((data = in.readLine()) != null) {
                    System.out.println("Message from " + clientAddress + ": " + data);
                    out.println("Server received: " + data);
                }

            } catch (Exception e) {
                System.out.println("Error handling client: " + e.getMessage());
            }
        }
    }

    public InetAddress getSocketAddress() {
        return this.server.getInetAddress();
    }

    public int getPort() {
        return this.server.getLocalPort();
    }

    // Optional main method for testing only
    public static void main(String[] args) throws Exception {
        int port = 443;
        String ipAddress = "127.0.0.1";

        if (args.length > 0) {
            ipAddress = args[0];
        }
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }

        TCPServer app = new TCPServer(ipAddress, port);
        app.start();
    }
}
