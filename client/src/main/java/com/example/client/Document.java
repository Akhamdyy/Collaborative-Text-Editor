
package com.example.client;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.Nulls;
@JsonIgnoreProperties(ignoreUnknown = true)
public class Document {
    @JsonProperty("nodes")
    private final Map<String, Node> nodes = new HashMap<>();
    @JsonProperty("rootId")
    private String rootId= null;
    @JsonProperty("undoStacks")
    private Map<String, Deque<String>> undoStacks = new HashMap<>();

    @JsonProperty("redoStacks")
    private Map<String, Deque<String>> redoStacks = new HashMap<>();
    private final List<CRDTOperation> pendingOperations = Collections.synchronizedList(new ArrayList<>());
    private long lasttimestamp = System.currentTimeMillis();

    public Document() {} // Required by Jackson

    private String generateid(String userid) {
        synchronized (this) {
            lasttimestamp = Math.max(lasttimestamp + 1, System.currentTimeMillis());
            return userid + ":" + lasttimestamp;
        }
    }

    private boolean isEffectivelyEmpty() {
        if (rootId == null) return true;
        for (Node node : nodes.values()) {
            if (!node.isDeleted()) {
                return false;
            }
        }
        return true;
    }

    public String insert(char value, String parentId, String userId) {
        if (parentId != null && !nodes.containsKey(parentId)) {
            throw new IllegalArgumentException("Parent node not found: " + parentId);
        }
        String nodeId = generateid(userId);
        Node newNode = new Node(nodeId, value, parentId);
        synchronized (this) {
            nodes.put(nodeId, newNode);
            if (rootId == null || isEffectivelyEmpty()) {
                rootId = nodeId;
                System.out.println("Set rootId to new node: " + nodeId);
            } else if (parentId == null) {
                newNode.setParentId(rootId);
                System.out.println("Set parentId to rootId: " + rootId);
            }
            printNodeDetails();
        }
        recordInsert(nodeId, userId);
        return nodeId;
    }

    public void delete(String nodeId) {
        if (!nodes.containsKey(nodeId)) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        synchronized (this) {
            nodes.get(nodeId).delete();
            if (isEffectivelyEmpty()) {
                rootId = null;
                System.out.println("All nodes deleted, reset rootId to null");
            }
        }
    }

    public void remoteInsert(String id, char value, String parentId) {
        synchronized (this) {
            if (nodes.containsKey(id)) {
                System.out.println("Skipping duplicate remote insert for id: " + id);
                return;
            }
            // Check if parent node exists or is null (for root)
            if (parentId != null && !nodes.containsKey(parentId)) {
                // Queue the operation if parent is not found
                pendingOperations.add(new CRDTOperation("insert", id, value, parentId));
                System.out.println("Queued operation for id: " + id + " due to missing parent: " + parentId);
                return;
            }
            // Perform the insert
            Node newNode = new Node(id, value, parentId);
            nodes.put(id, newNode);
            if (rootId == null || isEffectivelyEmpty()) {
                rootId = id;
                System.out.println("Set rootId to new node: " + id);
            } else if (parentId == null) {
                newNode.setParentId(rootId);
                System.out.println("Set parentId to rootId: " + rootId);
            }
            printNodeDetails();
            // Process any pending operations that can now be applied
            processPendingOperations();
        }
    }

    private void processPendingOperations() {
        List<CRDTOperation> appliedOperations = new ArrayList<>();
        for (CRDTOperation op : pendingOperations) {
            if (op.getType().equals("insert") && (op.getParentId() == null || nodes.containsKey(op.getParentId()))) {
                Node node = new Node(op.getId(), op.getValue(), op.getParentId());
                nodes.put(op.getId(), node);
                if (op.getParentId() == null && rootId == null) {
                    rootId = op.getId();
                    System.out.println("Set rootId to queued node: " + op.getId());
                }
                System.out.println("Applied queued operation for id: " + op.getId());
                printNodeDetails();
                appliedOperations.add(op);
            }
        }
        pendingOperations.removeAll(appliedOperations);

        // Recursively process pending operations in case new dependencies are resolved
        if (!appliedOperations.isEmpty()) {
            processPendingOperations();
        }
    }

    public void remoteDelete(String id) {
        synchronized (this) {
            Node node = nodes.get(id);
            if (node != null) {
                node.delete();
                if (isEffectivelyEmpty()) {
                    rootId = null;
                    System.out.println("All nodes deleted, reset rootId to null");
                }
            }
        }
    }

    public String getText() {
        System.out.println("Getting text, rootId=" + rootId);
        if (rootId == null) {
            System.out.println("rootId is null, returning empty text");
            return "";
        }
        StringBuilder documenttext = new StringBuilder();
        traverse(rootId, documenttext);
        String result = documenttext.toString();
        System.out.println("Traversed text: " + result);
        return result;
    }

    private List<Node> getchildren(String parentId) {
        List<Node> children = new ArrayList<>();
        for (Node n : nodes.values()) {
            if (parentId != null && parentId.equals(n.getParentId())) {
                children.add(n);
            }
        }
        return children;
    }

    public List<String> getVisibleNodes() {
        List<String> visibleIds = new ArrayList<>();
        for (Map.Entry<String, Node> entry : nodes.entrySet()) {
            if (!entry.getValue().isDeleted()) {
                visibleIds.add(entry.getKey());
            }
        }
        return visibleIds;
    }

    private void sortChildren(List<Node> children) {
        children.sort((a, b) -> {
            String[] aParts = a.getId().split(":");
            String[] bParts = b.getId().split(":");
            long aTime = Long.parseLong(aParts[1]);
            long bTime = Long.parseLong(bParts[1]);
            if (aTime != bTime) {
                return Long.compare(bTime, aTime);
            }
            return aParts[0].compareTo(bParts[0]);
        });
    }

    public void buildFromString(String text, String userId) {
        String parentId = null;
        for (char c : text.toCharArray()) {
            parentId = insert(c, parentId, userId);
        }
    }

    private void traverse(String nodeId, StringBuilder builder) {
        Node node = nodes.get(nodeId);
        if (node == null) {
            System.out.println("Node not found during traversal: " + nodeId);
            return;
        }

        if (!node.isDeleted()) {
            builder.append(node.getValue());
            //System.out.println("Appended node: " + nodeId + " value: " + node.getValue());
        }

        List<Node> children = getchildren(nodeId);
        sortChildren(children);

        for (Node child : children) {
            traverse(child.getId(), builder);
        }
    }

    public Map<String, Node> getNodes() {
        return new HashMap<>(nodes);
    }

    public String getRootId() {
        return rootId;
    }

    public void printNodeDetails() {
        System.out.println("=== Node Details ===");
        System.out.printf("%-20s | %-6s | %-20s | %-8s%n",
                "ID", "Value", "Parent ID", "Deleted");
        System.out.println("-----------------------------------------------------");

        nodes.values().forEach(node -> {
            System.out.printf("%-20s | %-6c | %-20s | %-8b%n",
                    node.getId(),
                    node.getValue(),
                    node.getParentId() != null ? node.getParentId() : "null",
                    node.isDeleted()
            );
        });
    }

    public void undo(String userId) {
        if (!undoStacks.containsKey(userId)) return;

        Deque<String> stack = undoStacks.get(userId);
        if (stack.isEmpty()) return;

        String lastId = stack.pop();
        Node node = nodes.get(lastId);

        if (node != null && !node.isDeleted()) {
            node.delete();
            redoStacks.computeIfAbsent(userId, k -> new LinkedList<>()).push(lastId);

            if (lastId.equals(rootId)) {
                rootId = null;
            }
        }
    }

    public void redo(String userId) {
        if (!redoStacks.containsKey(userId)) {
            redoStacks.put(userId, new LinkedList<>());
            return;
        }

        Deque<String> stack = redoStacks.get(userId);
        if (stack.isEmpty()) return;

        String id = stack.pop();
        Node node = nodes.get(id);

        if (node != null && node.isDeleted()) {
            node.restore();

            if (node.getLastParentIdBeforeDeletion() != null) {
                if (!nodes.containsKey(node.getLastParentIdBeforeDeletion())) {
                    node.setParentId(rootId != null ? rootId : null);
                }
            }

            undoStacks.computeIfAbsent(userId, k -> new LinkedList<>()).push(id);

            System.out.println("Redo restored node: " + id);
            printNodeDetails();
        }
    }

    public String getLastNodeId() {
        if (rootId == null) return null;

        String currentId = rootId;
        while (true) {
            List<Node> children = getchildren(currentId);
            sortChildren(children);
            if (children.isEmpty()) {
                break;
            }
            Node lastChild = null;
            for (int i = children.size() - 1; i >= 0; i--) {
                Node child = children.get(i);
                if (!child.isDeleted()) {
                    lastChild = child;
                    break;
                }
            }
            if (lastChild == null) {
                break;
            }
            currentId = lastChild.getId();
        }
        return nodes.get(currentId).isDeleted() ? null : currentId;
    }

    public void recordInsert(String id, String userId) {
        undoStacks.computeIfAbsent(userId, k -> new LinkedList<>()).push(id);
        redoStacks.computeIfAbsent(userId, k -> new LinkedList<>()).clear();
    }

    public String getNodeIdAtPosition(int position) {
        if (rootId == null) return null;

        int[] currentPosition = {0};
        String[] nodeIdAtPosition = {null};
        traverseForPosition(rootId, position, currentPosition, nodeIdAtPosition);

        return nodeIdAtPosition[0];
    }

    public int getPositionOfNode(String nodeId) {
        if (nodeId == null || !nodes.containsKey(nodeId) || rootId == null) {
            return -1;
        }

        int[] currentPosition = {0};
        boolean[] found = {false};
        traverseForPositionOfNode(rootId, nodeId, currentPosition, found);

        return found[0] ? currentPosition[0] : -1;
    }

    private void traverseForPositionOfNode(String currentNodeId, String targetNodeId, int[] currentPosition, boolean[] found) {
        if (found[0]) return;

        Node node = nodes.get(currentNodeId);
        if (node == null) return;

        if (currentNodeId.equals(targetNodeId) && !node.isDeleted()) {
            found[0] = true;
            return;
        }

        if (!node.isDeleted()) {
            currentPosition[0]++;
        }

        List<Node> children = getchildren(currentNodeId);
        sortChildren(children);
        for (Node child : children) {
            traverseForPositionOfNode(child.getId(), targetNodeId, currentPosition, found);
        }
    }

    private void traverseForPosition(String nodeId, int targetPosition, int[] currentPosition, String[] nodeIdAtPosition) {
        if (nodeIdAtPosition[0] != null) return;

        Node node = nodes.get(nodeId);
        if (node == null) return;

        if (!node.isDeleted()) {
            if (currentPosition[0] == targetPosition) {
                nodeIdAtPosition[0] = node.getId();
                return;
            }
            currentPosition[0]++;
        }

        List<Node> children = getchildren(nodeId);
        sortChildren(children);
        for (Node child : children) {
            traverseForPosition(child.getId(), targetPosition, currentPosition, nodeIdAtPosition);
        }
    }
}

