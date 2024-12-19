package server;

import common.JsonUtil;
import common.Message;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StorageManager {

    private static final String BASE_DIR = "user_data";
    private static final Map<String, Set<String>> userFilesCache = new ConcurrentHashMap<>();

    public static void initialize() {
        File baseDir = new File(BASE_DIR);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    public static void createUserFile(String username) {
        File userFile = new File(BASE_DIR, username + ".dat");
        if (!userFile.exists()) {
            try {
                userFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void saveChatHistory(String username, List<Message> chatHistory) throws IOException {
        String json = JsonUtil.toJson(chatHistory);
        File chatHistoryFile = new File(BASE_DIR, username + "_history.dat");
        if (isFileAlreadySaved(username, chatHistoryFile.getName())) {
            return;
        }
        Files.write(chatHistoryFile.toPath(), json.getBytes(), StandardOpenOption.APPEND);
        updateUserFilesCache(username, chatHistoryFile.getName());
    }

    private static boolean isFileAlreadySaved(String username, String fileName) {
        return userFilesCache.getOrDefault(username, Collections.emptySet()).contains(fileName);
    }

    private static void updateUserFilesCache(String username, String fileName) {
        userFilesCache.computeIfAbsent(username, k -> new HashSet<>()).add(fileName);
    }
}