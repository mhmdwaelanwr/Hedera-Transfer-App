package anwar.mlsa.hadera.aou;

public class Transaction {
    public String transactionId;
    public String type;
    public String amount;
    public String party;
    public String date;
    public String status;
    public String memo;
    public String fee;
    public Double hbarPriceAtTxTime; // Price in USD at transaction time
    public String fiatCurrency; // Currency used for price
    public String feeFiat; // Fee in fiat currency
}
