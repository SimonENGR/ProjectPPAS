import java.net.InetAddress;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class Main {
    private static final String SERVER_IP = "127.0.0.1"; // Change this to your server's IP
    private static final int SERVER_PORT = 443;
    public static void main(String[] args) {
       //TCPClient code
        System.out.println("Connecting to server at " + SERVER_IP + ":" + SERVER_PORT + "...");
        //TCPClient client = new TCPClient(InetAddress.getByName(SERVER_IP), SERVER_PORT);
        // client.start();
    }
}
