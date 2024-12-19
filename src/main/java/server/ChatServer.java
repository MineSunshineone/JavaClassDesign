package server;

import common.Message;
import common.Message.MessageType;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ChatServer {
    private static final Logger logger = Logger.getLogger(ChatServer.class.getName());
    private static final int INITIAL_PORT = 9000;

    private ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final Map<String, ClientHandler> onlineUsers;
    private final Map<String, List<Message>> offlineMessages;
    private final Properties config;
    private final FriendManager friendManager;
    private final MessageHistory messageHistory;
    private volatile boolean running;
    private Set<String> loggedMessages = new HashSet<>();
    private Set<String> forwardedMessages = Collections.synchronizedSet(new HashSet<>());


    public ChatServer() {
        this.executorService = Executors.newCachedThreadPool();
        this.onlineUsers = new ConcurrentHashMap<>();
        this.offlineMessages = new ConcurrentHashMap<>();
        this.config = new Properties();
        this.friendManager = new FriendManager();
        this.messageHistory = new MessageHistory();
        this.running = false;
        loadConfig();
    }

    private void loadConfig() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("server.properties")) {
            if (input == null) {
                logger.warning("配置文件未找到，使用默认配置");
                setDefaultConfig();
            } else {
                config.load(input);
            }
        } catch (IOException e) {
            logger.warning("加载配置文件时出错，使用默认配置: " + e.getMessage());
            setDefaultConfig();
        }
    }

    private void setDefaultConfig() {
        config.setProperty("port", String.valueOf(INITIAL_PORT));
        config.setProperty("maxConnections", "100");
    }

    public void start() {
        int port = Integer.parseInt(config.getProperty("port", String.valueOf(INITIAL_PORT)));
        int maxConnections = Integer.parseInt(config.getProperty("maxConnections", "100"));

        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("服务器启动在端口: " + port);
            logger.info("最大连接数: " + maxConnections);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    executorService.execute(handler);
                } catch (IOException e) {
                    if (running) {
                        logger.warning("接受客户端连接时出错: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.severe("服务器启动失败: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.warning("关闭服务器套接字时出错: " + e.getMessage());
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void addOnlineUser(String username, ClientHandler handler) {
        onlineUsers.put(username, handler);
        StorageManager.createUserFile(username);
        friendManager.loadFriendsList(username);
        broadcastStatusUpdate(username, true);
        broadcastOnlineUsers();
    }

    public void removeOnlineUser(String username) {
        onlineUsers.remove(username);
        broadcastStatusUpdate(username, false);
        broadcastOnlineUsers();
    }

    private void broadcastOnlineUsers() {
        StringBuilder onlineUsersList = new StringBuilder();
        for (String user : onlineUsers.keySet()) {
            onlineUsersList.append(user).append(",");
        }
        if (onlineUsersList.length() > 0) {
            onlineUsersList.setLength(onlineUsersList.length() - 1); // Remove trailing comma
        }
        Message onlineUsersMessage = new Message(null, null, onlineUsersList.toString(), MessageType.ONLINE_USERS);
        for (ClientHandler handler : onlineUsers.values()) {
            handler.sendMessage(onlineUsersMessage);
        }
    }

    public void storeOfflineMessage(Message message) {
        offlineMessages.computeIfAbsent(message.getTo(), k -> new ArrayList<>()).add(message);
        saveUserData(message.getTo());
    }

    private void saveUserData(String username) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(username + ".dat"))) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("friends", friendManager.getFriendsList(username));
            userData.put("offlineMessages", offlineMessages.get(username));
            oos.writeObject(userData);
        } catch (IOException e) {
            logger.warning("保存用户数据时出错: " + e.getMessage());
        }
    }

    public List<Message> loadOfflineMessagesFromFile(String username) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(username + ".dat"))) {
            Map<String, Object> userData = (Map<String, Object>) ois.readObject();
            return (List<Message>) userData.get("offlineMessages");
        } catch (IOException | ClassNotFoundException e) {
            logger.warning("加载离线消息时出错: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void broadcastStatusUpdate(String username, boolean online) {
        Message statusMsg = new Message(username, null,
            online ? "online" : "offline", MessageType.STATUS_UPDATE);

        for (ClientHandler handler : onlineUsers.values()) {
            handler.sendMessage(statusMsg);
        }
    }

    public List<Message> getOfflineMessages(String username) {
        return offlineMessages.remove(username);
    }

    public void handleFriendRequest(String from, String to) {
        if (friendManager.addFriendship(from, to)) {
            notifyFriendshipUpdate(from, to, true);
            saveUserData(from);
            saveUserData(to);
        }
    }

    private void notifyFriendshipUpdate(String from, String to, boolean isFriend) {
        // Implement the method to notify users about the friendship update
        Message updateMsg = new Message(from, to, isFriend ? "added" : "removed", MessageType.FRIEND_LIST_UPDATE);
        sendMessage(updateMsg);
    }

    private void sendMessage(Message message) {
        ClientHandler recipientHandler = onlineUsers.get(message.getTo());
        if (recipientHandler != null) {
            recipientHandler.sendMessage(message);
        } else {
            storeOfflineMessage(message);
        }
    }

    private void forwardMessage(Message message, boolean isOffline) {
        String messageId = message.getId(); // Assume each message has a unique ID

        synchronized (forwardedMessages) {
            if (forwardedMessages.contains(messageId)) {
                // Message already forwarded, skip
                return;
            }
            forwardedMessages.add(messageId);
        }

        // Add prefix for offline messages
        if (isOffline) {
            message.setContent("【离线消息】" + message.getContent());
        }

        sendMessage(message);

        deleteTemporaryFile(messageId);
    }

    private void deleteTemporaryFile(String messageId) {
    File tempFile = new File("user_data/" + messageId + ".dat");
    if (tempFile.exists()) {
        if (!tempFile.delete()) {
            System.err.println("删除临时文件失败: " + tempFile.getPath());
        }
    }
}

    void handleMessage(Message message) {
        messageHistory.addMessage(message);
        try {
            StorageManager.saveChatHistory(message.getFrom(), messageHistory.getHistory(message.getFrom()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        switch (message.getType()) {
            case OFFLINE_MESSAGE:
                forwardMessage(message, true);
                break;
            case FRIEND_REQUEST:
                handleFriendRequest(message.getFrom(), message.getTo());
                break;
            default:
                forwardMessage(message, false);
                break;
        }
    }

    public FriendManager getFriendManager() {
        return friendManager;
    }

    public ClientHandler getOnlineUser(String username) {
        return onlineUsers.get(username);
    }

    public static void main(String[] args) {
        StorageManager.initialize();
        ChatServer server = new ChatServer();
        server.start();
    }
}