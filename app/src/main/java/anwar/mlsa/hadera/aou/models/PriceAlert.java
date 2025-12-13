package anwar.mlsa.hadera.aou.models;

public class PriceAlert {
    private String id;
    private double targetPrice;
    private String direction; // "ABOVE" or "BELOW"
    private boolean active;
    private long createdAt;
    private String fiatCurrency;

    public PriceAlert(String id, double targetPrice, String direction, boolean active, String fiatCurrency) {
        this.id = id;
        this.targetPrice = targetPrice;
        this.direction = direction;
        this.active = active;
        this.createdAt = System.currentTimeMillis();
        this.fiatCurrency = fiatCurrency;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(double targetPrice) {
        this.targetPrice = targetPrice;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getFiatCurrency() {
        return fiatCurrency;
    }

    public void setFiatCurrency(String fiatCurrency) {
        this.fiatCurrency = fiatCurrency;
    }
}
