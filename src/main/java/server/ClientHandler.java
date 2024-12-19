package server;

import common.JsonUtil;
import common.Message;
import common.Message.MessageType;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    private final Socket socket;
    private final ChatServer server;
    private String username;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean running;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        this.running = true;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            logger.severe("创建客户端处理器时出错: " + e.getMessage());
            running = false;
        }
    }

    @Override
    public void run() {
        try {
            handleLogin();
            while (running && !socket.isClosed()) {
                String jsonMessage = in.readLine();
                if (jsonMessage == null) {
                    break;
                }
                handleMessage(jsonMessage);
            }
        } catch (IOException e) {
            logger.warning("客户端连接异常: " + e.getMessage());
        } finally {
            handleDisconnect();
        }
    }

    private void handleLogin() throws IOException {
        String jsonMessage = in.readLine();
        if (jsonMessage == null) {
            throw new IOException("登录消息为空");
        }

        try {
            Message loginMsg = JsonUtil.fromJson(jsonMessage, Message.class);
            if (loginMsg.getType() != MessageType.LOGIN) {
                sendMessage(new Message(null, null, "无效的登录尝试", MessageType.LOGIN));
                throw new IOException("无效的登录尝试");
            }

            this.username = loginMsg.getFrom();
            server.addOnlineUser(username, this);

            // 发送好友列表
            Set<String> friends = server.getFriendManager().getFriendsList(username);
            sendMessage(new Message(null, username, String.join(",", friends), MessageType.FRIEND_LIST));

            // 发送离线消息
            List<Message> offlineMessages = server.getOfflineMessages(username);
            if (offlineMessages != null) {
                for (Message msg : offlineMessages) {
                    sendMessage(msg);
                }
            }
        } catch (Exception e) {
            logger.warning("处理登录消息时出错: " + e.getMessage());
            throw new IOException("登录处理失败", e);
        }
    }

    private void handleMessage(String jsonMessage) {
        try {
            Message message = JsonUtil.fromJson(jsonMessage, Message.class);
            server.handleMessage(message);
        } catch (Exception e) {
            logger.warning("处理消息时出错: " + e.getMessage());
        }
    }

    public void sendMessage(Message message) {
        try {
            out.println(JsonUtil.toJson(message));
        } catch (Exception e) {
            logger.warning("发送消息失败: " + e.getMessage());
        }
    }

    private void handleDisconnect() {
        try {
            if (username != null) {
                server.removeOnlineUser(username);
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getUsername() {
        return username;
    }
}