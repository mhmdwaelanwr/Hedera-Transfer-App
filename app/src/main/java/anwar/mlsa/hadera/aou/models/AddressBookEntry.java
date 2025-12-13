package anwar.mlsa.hadera.aou.models;

public class AddressBookEntry {
    private String id;
    private String label;
    private String accountId;
    private String memo;
    private long createdAt;

    public AddressBookEntry(String id, String label, String accountId, String memo) {
        this.id = id;
        this.label = label;
        this.accountId = accountId;
        this.memo = memo;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
