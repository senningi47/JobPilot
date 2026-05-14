package com.jobpilot.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatSendRequest {

    @NotBlank
    @Size(max = 5000)
    private String content;

    public ChatSendRequest() {
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
