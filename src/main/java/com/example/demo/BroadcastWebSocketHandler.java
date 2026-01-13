package com.example.demo;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.CloseStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BroadcastWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final Map<String, String> sessionUsernames = new ConcurrentHashMap<>();
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BroadcastWebSocketHandler(ChatMessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        String username = sessionUsernames.remove(session.getId());

        if (username != null) {
            // Save leave notification to database
            ChatMessage leaveMsg = new ChatMessage(username, username + " left the chat", "LEAVE");
            messageRepository.save(leaveMsg);

            // Broadcast leave notification
            Map<String, Object> leaveNotification = Map.of(
                    "type", "LEAVE",
                    "username", username,
                    "content", username + " left the chat",
                    "timestamp", leaveMsg.getCreatedAt().toString());
            broadcastMessage(objectMapper.writeValueAsString(leaveNotification));

            // Broadcast updated user list
            broadcastUserList();
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);

            String type = (String) data.get("type");
            String username = (String) data.get("username");
            String content = (String) data.get("content");

            if ("JOIN".equals(type)) {
                // Store username for this session
                sessionUsernames.put(session.getId(), username);

                // Save join notification to database
                ChatMessage joinMsg = new ChatMessage(username, username + " joined the chat", "JOIN");
                messageRepository.save(joinMsg);

                // Broadcast join notification
                Map<String, Object> joinNotification = Map.of(
                        "type", "JOIN",
                        "username", username,
                        "content", username + " joined the chat",
                        "timestamp", joinMsg.getCreatedAt().toString());
                broadcastMessage(objectMapper.writeValueAsString(joinNotification));

                // Broadcast updated user list
                broadcastUserList();

            } else if ("CHAT".equals(type)) {
                // Save chat message to database
                ChatMessage chatMsg = new ChatMessage(username, content, "CHAT");
                messageRepository.save(chatMsg);

                // Broadcast chat message
                Map<String, Object> chatNotification = Map.of(
                        "type", "CHAT",
                        "username", username,
                        "content", content,
                        "timestamp", chatMsg.getCreatedAt().toString());
                broadcastMessage(objectMapper.writeValueAsString(chatNotification));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcastMessage(String message) {
        TextMessage textMessage = new TextMessage(message);
        sessions.stream()
                .filter(WebSocketSession::isOpen)
                .forEach(s -> {
                    try {
                        s.sendMessage(textMessage);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    private void broadcastUserList() {
        try {
            Map<String, Object> userListMsg = Map.of(
                    "type", "USER_LIST",
                    "users", sessionUsernames.values());
            broadcastMessage(objectMapper.writeValueAsString(userListMsg));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
