package com.example.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.converter.cbor.MappingJackson2CborHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpHelper {
    private static final RestTemplate restTemplate= new RestTemplate();
    public static String baseUrl;
    HttpHelper()
    {
        //restTemplate.getMessageConverters().add(new MappingJackson2CborHttpMessageConverter());
    }
    public  Map<String,String> createSession(String username) {
        Map<String,String> sessionCodes = new ConcurrentHashMap<>();
        String url = baseUrl + "/create";
        try{
            sessionCodes=restTemplate.postForObject(url,username,Map.class);
            System.out.println("Session created successfully\n");
        }catch (Exception e){
            System.out.println("Failed to create session with error " + e.getMessage());
            System.out.println("Exiting..");
            System.exit(0);
        }
        return sessionCodes;
    }
    public  Map<String,String> joinSession(String code,String username) {
        Map<String,String> sessionCodes = new ConcurrentHashMap<>();
        String url = baseUrl + "/join";
        try{
            String params = code+","+username;
            System.out.println(username+" is trying to join");
            sessionCodes=restTemplate.postForObject(url,params,Map.class);
            System.out.println("Session Joined successfully\n");
        }catch (Exception e){
            System.out.println("Failed to Join session with error " + e.getMessage());
            System.out.println("Exiting..");
            System.exit(0);
        }
        return sessionCodes;
    }

    public  Document requestDocument(String roomCode) throws IOException, InterruptedException {
        String BASE_URL = baseUrl + "/document/";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + roomCode))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Deserialize JSON to Document using Jackson or Gson
        ObjectMapper mapper = new ObjectMapper();


        Document d=mapper.readValue(response.body(), Document.class);
        System.out.println("Document fetched successfully gowa requestdocument\n");
        System.out.println("data:"+d.getText()+"/");
        return d;
    }
    public  Document getDocumentFromCode(String code) {
        String url = baseUrl + "/getdoc?code={code}";
        try {
            Document document = restTemplate.getForObject(url, Document.class, code);
            System.out.println("Document fetched successfully\n");
            return document;
        } catch (Exception e) {
            System.out.println("Failed to fetch document with error " + e.getMessage());
            System.out.println("Exiting..");
            return null;
        }
}
}
