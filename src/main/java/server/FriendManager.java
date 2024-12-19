package server;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FriendManager {
    private final Map<String, Set<String>> friendships;

    public FriendManager() {
        this.friendships = new HashMap<>();
    }

    public boolean addFriendship(String user1, String user2) {
        return friendships.computeIfAbsent(user1, k -> new HashSet<>()).add(user2) &&
                friendships.computeIfAbsent(user2, k -> new HashSet<>()).add(user1);
    }

    public Set<String> getFriendsList(String username) {
        return friendships.getOrDefault(username, Collections.emptySet());
    }

    public void loadFriendsList(String username) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(username + ".dat"))) {
            Map<String, Object> userData = (Map<String, Object>) ois.readObject();
            friendships.put(username, new HashSet<>((List<String>) userData.get("friends")));
        } catch (IOException | ClassNotFoundException e) {
            Logger.getLogger(FriendManager.class.getName()).log(Level.SEVERE, null, e);
        }
    }
}