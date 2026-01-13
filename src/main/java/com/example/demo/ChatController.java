package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Collections;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private ChatMessageRepository messageRepository;

    @GetMapping
    public List<ChatMessage> getRecentMessages() {
        List<ChatMessage> messages = messageRepository.findTop50ByOrderByCreatedAtDesc();
        Collections.reverse(messages);
        return messages;
    }
}
