package TCPFunctions;

import java.io.*;
import java.net.*;

public class TCPConnection {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public TCPConnection(String ipAddress, int port) throws IOException {
        this.socket = new Socket(ipAddress, port);
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public void sendMessage(String message) {
        out.println(message);  // Send the message to the connected client
    }

    public String receiveMessage() throws IOException {
        return in.readLine();  // Read the response from the server (if any)
    }

    public void close() throws IOException {
        out.close();
        in.close();
        socket.close();
    }
}