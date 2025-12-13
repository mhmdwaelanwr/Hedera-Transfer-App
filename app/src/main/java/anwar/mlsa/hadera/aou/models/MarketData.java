package anwar.mlsa.hadera.aou.models;

public class MarketData {
    private double price;
    private double change24h;
    private double changePercent24h;
    private String fiatCurrency;
    private long timestamp;

    public MarketData(double price, double change24h, double changePercent24h, String fiatCurrency) {
        this.price = price;
        this.change24h = change24h;
        this.changePercent24h = changePercent24h;
        this.fiatCurrency = fiatCurrency;
        this.timestamp = System.currentTimeMillis();
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getChange24h() {
        return change24h;
    }

    public void setChange24h(double change24h) {
        this.change24h = change24h;
    }

    public double getChangePercent24h() {
        return changePercent24h;
    }

    public void setChangePercent24h(double changePercent24h) {
        this.changePercent24h = changePercent24h;
    }

    public String getFiatCurrency() {
        return fiatCurrency;
    }

    public void setFiatCurrency(String fiatCurrency) {
        this.fiatCurrency = fiatCurrency;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isPositiveChange() {
        return changePercent24h >= 0;
    }
}
