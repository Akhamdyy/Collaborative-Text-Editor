package com.example.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CursorPositionMessage {
    private String userId;
    private int position;
    private String color;

    @JsonCreator
    public CursorPositionMessage(@JsonProperty("userId") String userId,
                                 @JsonProperty("position") int position,
                                 @JsonProperty("color") String color) {
        this.userId = userId;
        this.position = position;
        this.color = color;
    }

    public String getUserId() {
        return userId;
    }

    public int getPosition() {
        return position;
    }

    public String getColor() {
        return color;
    }
}
