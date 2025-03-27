package Utils;

public class ItemRegistry {
    private String itemName;
    private String description;
    private double startingPrice;
    private long duration; // in milliseconds
    private int requestNumber;

    public ItemRegistry(String itemName, String description, double startingPrice, long duration, int requestNumber) {
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
        this.duration = duration;
        this.requestNumber = requestNumber;
    }

    public String getItemName() { return itemName; }
    public String getDescription() { return description; }
    public double getStartingPrice() { return startingPrice; }
    public long getDuration() { return duration; }

    @Override
    public String toString() {
        return  "itemName = "+ itemName +
                ", description: " + description +
                ", startingPrice = " + startingPrice +
                ", duration = " + duration +
                ", requestNumber = RQ#" + requestNumber;
    }
}
