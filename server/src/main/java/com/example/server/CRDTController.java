package com.example.server;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class CRDTController {
    private final DocumentService service;

    public CRDTController(DocumentService service) {
        this.service = service;
    }

    @MessageMapping("/room/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public CRDTOperation handleRoomMessage(@DestinationVariable String roomId, CRDTOperation op) {
        // Step 1: Validate room code
        if (!service.isValidCode(roomId)) {
            System.out.println("Invalid room ID: " + roomId);
            return null;
        }

        // Step 2: Get user's role (editor/viewer)
        String role = service.getRole(roomId);
        if ("viewer".equals(role) && !"cursor".equals(op.getType())) {
            System.out.println("Viewer tried to perform a write operation.");
            return null;
        }

        // Step 3: Get the corresponding document
        Document doc = service.getDocumentFromCode(roomId);

        // Step 4: Apply the operation
        try {
            switch (op.getType()) {
                case "insert":
                    doc.remoteInsert(op.getId(), op.getValue(), op.getParentId());
                    System.out.println("Applied insert operation, document text: " + doc.getText());
                    break;
                case "delete":
                    doc.remoteDelete(op.getId());
                    System.out.println("Applied delete operation, document text: " + doc.getText());
                    break;
                case "cursor":
                    // Cursor operations don't modify the document, just broadcast
                    break;
                default:
                    System.out.println("Unknown operation type: " + op.getType());
                    return null;
            }
        } catch (Exception e) {
            System.out.println("Error processing operation: " + op.getType() + ", id: " + op.getId() + ", error: " + e.getMessage());
            return null;
        }

        // Step 5: Return the op to broadcast to all users in the room
        return op;
    }
}