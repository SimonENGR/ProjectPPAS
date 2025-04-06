// ItemRegistry.java
package Utils;

public class ItemRegistry {
    private String itemName;
    private String description;
    private double startingPrice;
    private double currentPrice;
    private String highestBidder;
    private long duration;
    private long startTime;
    private boolean negotiationSent;
    private int requestNumber;
    private String sellerName;

    public ItemRegistry(String itemName, String description, double startingPrice, long duration, int requestNumber, String sellerName) {
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
        this.highestBidder = "None";
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
        this.negotiationSent = false;
        this.requestNumber = requestNumber;
        this.sellerName = sellerName;
    }

    public static ItemRegistry fromCSV(String csv) {
        String[] tokens = csv.split(",");
        if (tokens.length < 10) throw new IllegalArgumentException("Malformed auction line");

        String itemName = tokens[0].trim();
        String sellerName = tokens[1].trim();
        String description = tokens[2].trim();
        double startingPrice = Double.parseDouble(tokens[3].trim());
        double currentPrice = Double.parseDouble(tokens[4].trim());
        String highestBidder = tokens[5].trim();
        long duration = Long.parseLong(tokens[6].trim());
        long startTime = Long.parseLong(tokens[7].trim());
        boolean negotiationSent = Boolean.parseBoolean(tokens[8].trim());
        int requestNumber = Integer.parseInt(tokens[9].trim().split("#")[1]);

        ItemRegistry item = new ItemRegistry(itemName, description, startingPrice, duration, requestNumber, sellerName);
        item.currentPrice = currentPrice;
        item.highestBidder = highestBidder;
        item.startTime = startTime;
        item.negotiationSent = negotiationSent;
        return item;
    }

    public String toCSV() {
        return String.format("%s,%s,%s,%.2f,%.2f,%s,%d,%d,%s,RQ#%d",
                itemName,
                sellerName,
                description,
                startingPrice,
                currentPrice,
                highestBidder,
                duration,
                startTime,
                negotiationSent,
                requestNumber);
    }

    public String getItemName() { return itemName; }
    public String getDescription() { return description; }
    public double getStartingPrice() { return startingPrice; }
    public double getCurrentPrice() { return currentPrice; }
    public String getHighestBidder() { return highestBidder; }
    public long getDuration() { return duration; }
    public long getStartTime() { return startTime; }
    public long getTimeRemaining() { return (startTime + duration) - System.currentTimeMillis(); }
    public boolean isNegotiationSent() { return negotiationSent; }
    public void setNegotiationSent(boolean sent) { this.negotiationSent = sent; }
    public int getRequestNumber() { return requestNumber; }
    public String getSellerName() { return sellerName; }

    public boolean placeBid(String bidder, double bidAmount) {
        if (bidAmount > currentPrice) {
            currentPrice = bidAmount;
            highestBidder = bidder;
            return true;
        }
        return false;
    }

    public void adjustPrice(double newPrice) {
        this.currentPrice = newPrice;
        this.highestBidder = "None";
    }
}
