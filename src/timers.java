import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class timers {
    private final int myLong = 2;  // in seconds
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void main() {
        scheduler.scheduleAtFixedRate(this::doStuffPeriodically, 10, myLong, TimeUnit.SECONDS);
    }

    public void doStuffPeriodically() {
        doStuff();
    }

    public void doStuff() {
        // do stuff here
        System.out.println("Doing stuff...");
    }

    public static void main(String[] args) {
        timers test = new timers();
        test.main();
        // Keep the main thread running to allow the ScheduledExecutorService to execute
        while (true) {
            try {
                Thread.sleep(1000);  // You can adjust the sleep time as needed
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}