package com.example.server;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class DocumentRestController {

    public DocumentService documentService;
    public DocumentRestController(DocumentService documentService) {this.documentService = documentService;}

    @PostMapping("/create")
    public Map<String,String > create(@RequestBody String username) {
        return documentService.createSession(username);
    }
    @PostMapping("/join")
    public Map<String,String > join(@RequestBody String params) {
        String code = params.split(",")[0];
        String username = params.split(",")[1];
        List<String>users=documentService.getusers(code);
        Map<String,String>codes= documentService.joinSession(code,username);
        System.out.println("After joining: ");
        for (String user : users) {
            System.out.println(user);
        }
        System.out.println("/////");
        return codes;
    }

@GetMapping("/getdoc")
public Document get(@RequestParam String code) {  // Change to @RequestParam
    return documentService.getDocumentFromCode(code);
}

}
