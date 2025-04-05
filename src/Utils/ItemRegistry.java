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

    public long getTimeRemaining() {
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, duration - elapsed);
    }

    public boolean placeBid(String bidder, double bidAmount) {
        if (bidAmount > currentPrice) {
            currentPrice = bidAmount;
            highestBidder = bidder;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return  "itemName = " + itemName +
                ", description: " + description +
                ", startingPrice = " + startingPrice +
                ", currentPrice = " + currentPrice +
                ", highestBidder = " + highestBidder +
                ", duration = " + duration +
                ", timeRemaining = " + getTimeRemaining() / 60000 + " minutes" +
                ", requestNumber = RQ#" + requestNumber;
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
