package anwar.mlsa.hadera.aou.models;

public class NetworkAlert {
    private String id;
    private String level; // "INFO", "WARN", "CRITICAL"
    private String message;
    private long startTime;
    private long endTime;

    public NetworkAlert(String id, String level, String message, long startTime, long endTime) {
        this.id = id;
        this.level = level;
        this.message = message;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public boolean isActive() {
        long now = System.currentTimeMillis();
        return now >= startTime && (endTime == 0 || now <= endTime);
    }
}
