package server;

import common.Message;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MessageHistory {
    private static final int MAX_HISTORY_PER_USER = 100;
    private Map<String, Queue<Message>> messageHistory;
    private Path historyFile;
    private Set<String> loggedMessages = Collections.synchronizedSet(new HashSet<>());

    public MessageHistory() {
        messageHistory = new ConcurrentHashMap<>();
        historyFile = Paths.get("message_history.dat");
        loadHistory();
    }

    public void addMessage(Message message) {
        String key = getHistoryKey(message.getFrom(), message.getTo());
        Queue<Message> history = messageHistory.computeIfAbsent(key, k -> new LinkedList<>());

        history.offer(message);
        while (history.size() > MAX_HISTORY_PER_USER) {
            history.poll();
        }

        logMessage(message);
        saveHistory();
    }

    public List<Message> getHistory(String username) {
        List<Message> userMessages = new ArrayList<>();
        for (Queue<Message> messages : messageHistory.values()) {
            for (Message message : messages) {
                if (message.getFrom().equals(username) || message.getTo().equals(username)) {
                    userMessages.add(message);
                }
            }
        }
        return userMessages;
    }

    public List<Message> getHistory(String user1, String user2) {
        String key = getHistoryKey(user1, user2);
        return new ArrayList<>(messageHistory.getOrDefault(key, new LinkedList<>()));
    }

    private String getHistoryKey(String user1, String user2) {
        return user1.compareTo(user2) < 0 ? user1 + ":" + user2 : user2 + ":" + user1;
    }

    private void loadHistory() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(historyFile.toFile()))) {
            messageHistory = (Map<String, Queue<Message>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            messageHistory = new ConcurrentHashMap<>();
        }
    }

    private void saveHistory() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(historyFile.toFile()))) {
            oos.writeObject(messageHistory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logMessage(Message message) {
        if (message.getFrom() == null || message.getTo() == null) {
            // 忽略系统消息
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
        String logEntry = String.format("[%s] %s >> %s : %s",
                LocalDateTime.ofEpochSecond(message.getTimestamp() / 1000, 0, ZoneOffset.UTC).format(formatter),
                message.getFrom(),
                message.getTo(),
                message.getContent());

        synchronized (loggedMessages) {
            if (loggedMessages.contains(logEntry)) {
                // 消息已记录，跳过
                return;
            }
            loggedMessages.add(logEntry);
        }

        try (FileWriter fw = new FileWriter("user_data/latest.log", true)) {
            fw.write(logEntry + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}