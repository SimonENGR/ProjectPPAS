package Utils;

public class ItemRegistry {
    private String itemName;
    private String description;
    private double startingPrice;
    private long duration; // in milliseconds

    public ItemRegistry(String itemName, String description, double startingPrice, long duration) {
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
        this.duration = duration;
    }

    public String getItemName() { return itemName; }
    public String getDescription() { return description; }
    public double getStartingPrice() { return startingPrice; }
    public long getDuration() { return duration; }

    @Override
    public String toString() {
        return  "ItemInfo{ " +
                "itemName = "+ itemName +
                ", description: " + description +
                ", startingPrice = " + startingPrice +
                ", duration = " + duration +
                " }";
    }
}
