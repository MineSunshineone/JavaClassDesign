package common;

import java.io.Serializable;
import java.util.UUID;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MessageType {
        LOGIN, LOGOUT, CHAT, FRIEND_REQUEST, FRIEND_LIST, STATUS_UPDATE, ONLINE_USERS, REMOVE_FRIEND, FRIEND_LIST_UPDATE, OFFLINE_MESSAGE, FILE
    }

    private String id;
    private String from;
    private String to;
    private String content;
    private MessageType type;
    private long timestamp;
    
    public Message(String from, String to, String content, MessageType type) {
        this.id = UUID.randomUUID().toString();
        this.from = from;
        this.to = to;
        this.content = content;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and Setters

    public String getId() { return id; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
} 