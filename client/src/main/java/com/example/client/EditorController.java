package com.example.client;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.core.io.FileSystemResource;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class EditorController {
    @FXML public Label edit_label;
    @FXML public Label view_label;
    @FXML private Label titleLabel;
    @FXML private TextField editorCodeField;
    @FXML private TextField viewerCodeField;
    @FXML private Button copyEditorButton;
    @FXML private Button copyViewerButton;
    @FXML private Button undoButton;
    @FXML private Button redoButton;
    @FXML private Button endButton;
    @FXML private ListView<String> userList;
    @FXML private ListView<String> commentsList;
    @FXML private TextArea textArea;
    @FXML private VBox lineNumbers;
    @FXML private ScrollPane lineScrollPane;
    @FXML private ScrollPane textScrollPane;
    @FXML private Button commentbutton;
    @FXML private Pane cursorLayer;
    @FXML private Label copyFeedbackLabel;

    private List<String> user_list = new ArrayList<>();
    private DocumentWebsockethandler websockethandler;
    private Userlistwebsockethandler userlistwebsockethandler;
    private Commentwebsockethandler commentsHandler;
    private CursorWebSocketHandler cursorWebSocketHandler;
    private Document document;
    private String UserID;
    private String roomCode;
    private Stack<String> undoStack = new Stack<>();
    private Stack<String> redoStack = new Stack<>();
    private boolean isProcessingChange = false;
    private Set<String> sentOperationIds = new HashSet<>();
    private Set<String> deletedids = new HashSet<>();
    private boolean Remoteupdate = false;
    private boolean newdoc = false;
    private List<Comment> comments = new ArrayList<>();
    private HttpHelper helper;
    private final Map<String, Integer> commentPositions = new HashMap<>();
    private int lastSentPosition = -1;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingUpdate = null;
    private static final long CURSOR_UPDATE_DELAY_MS = 100;
    private final Map<String, RemoteCursor> remoteCursors = new HashMap<>();
    private static final String[] USER_COLORS = {
            "#FF0000", "#00FF00", "#0000FF",
            "#FF00FF", "#FFFF00", "#00FFFF"
    };

    @FXML
    public void initialize() {
        titleLabel.setText("Text Editor");
        document = new Document();
        websockethandler = new DocumentWebsockethandler();
        websockethandler.setMessageHandler(this::handleServerOperation);
        userlistwebsockethandler = new Userlistwebsockethandler();
        userlistwebsockethandler.setUsershandler(this::handleuserlist);
        commentsHandler = new Commentwebsockethandler();
        commentsHandler.setCommentsHandler(this::updateComments);
        commentsHandler.setCommentRemovalHandler(this::removeComment);
        cursorWebSocketHandler = new CursorWebSocketHandler();
        cursorWebSocketHandler.setCursorHandler(this::handlecursors);
        helper = new HttpHelper();

        // Debounce text change listener
        textArea.textProperty().addListener((obs, oldValue, newValue) -> {
            if (Remoteupdate) {
                return;
            }
            if (!isProcessingChange) {
                isProcessingChange = true;
                handleLocalTextChange(oldValue, newValue);
                isProcessingChange = false;
            }
        });

        // Comment deletion and highlight listener
        textArea.textProperty().addListener((obs, oldText, newText) -> {
            List<Comment> toRemove = comments.stream()
                    .filter(c -> c.isTextDeleted(newText))
                    .collect(Collectors.toList());
            toRemove.forEach(c -> removeComment(c.getId()));
            Platform.runLater(() -> {
                updateCommentPositions(oldText, newText);
                applyCommentHighlights(textArea);
                updateCommentList();
            });
        });

        // Sync scrolling of line numbers with text area
        textScrollPane.vvalueProperty().addListener((obs, oldValue, newValue) -> {
            lineScrollPane.setVvalue(newValue.doubleValue());
        });

        // Track local cursor position
        textArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            int newPosInt = (Integer) newPos;
            if (pendingUpdate != null && !pendingUpdate.isDone()) {
                pendingUpdate.cancel(false);
            }
            pendingUpdate = scheduler.schedule(() -> {
                if (newPosInt != lastSentPosition) {
                    cursorWebSocketHandler.sendCursorPosition(roomCode, UserID, newPosInt, getColorForUser(UserID));
                    lastSentPosition = newPosInt;
                }
            }, CURSOR_UPDATE_DELAY_MS, TimeUnit.MILLISECONDS);
        });

        // Initialize line numbers
        updateLineNumbers();

        // Initialize buttons and fields
        editorCodeField.setText(generateEditorCode());
        viewerCodeField.setText(generateViewerCode());
        updateUndoRedoButtons();
    }

    public void initializeWithUsername(String username, String editor, String viewer, boolean edit,String SERVER_IP) throws IOException, InterruptedException {
        if (username != null && !username.trim().isEmpty()) {
            userList.getItems().add(username);
        }
        this.viewerCodeField.setText(viewer);
        this.editorCodeField.setText(editor);
        user_list.add(username);

        if (!edit) {
            editorCodeField.setVisible(false);
            viewerCodeField.setVisible(false);
            textArea.setEditable(false);
            copyEditorButton.setVisible(false);
            copyViewerButton.setVisible(false);
            edit_label.setVisible(false);
            view_label.setVisible(false);
        }
        this.UserID = username;
        roomCode = editor;
        if (roomCode == null || roomCode.trim().isEmpty()) {
            showAlert("Invalid Room Code", "Room code is not set.");
            return;
        }
        websockethandler.IP = SERVER_IP;
        userlistwebsockethandler.IP = SERVER_IP;
        commentsHandler.IP = SERVER_IP;
        cursorWebSocketHandler.IP=SERVER_IP;

        // Connect to WebSocket
        if (websockethandler.connectToWebSocket()) {
            websockethandler.subscribeToRoom(roomCode);
        } else {
            showAlert("Connection Failed", "Couldn't connect to WebSocket server");
        }

        if (userlistwebsockethandler.connectToWebSocket()) {
            userlistwebsockethandler.subscribeToRoom(roomCode);
        } else {
            showAlert("Connection Failed", "Couldn't connect to WebSocket server");
        }

        if (commentsHandler.connectToWebSocket()) {
            commentsHandler.subscribeToRoom(roomCode);
        } else {
            showAlert("Connection Failed", "Couldn't connect to WebSocket server for comments");
        }

        if (cursorWebSocketHandler.connectToWebSocket()) {
            cursorWebSocketHandler.subscribeToCursorUpdates(roomCode);
        } else {
            showAlert("connection failed", "can't connect to websocket server");
        }

        // Fetch the document from server
        Document doc2 = helper.getDocumentFromCode(roomCode);
        System.out.println("doc received: " + (doc2 != null ? doc2.getText() : "null"));

        if (doc2 != null) {
            this.document = doc2;
            Platform.runLater(() -> {
                String text = document.getText();
                newdoc = true;
                textArea.setText(text);
                newdoc = false;
                updateLineNumbers();
                System.out.println("Document loaded with text: " + text);

                System.out.println("Document nodes:");
                document.getNodes().forEach((id, node) -> {
                    System.out.println("ID: " + id +
                            ", Value: " + node.getValue() +
                            ", Parent: " + node.getParentId() +
                            ", Deleted: " + node.isDeleted());
                });
            });
        } else {
            System.out.println("Failed to fetch document from server");
        }

        userlistwebsockethandler.join(roomCode, username);
    }

    private void handleLocalTextChange(String oldValue, String newValue) {
        if(!newdoc) {
            if (oldValue == null || newValue == null || oldValue.equals(newValue)) return;


            int cursorPosition = textArea.getCaretPosition();
            System.out.println("oldValue: '" + oldValue + "', newValue: '" + newValue + "', cursorPosition: " + cursorPosition);

            if (newValue.length() > oldValue.length()) { // Character inserted
                if (cursorPosition >= 0 && cursorPosition <= newValue.length()) {
                    char insertedChar = 0;
                    int insertIndex = cursorPosition > 0 ? cursorPosition - 1 : 0; // get position of the inserted character
                    if (newValue.length() == oldValue.length() + 1) {
                        // Single character insertion
                        if (insertIndex < oldValue.length() && oldValue.charAt(insertIndex) == newValue.charAt(insertIndex)) {
                            insertedChar = cursorPosition < newValue.length() ? newValue.charAt(cursorPosition) : newValue.charAt(newValue.length() - 1);
                        } else {
                            insertedChar = newValue.charAt(insertIndex);
                        }
                    } else {
                        // here we will handle lama text yeb2a pasted. // multiple character insertion at once.
                        // Multiple character insertion = paste
                        int insertedLength = newValue.length() - oldValue.length();
                        int startIndex = insertIndex;

// Extract inserted substring
                        String insertedText = newValue.substring(startIndex, startIndex + insertedLength);
                        System.out.println("Pasted text: '" + insertedText + "'");

// Get initial parentId (before the paste position)
                        String parentId = null;
                        if (startIndex > 0) {
                            parentId = document.getNodeIdAtPosition(startIndex - 1);
                        }
                        if (parentId == null) {
                            parentId = document.getLastNodeId();
                        }

                        for (int i = 0; i < insertedText.length(); i++) {
                            char c = insertedText.charAt(i);
                            String nodeId = document.insert(c, parentId, UserID);
                            System.out.println("Inserted char from paste: " + c + ", nodeId: " + nodeId + ", parentId: " + parentId);

                            if (nodeId != null && sentOperationIds.add(nodeId)) {
                                websockethandler.sendInsert(roomCode, nodeId, c, parentId);
                            }

                            // Set parentId for the next character to be this node
                            parentId = nodeId;
                        }
                    }

                    // Determine parentId
                    String parentId = null;
                    if (cursorPosition > 0) {
                        parentId = document.getNodeIdAtPosition(cursorPosition - 1);
                    }
                    if (parentId == null) {
                        parentId = document.getLastNodeId();
                    }

                    System.out.println("Inserting char: " + insertedChar + ", parentId: " + parentId);
                    String nodeId = document.insert(insertedChar, parentId, UserID); // Insert in the local document
                    // String nodeId = document.getNodes().keySet().stream().max(String::compareTo).orElse(null);
                    System.out.println("Document text after insert: " + document.getText());
                    //textArea.positionCaret(insertIndex)
                    if (nodeId != null && sentOperationIds.add(nodeId)) {
                        System.out.println("Sent insert op: " + insertedChar + ", nodeId: " + nodeId);
                        websockethandler.sendInsert(roomCode, nodeId, insertedChar, parentId);
                    } else {
                        System.out.println("Failed to get nodeId or operation already sent for insertion: " + nodeId);
                    }
                } else {
                    System.out.println("Invalid cursor position for insertion: " + cursorPosition);
                }
            } else if (newValue.length() < oldValue.length()) { // Character deleted
                // Adjust cursor position and check if there's text to delete
                int deletedPosition = cursorPosition - 1;

                if (deletedPosition < 0) {
                    System.out.println("Nothing to delete (cursor at start)");
                    return;
                }

                System.out.println("Attempting to delete at position: " + deletedPosition);

                // Find the correct nodeId corresponding to the deleted character
                String nodeId = document.getNodeIdAtPosition(deletedPosition);

                if (nodeId != null) {
                    //sentOperationIds.remove(nodeId);
                    // Check if operation for this nodeId has already been sent

                    if (deletedids.add(nodeId)) {
                        System.out.println("Sent delete op for nodeId: " + nodeId);
                        document.delete(nodeId);  // Actually delete the node in the document
                        websockethandler.sendDelete(roomCode, nodeId);

                        // After deletion, update the document text
                        String updatedText = document.getText();
                        System.out.println("Updated document text after delete: '" + updatedText + "'");

                        // Update the TextArea with the updated text after deletion
                        textArea.setText(updatedText);
                    } else {
                        System.out.println("Operation already sent for nodeId: " + nodeId);
                    }
                } else {
                    System.out.println("No nodeId found at position: " + deletedPosition);
                }
            }
        }


    }
    private void handleServerOperation(CRDTOperation op) {
        Platform.runLater(() -> {
            // Skip if operation ID was sent by this client
            if (sentOperationIds.contains(op)) {
                System.out.println("Ignoring sent operation: " + op.getType() + ", id: " + op.getId());
                return;
            }
            int old = textArea.getCaretPosition();
            System.out.println("Processing remote op: " + op.getType() + ", id: " + op.getId() + ", value: " + op.getValue());
            if ("insert".equals(op.getType())) {
                document.remoteInsert(op.getId(), op.getValue(), op.getParentId());
            } else if ("delete".equals(op.getType())) {
                System.out.println("applying remote delete for id: " + op.getId());
                document.remoteDelete(op.getId());
                if (document.getNodes().get(op.getId()) == null) {
                    System.out.println("node not found for remote delete: " + op.getId());
                }
            }
            String documentText = document.getText();
            System.out.println("Remote op applied, document text: " + documentText);
            Remoteupdate = true;
            textArea.setText(documentText);
            textArea.positionCaret(Math.min(old, documentText.length()));
            Remoteupdate = false;
            updateLineNumbers();
        });
    }
    private void handleuserlist(List<String> usernames) {
        System.out.println("received userlist");
        for (String user : usernames) {
            System.out.println(user);
        }
        Platform.runLater(() -> {
            userList.getItems().setAll(usernames);
        });
    }

    private void handlecursors(String userId, Integer position, String color) {
        System.out.println("Received cursor from: " + userId + " with position " + position + " and color: " + color);
        Platform.runLater(() -> {
            if (userId.equals(UserID)) return;
            if(position == null){
                removeRemoteCursor(userId);
            }else{
                String username = userList.getItems().stream()
                        .filter(u -> u.equals(userId))
                        .findFirst()
                        .orElse(userId);
                updateRemoteCursor(userId, username, position, color);
            }
 });
}
    private void updateRemoteCursor(String userId, String username, int position, String color) {
        if (cursorLayer == null) {
            System.err.println("Cursor layer not initialized!");
            return;
        }
        try {
            RemoteCursor cursor = remoteCursors.computeIfAbsent(userId, id ->
                    new RemoteCursor(username, color != null ? color : getColorForUser(id)));
            Point2D pos = calculateCursorPosition(position);
            cursor.updatePosition(pos.getX(), pos.getY());
        } catch (Exception e) {
            System.err.println("Error updating cursor: " + e.getMessage());
        }
    }

    private Point2D calculateCursorPosition(int caretPosition) {
        String text = textArea.getText().substring(0, caretPosition);
        String[] lines = text.split("\n", -1);
        int lineNumber = lines.length - 1;
        String lastLine = lines[lineNumber];
        Text helper = new Text(lastLine);
        helper.setFont(textArea.getFont());
        double lineHeight = textArea.getFont().getSize() * 1.2;
        double yPos = lineHeight * (lineNumber + 0.5) + 5;
        double xPos = helper.getLayoutBounds().getWidth() + 10;
        return new Point2D(xPos, yPos);
    }

    private String getColorForUser(String userId) {
        int hash = userId.hashCode();
        return USER_COLORS[Math.abs(hash) % USER_COLORS.length];
    }

    private class RemoteCursor {
        private final Line line;
        private final Text label;
        private final String color;

        public RemoteCursor(String username, String color) {
            this.color = color;
            this.line = new Line(0, 0, 0, 20);
            this.line.setStroke(Color.web(color));
            this.line.setStrokeWidth(2);
            this.label = new Text(username);
            this.label.setStyle("-fx-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
            this.label.setTranslateY(-15);
            Group cursorGroup = new Group(line, label);
            cursorLayer.getChildren().add(cursorGroup);
        }

        public void updatePosition(double x, double y) {
            line.setStartX(x);
            line.setStartY(y);
            line.setEndX(x);
            line.setEndY(y + 20);
            label.setTranslateX(x - (label.getLayoutBounds().getWidth() / 2));
            label.setTranslateY(y - 15);
        }
    }

    private void updateCommentPositions(String oldText, String newText) {
        int diff = newText.length() - oldText.length();
        if (diff == 0) return;
        int changePos = textArea.getCaretPosition();
        for (Comment comment : comments) {
            if (changePos <= comment.getStartPos()) {
                comment.setStartPos(comment.getStartPos() + diff);
                comment.setEndPos(comment.getEndPos() + diff);
            } else if (changePos < comment.getEndPos()) {
                comment.setEndPos(comment.getEndPos() + diff);
            }
        }
    }

    private void updateCommentList() {
        commentsList.getItems().clear();
        for (Comment comment : comments) {
            HBox entry = new HBox(5);
            Label authorLabel = new Label(comment.getAuthor() + ":");
            authorLabel.setStyle("-fx-text-fill: " + comment.getColor());
            Button jumpBtn = new Button("→");
            jumpBtn.setOnAction(e -> {
                textArea.positionCaret(comment.getStartPos());
                textArea.selectRange(comment.getStartPos(), comment.getEndPos());
            });
            entry.getChildren().addAll(jumpBtn, authorLabel);
            commentsList.getItems().add(entry.toString());
        }
    }

    public void applyCommentHighlights(TextArea textArea) {
        String text = textArea.getText();
        textArea.setStyle("-fx-font-family: 'Consolas';");
        textArea.setStyle("");
        if (text.contains("//")) {
            textArea.setStyle("-fx-font-family: 'Consolas'; -fx-fill: gray;");
        }
    }

    private void handleCommentDeletion(int deleteStart, int deleteEnd, int deletedLength) {
        List<Comment> commentsToRemove = new ArrayList<>();
        List<Comment> commentsToUpdate = new ArrayList<>();
        for (Comment comment : comments) {
            if (comment.isContainedInDeletion(deleteStart, deleteEnd)) {
                commentsToRemove.add(comment);
            } else if (comment.overlapsWithDeletion(deleteStart, deleteEnd)) {
                commentsToRemove.add(comment);
            } else if (comment.getStartPos() > deleteStart) {
                comment.setStartPos(comment.getStartPos() - deletedLength);
                comment.setEndPos(comment.getEndPos() - deletedLength);
                commentsToUpdate.add(comment);
            }
        }
        comments.removeAll(commentsToRemove);
        for (Comment comment : commentsToRemove) {
            commentsHandler.removeComment(roomCode, comment.getId());
        }
        for (Comment comment : commentsToUpdate) {
            commentsHandler.updateComment(roomCode, comment);
        }
        updateCommentDisplay();
        applyCommentHighlights(textArea);
    }

    @FXML
    private void handleImport(ActionEvent event) throws IOException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to import");
        Window window = ((Node) event.getSource()).getScene().getWindow();
        File selectedfile = fileChooser.showOpenDialog(window);
        if (selectedfile != null) {
            String filepath = selectedfile.getAbsolutePath();
            String text = Files.readString(Paths.get(filepath));
            textArea.setText(text);
        } else {
            System.out.println("no file is selected!");
        }
    }

    @FXML
    private void handleExport(ActionEvent event) throws IOException {
        String content = textArea.getText();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save as ....");
        Window window = ((Node) event.getSource()).getScene().getWindow();
        File fileselected = fileChooser.showSaveDialog(window);
        if (fileselected != null) {
            String filepath = fileselected.getAbsolutePath();
            ExportFile(content, filepath);
            showSuccess("File Saved", "File Exported Successfully");
        }
    }

    public void ExportFile(String content, String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!filePath.toLowerCase().endsWith(".txt")) {
            path = path.resolveSibling(path.getFileName() + ".txt");
        }
        File file = path.toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    @FXML
    public void copyEditorCode() {
        if (editorCodeField != null) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(editorCodeField.getText());
            clipboard.setContent(content);
            showCopyFeedback("Editor code copied!");
        }
    }

    @FXML
    public void copyViewerCode() {
        if (viewerCodeField != null) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(viewerCodeField.getText());
            clipboard.setContent(content);
            showCopyFeedback("Viewer code copied!");
        }
    }

    @FXML
    private void undo(ActionEvent event) {
        document.undo(UserID);
        textArea.setText(document.getText());
        updateUndoRedoButtons();
    }
    @FXML
    private void showCopyFeedback(String message) {
        if (copyFeedbackLabel != null) {
            copyFeedbackLabel.setText(message);
            copyFeedbackLabel.setVisible(true);
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(() -> copyFeedbackLabel.setVisible(false));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
    @FXML
    private void redo(ActionEvent event) {
        document.redo(UserID);
        textArea.setText(document.getText());
        updateUndoRedoButtons();
    }

    @FXML
    private void endSession(ActionEvent event) throws IOException {
        textArea.clear();
        userList.getItems().clear();
        commentsList.getItems().clear();
        removeRemoteCursor(UserID);
        userlistwebsockethandler.leave(roomCode, UserID);
        userlistwebsockethandler.disconnect();
        websockethandler.disconnect();
        commentsHandler.disconnect();
        cursorWebSocketHandler.disconnect(roomCode,UserID);
        updateUndoRedoButtons();
        Node source = (Node) event.getSource();
        Stage currentStage = (Stage) source.getScene().getWindow();
        currentStage.close();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/client/welcome.fxml"));
        Parent root = loader.load();
        Stage welcomeStage = new Stage();
        welcomeStage.setTitle("Welcome");
        welcomeStage.setScene(new Scene(root, 800, 600));
        welcomeStage.show();
        }
    private void removeRemoteCursor(String userId) {
        RemoteCursor cursor = remoteCursors.remove(userId);
        if (cursor != null) {
            cursorLayer.getChildren().removeIf(node -> {
                if (node instanceof Group) {
                    Group group = (Group) node;
                    return group.getChildren().contains(cursor.line) || group.getChildren().contains(cursor.label);
                }
                return false;
            });
            System.out.println("Removed cursor for user: " + userId);
}
}


    @FXML
    private void handleRemovecomment() {
        int selectedIndex = commentsList.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < comments.size()) {
            Comment selectedComment = comments.get(selectedIndex);
            commentsHandler.removeComment(roomCode, selectedComment.getId());
        }
    }

    @FXML
    private void handleAddcomment() {
        int startPos = textArea.getSelection().getStart();
        int endPos = textArea.getSelection().getEnd();
        if (startPos == endPos) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText(null);
            alert.setContentText("Please select some text to comment on.");
            alert.showAndWait();
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Comment");
        dialog.setHeaderText("Enter your comment");
        dialog.setContentText("Comment:");
        dialog.showAndWait().ifPresent(commentText -> {
            if (commentText != null && !commentText.trim().isEmpty()) {
                String commentId = UUID.randomUUID().toString();
                String author = UserID;
                Comment comment = new Comment(commentId, startPos, endPos, commentText, author);
                commentsHandler.addComment(roomCode, comment);
            }
        });
    }

    private void updateComments(List<Comment> updatedComments) {
        Platform.runLater(() -> {
            this.comments = updatedComments != null ? updatedComments : new ArrayList<>();
            updateCommentDisplay();
        });
    }

    private void removeComment(String commentId) {
        Platform.runLater(() -> {
            System.out.println("removing comment EC");
            comments.removeIf(c -> c.getId().equals(commentId));
            updateCommentDisplay();
        });
    }

    private void updateCommentDisplay() {
        commentsList.getItems().clear();
        for (Comment comment : comments) {
            String displayText = String.format("%s: %s (Range: %d-%d)",
                    comment.getAuthor(), comment.getText(), comment.getStartPos(), comment.getEndPos());
            commentsList.getItems().add(displayText);
        }
    }

    private void updateLineNumbers() {
        lineNumbers.getChildren().clear();
        int lineCount = textArea.getText().split("\n").length;
        for (int i = 1; i <= lineCount; i++) {
            Label lineLabel = new Label(String.valueOf(i));
            lineLabel.getStyleClass().add("line-number-label");
            lineNumbers.getChildren().add(lineLabel);
        }
    }

    private void updateUndoRedoButtons() {
        undoButton.setDisable(false);
        redoButton.setDisable(false);
    }

    private String generateEditorCode() {
        return "EDT-" + System.currentTimeMillis();
    }

    private String generateViewerCode() {
        return "VWR-" + System.currentTimeMillis();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}