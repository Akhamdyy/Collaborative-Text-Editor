package com.example.server;

import org.springframework.stereotype.Service;

import javax.lang.model.element.NestingKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class DocumentService {

    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, String> viewerToEditorMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> Userlist= new ConcurrentHashMap<>();
    private final Map<String, List<Comment>> commentsByEditor = new ConcurrentHashMap<>();

    public synchronized Map<String,String>joinSession(String code,String Username)
    {
        //System.out.println("code is: "+code);
        String editorcode="";
        String viewercode="";
        boolean valid=isValidCode(code); // test if code is avaliable
        if(!valid) return null;
        System.out.println("1- "+code);
        String role= getRole(code);
        if(role.equals("editor"))
        {
            editorcode=code;
            viewercode=viewerToEditorMap.get(editorcode);

        }
        else
        {
            viewercode=code;
            //editorcode="hi";
            for(Map.Entry<String,String> entry: viewerToEditorMap.entrySet())
            {
                if(entry.getValue().equals(viewercode)){
                    editorcode=entry.getKey();
                }
            }

        }
        System.out.println("code: "+code);

        System.out.println("editor:"+editorcode);
        List<String> users=Userlist.getOrDefault(editorcode,new ArrayList<>());
        users.add(Username);

        Userlist.put(code,users);
        return Map.of(
                "editorCode", editorcode,
                "viewerCode", viewercode
        );

    }
    public synchronized Map<String, String> createSession(String username) {
        String editorCode = generateReadableCode() + "-e";
        String viewerCode = editorCode.replace("-e", "-v");
        List<String> users=new ArrayList<String>();
        users.add(username);
        viewerToEditorMap.put(editorCode,viewerCode);
        Userlist.put(editorCode,users);
        documents.put(editorCode, new Document());
        viewerToEditorMap.put(viewerCode, editorCode);

        return Map.of(
                "editorCode", editorCode,
                "viewerCode", viewerCode
        );
    }

    public Document getDocumentFromCode(String code) {
        String editorCode = code.endsWith("-v") ? viewerToEditorMap.get(code) : code;
        return documents.get(editorCode);
    }

    public String getRole(String code) {
        if (code.endsWith("-e")) return "editor";
        if (code.endsWith("-v")) return "viewer";
        return "unknown";
    }

    public boolean isValidCode(String code) {
        return documents.containsKey(code) || viewerToEditorMap.containsKey(code);
    }

    private String generateReadableCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
    public List<String> getusers(String roomcode)
    {
        if(getRole(roomcode)=="editor")
        {
            return Userlist.get(roomcode);
        }
        else
        {
            String viewer=roomcode;
            String editorcode="";
            for(Map.Entry<String,String> entry: viewerToEditorMap.entrySet())
            {
                if(entry.getValue().equals(viewer)){
                    editorcode=entry.getKey();
                }
            }

            return Userlist.get(editorcode);
        }
    }
    public List<String> removeuser(String roomcode,String username)
    {
        Userlist.get(roomcode).remove(username);
        if(getRole(roomcode)=="editor")
        {
            return Userlist.get(roomcode);
        }
        else
        {
            String viewer=roomcode;
            String editorcode="";
            for(Map.Entry<String,String> entry: viewerToEditorMap.entrySet())
            {
                if(entry.getValue().equals(viewer)){
                    editorcode=entry.getKey();
                }
            }
            return Userlist.get(editorcode);
        }
    }
    public String geteditorcode(String roomcode)
    {
        String editorcode="";
        if(getRole(roomcode)=="editor")
        {
            return roomcode;
        }
        else
        {
            String viewer=roomcode;
            editorcode="";
            for(Map.Entry<String,String> entry: viewerToEditorMap.entrySet())
            {
                if(entry.getValue().equals(viewer)){
                    editorcode=entry.getKey();
                }
            }
        }
        return editorcode;
    }
    public void addComment(String editorCode, Comment comment) {
        commentsByEditor.computeIfAbsent(editorCode, k -> new ArrayList<>()).add(comment);
    }

    public void removeComment(String editorCode, String commentId) {
//        System.out.println("I am here");
//        List<Comment> comments = commentsByEditor.getOrDefault(editorCode, new ArrayList<>());
//        for (Comment comment : comments) {
//            if (comment.getId().equals(commentId)) {
//                commentsByEditor.remove(editorCode);
//            }
//        }
//        //return comments;
        List<Comment> comments = commentsByEditor.getOrDefault(editorCode, new ArrayList<>());
        comments.removeIf(comment -> comment.getId().equals(commentId));
        commentsByEditor.put(editorCode,comments);


    }

    public void updateComments(String editorCode, List<Comment> updatedComments) {
        commentsByEditor.put(editorCode, updatedComments);
    }

    public List<Comment> getComments(String editorCode) {
        return commentsByEditor.getOrDefault(editorCode, new ArrayList<>());
    }
}
