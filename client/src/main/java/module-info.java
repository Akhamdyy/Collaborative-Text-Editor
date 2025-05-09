module com.example.client {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires spring.messaging;
    requires spring.websocket;
    requires spring.core;
    requires spring.web;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires java.desktop;
    requires org.apache.logging.log4j;
    opens com.example.client to com.fasterxml.jackson.databind, javafx.fxml;
    exports com.example.client;
}