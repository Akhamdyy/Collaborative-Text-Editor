package com.example.client;

public class CRDTOperation {
    private String type;
    private String id;
    private char value; // Use char for single-character values
    private String parentId;

    // Default constructor for Jackson
    public CRDTOperation() {}

    public CRDTOperation(String type, String id, char value, String parentId) {
        this.type = type;
        this.id = id;
        this.value = value;
        this.parentId = parentId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public char getValue() {
        return value;
    }

    public void setValue(char value) {
        this.value = value;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
}