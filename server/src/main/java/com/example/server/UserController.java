
package com.example.server;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class UserController {
    public DocumentService service;

    public UserController(DocumentService Service) {
        this.service = Service;
    }

    @MessageMapping("/users/{roomId}")
    @SendTo("/topic/users/{roomId}")
    public List<String> handleRoomMessage(@DestinationVariable String roomId, String username) {
        //service.joinSession(roomId,username);
        List<String> usernames= service.getusers(roomId);
        for(String user:usernames )
        {
            System.out.println(user);
        }
        return service.getusers(roomId);

    }
    @MessageMapping("/users/remove/{roomId}")
    @SendTo("/topic/users/{roomId}")
    public List<String> leaveroom(@DestinationVariable String roomId, String username) {
        //service.joinSession(roomId,username);
        String edit= service.geteditorcode(roomId);
        roomId=edit;
        //System.out.println(edit);
        List<String> usernames= service.getusers(roomId);
        for(String user: usernames)
        {
            System.out.println(user);
        }
        service.removeuser(roomId,username);
        System.out.println("after removal: ");
        for(String user:usernames )
        {
            System.out.println(user);
        }
        return service.getusers(roomId);

    }
}


