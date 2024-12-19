// ChatClient.java
package client;

import common.JsonUtil;
import common.Message;
import common.Message.MessageType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class ChatClient extends JFrame {
    private JTextArea chatArea;
    private JTextField messageField;
    private JList<String> friendList;
    private JList<String> onlineClientList;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private Properties config;
    private JPanel bottomPanel;
    private Set<String> loggedMessages = Collections.synchronizedSet(new HashSet<>());

    public ChatClient() {
        super("聊天客户端");
        initComponents();
        loadConfig();
        showLoginDialog();
        loadFriendList();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));

        // 聊天区域
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setPreferredSize(new Dimension(400, 300));

        // 好友列表
        DefaultListModel<String> friendListModel = new DefaultListModel<>();
        friendList = new JList<>(friendListModel);
        friendList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane friendScrollPane = new JScrollPane(friendList);
        friendScrollPane.setPreferredSize(new Dimension(150, 300));

        // 在线客户端列表
        DefaultListModel<String> onlineClientListModel = new DefaultListModel<>();
        onlineClientList = new JList<>(onlineClientListModel);
        onlineClientList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane onlineClientScrollPane = new JScrollPane(onlineClientList);
        onlineClientScrollPane.setPreferredSize(new Dimension(150, 300));

        onlineClientList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = onlineClientList.locationToIndex(e.getPoint());
                    if (index != -1) {
                        String selectedUser = onlineClientList.getModel().getElementAt(index);
                        showContextMenu(onlineClientList, e.getX(), e.getY(), selectedUser);
                    }
                }
            }
        });
        // 添加选择监听器
        onlineClientList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                friendList.clearSelection();
            }
        });

        friendList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onlineClientList.clearSelection();
            }
        });

        // 标签
        JLabel friendListLabel = new JLabel("好友列表");
        JLabel chatAreaLabel = new JLabel("聊天区域");
        JLabel onlineClientListLabel = new JLabel("在线用户");

        // 带标签的面板
        JPanel friendListPanel = new JPanel(new BorderLayout());
        friendListPanel.add(friendListLabel, BorderLayout.NORTH);
        friendListPanel.add(friendScrollPane, BorderLayout.CENTER);

        JPanel chatAreaPanel = new JPanel(new BorderLayout());
        chatAreaPanel.add(chatAreaLabel, BorderLayout.NORTH);
        chatAreaPanel.add(chatScrollPane, BorderLayout.CENTER);

        JPanel onlineClientListPanel = new JPanel(new BorderLayout());
        onlineClientListPanel.add(onlineClientListLabel, BorderLayout.NORTH);
        onlineClientListPanel.add(onlineClientScrollPane, BorderLayout.CENTER);

        // 底部面板
        bottomPanel = new JPanel(new BorderLayout(5, 5));
        messageField = new JTextField();
        JButton sendButton = new JButton("发送");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(sendButton);

        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        // 添加组件到窗口
        add(friendListPanel, BorderLayout.WEST);
        add(chatAreaPanel, BorderLayout.CENTER);
        add(onlineClientListPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // 添加事件处理
        sendButton.addActionListener(e -> sendChatMessage());
        messageField.addActionListener(e -> sendChatMessage());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 400);
        setLocationRelativeTo(null);

        // 关闭窗口时断开连接
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });
    }

    private void showContextMenu(Component component, int x, int y, String selectedUser) {
        JPopupMenu contextMenu = new JPopupMenu();
        if (!selectedUser.equals(username)) {
            JMenuItem addFriendItem = new JMenuItem("添加好友");
            addFriendItem.addActionListener(e -> addFriend(selectedUser));
            contextMenu.add(addFriendItem);
        }
        JMenuItem removeFriendItem = new JMenuItem("删除好友");
        removeFriendItem.addActionListener(e -> removeFriend(selectedUser));
        contextMenu.add(removeFriendItem);
        contextMenu.show(component, x, y);
    }

    private void loadFriendList() {
        DefaultListModel<String> model = (DefaultListModel<String>) friendList.getModel();
        model.clear();
        try (BufferedReader br = new BufferedReader(new FileReader("user_data/friends.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("用户：" + username + "  好友：")) {
                    String friend = line.substring(("用户：" + username + "  好友：").length());
                    model.addElement(friend);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void notifyFriendListUpdate() {
        Message updateMsg = new Message(this.username, null, "", MessageType.FRIEND_LIST_UPDATE);
        sendMessage(updateMsg);
    }

    private void loadConfig() {
        config = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("client.properties")) {
            if (input == null) {
                System.err.println("无法加载配置文件，使用默认配置");
                setDefaultConfig();
            } else {
                config.load(input);
            }
        } catch (IOException e) {
            System.err.println("无法加载配置文件，使用默认配置");
            setDefaultConfig();
        }
    }

    private void setDefaultConfig() {
        config.setProperty("serverHost", "localhost");
        config.setProperty("serverPort", "9000");
        saveConfig();
    }

    private void saveConfig() {
        try (OutputStream output = new FileOutputStream("client.properties")) {
            config.store(output, "客户端配置");
        } catch (IOException e) {
            System.err.println("无法保存配置文件: " + e.getMessage());
        }
    }

    private void showLoginDialog() {
        JDialog loginDialog = createLoginDialog();
        loginDialog.setVisible(true);
    }

    private JDialog createLoginDialog() {
        JDialog loginDialog = new JDialog(this, "登录", true);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // 用户名输入
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("用户名:"), gbc);

        JTextField usernameField = new JTextField(20);
        gbc.gridx = 1; gbc.gridy = 0;
        panel.add(usernameField, gbc);

        // 登录按钮
        JButton loginButton = new JButton("登录");
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.LINE_END;
        panel.add(loginButton, gbc);

        loginButton.addActionListener(e -> {
            String inputUsername = usernameField.getText().trim();
            if (!inputUsername.isEmpty()) {
                username = inputUsername;
                loginDialog.dispose();
                connectToServer();
            } else {
                JOptionPane.showMessageDialog(loginDialog, "请输入用户名");
            }
        });

        loginDialog.add(panel);
        loginDialog.pack();
        loginDialog.setLocationRelativeTo(this);
        loginDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        return loginDialog;
    }

    private void loadFriendsList() {
        DefaultListModel<String> model = (DefaultListModel<String>) friendList.getModel();
        model.clear();
        try (BufferedReader br = new BufferedReader(new FileReader("user_data/friends.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("用户：" + username + "  好友：")) {
                    String friend = line.split("好友：")[1].trim();
                    model.addElement(friend);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveFriendList() {
        DefaultListModel<String> model = (DefaultListModel<String>) friendList.getModel();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("user_data/friends.txt"))) {
            for (int i = 0; i < model.getSize(); i++) {
                String friend = model.getElementAt(i);
                bw.write("用户：" + username + "  好友：" + friend + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addFriend(String friendUsername) {
        DefaultListModel<String> model = (DefaultListModel<String>) friendList.getModel();
        if (!model.contains(friendUsername)) {
            model.addElement(friendUsername);
            saveFriendList();
            Message friendRequest = new Message(this.username, friendUsername, "", MessageType.FRIEND_REQUEST);
            sendMessage(friendRequest);
            notifyFriendListUpdate();
        }
    }

    private void removeFriend(String friendUsername) {
        DefaultListModel<String> model = (DefaultListModel<String>) friendList.getModel();
        model.removeElement(friendUsername);
        saveFriendList();
        Message removeFriendMsg = new Message(this.username, friendUsername, "", MessageType.REMOVE_FRIEND);
        sendMessage(removeFriendMsg);
        notifyFriendListUpdate();
    }

    private void connectToServer() {
        try {
            connect();
            sendLoginMessage();
            startMessageReceiver();
            loadFriendsList();
            setTitle("聊天客户端 - " + username);
            setVisible(true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "尝试连接失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private void connect() throws IOException {
        String host = config.getProperty("serverHost", "localhost");
        int port = Integer.parseInt(config.getProperty("serverPort", "9000"));
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    private void sendLoginMessage() {
        Message loginMsg = new Message(username, null, "", MessageType.LOGIN);
        sendMessage(loginMsg);
    }

    private void startMessageReceiver() {
        new Thread(this::receiveMessages).start();
    }
    private void receiveMessages() {
        try {
            String jsonMessage;
            while ((jsonMessage = in.readLine()) != null) {
                Message message = JsonUtil.fromJson(jsonMessage, Message.class);
                handleMessage(message);
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                JOptionPane.showMessageDialog(this, "连接断开: " + e.getMessage());
                disconnect();
            }
        }
    }

    private void disconnect() {
        saveFriendList();
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    private void handleMessage(Message message) {
        SwingUtilities.invokeLater(() -> {
            try {
                switch (message.getType()) {
                    case CHAT:
                        handleChatMessage(message);
                        break;
                    case ONLINE_USERS:
                        updateOnlineClients(message.getContent());
                        break;
                    case FRIEND_LIST:
                        updateFriendList(message.getContent());
                        break;
                    default:
                        System.err.println("未知的消息格式: " + message.getType());
                }
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "错误处理消息: " + e.getMessage());
            }
        });
    }

    private void handleChatMessage(Message message) {
        String from = message.getFrom().equals(username) ? "Me" : message.getFrom();
        String to = message.getTo().equals(username) ? "Me" : message.getTo();
        String content = message.getContent();

        if (message.getType() == MessageType.OFFLINE_MESSAGE) {
            content = "【离线消息】" + content;
        }

        String displayText = String.format("%s >> %s : %s", from, to, content);
        appendToChatArea(displayText);
    }

    private void appendToChatArea(String text) {
        chatArea.append(text + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void updateFriendList(String friendListStr) {
        DefaultListModel<String> model = (DefaultListModel<String>) friendList.getModel();
        model.clear();
        if (friendListStr != null && !friendListStr.isEmpty()) {
            String[] friends = friendListStr.split(",");
            for (String friend : friends) {
                if (!friend.trim().isEmpty()) {
                    model.addElement(friend);
                }
            }
        }
        saveFriendList();
    }


    private void updateOnlineClients(String onlineUsersStr) {
        DefaultListModel<String> model = (DefaultListModel<String>) onlineClientList.getModel();
        model.clear();
        if (onlineUsersStr != null && !onlineUsersStr.isEmpty()) {
            String[] onlineUsers = onlineUsersStr.split(",");
            for (String user : onlineUsers) {
                if (!user.trim().isEmpty()) {
                    if (user.equals(username)) {
                        model.addElement("Me");
                    } else {
                        model.addElement(user);
                    }
                }
            }
        }
    }

    private void sendMessage(Message message) {
        out.println(JsonUtil.toJson(message));
    }

    private void sendChatMessage() {
        String recipient = onlineClientList.getSelectedValue();
        if (recipient == null) {
            recipient = friendList.getSelectedValue();
        }
        String content = messageField.getText().trim();

        if (recipient == null || recipient.equals(username)) {
            JOptionPane.showMessageDialog(this, "请选择一个接收者");
            return;
        }

        if (!content.isEmpty()) {
            Message chatMsg = new Message(username, recipient, content, MessageType.CHAT);
            sendMessage(chatMsg);
            appendToChatArea("Me >> " + recipient + " : " + content);
            messageField.setText("");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ChatClient();
        });
    }
}