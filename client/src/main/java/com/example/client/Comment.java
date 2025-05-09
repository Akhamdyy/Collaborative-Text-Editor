package com.example.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Comment {
    private String id;
    private int startPos;
    private int endPos;
    private String text;
    private String author;
    private String color;

    public Comment() {}

    public Comment(
            @JsonProperty("id") String id,
            @JsonProperty("startPos") int startPos,
            @JsonProperty("endPos") int endPos,
            @JsonProperty("text") String text,
            @JsonProperty("author") String author) {
        this.id = id;
        this.startPos = startPos;
        this.endPos = endPos;
        this.text = text;
        this.author = author;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getStartPos() { return startPos; }
    public void setStartPos(int startPos) { this.startPos = startPos; }
    public int getEndPos() { return endPos; }
    public void setEndPos(int endPos) { this.endPos = endPos; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isTextDeleted(String currentText) {
        if (startPos >= currentText.length() || endPos > currentText.length()) {
            return true;
        }
        return false;
    }
    public boolean isContainedInDeletion(int deleteStart, int deleteEnd) {
        return this.startPos >= deleteStart && this.endPos <= deleteEnd;
    }
    public boolean overlapsWithDeletion(int deleteStart, int deleteEnd) {
        return this.startPos < deleteEnd && this.endPos > deleteStart;
}

}
