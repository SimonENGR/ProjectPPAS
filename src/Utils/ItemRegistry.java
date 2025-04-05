package Utils;

public class ItemRegistry {
    private String itemName;
    private String description;
    private double startingPrice;
    private double currentPrice;
    private String highestBidder;
    private long duration; // in milliseconds
    private long startTime;
    private int requestNumber;

    public ItemRegistry(String itemName, String description, double startingPrice, long duration, int requestNumber) {
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
        this.highestBidder = "None";
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
        this.requestNumber = requestNumber;
    }

    public String getItemName() { return itemName; }
    public String getDescription() { return description; }
    public double getStartingPrice() { return startingPrice; }
    public double getCurrentPrice() { return currentPrice; }
    public String getHighestBidder() { return highestBidder; }
    public long getDuration() { return duration; }
    public long getStartTime() { return startTime; }
    public int getRequestNumber() { return requestNumber; }

    public void setHighestBidder(String highestBidder) {
        this.highestBidder = highestBidder;
    }

    public String getTimeRemaining() {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = Math.max(0, duration - elapsed);

        long hours = remaining / 3600000;
        long minutes = (remaining % 3600000) / 60000;
        long seconds = (remaining % 60000) / 1000;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public boolean placeBid(String bidder, double bidAmount) {
        if (bidAmount > currentPrice) {
            currentPrice = bidAmount;
            highestBidder = bidder;
            return true;
        }
        return false;
    }


    public String toCSV() {
        return String.format("%s,%s,%.2f,%.2f,%s,%d,%d,RQ#%d",
                itemName,
                description,
                startingPrice,
                currentPrice,
                highestBidder,
                duration,
                startTime,
                requestNumber);
    }
}
