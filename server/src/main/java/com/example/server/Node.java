package com.example.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Node { //represents a single character crdt element

    private String id;          // Unique ID format: "userID:timestamp" (e.g., "User1:123456789")

    private char value;         // The actual character (e.g., 'A')

    private String parentId;    // ID of the preceding character (null for the first character)

    private boolean isDeleted;  // Tombstone flag (true if deleted)

    private String lastParentIdBeforeDeletion; // Track parent before deletion

    public Node() {} // Required by Jackson


    public Node(String id, char value, String parentId) {
        this.id = id;
        this.value = value;
        this.parentId = parentId;
        this.isDeleted = false;
    }
    public String getId() { return id; }
    public char getValue() { return value; }
    public String getParentId() { return parentId; }
    public boolean isDeleted() { return isDeleted; }
    public   String getLastParentIdBeforeDeletion()
    {return  lastParentIdBeforeDeletion;}




    public void delete() {
        this.lastParentIdBeforeDeletion=this.parentId;
        this.isDeleted = true;
    }
    public void restore()
    {
        this.isDeleted=false;
    }

    public void setParentId(String rootId) {
        this.parentId=rootId;
    }
}