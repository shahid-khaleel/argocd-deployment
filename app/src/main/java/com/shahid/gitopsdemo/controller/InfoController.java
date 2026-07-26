package com.shahid.gitopsdemo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes values sourced from the ConfigMap so a GitOps manifest change
 * (bumping app.version / app.welcome-message) is visibly reflected by the
 * running application without any code or image change.
 */
@RestController
public class InfoController {

    private final String version;
    private final String welcomeMessage;

    public InfoController(@Value("${app.version}") String version,
                           @Value("${app.welcome-message}") String welcomeMessage) {
        this.version = version;
        this.welcomeMessage = welcomeMessage;
    }

    @GetMapping("/api/version")
    public Map<String, String> version() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("welcomeMessage", welcomeMessage);
        body.put("hostname", hostname());
        return body;
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
