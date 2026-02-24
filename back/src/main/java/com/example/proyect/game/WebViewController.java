package com.example.proyect.game;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for serving protected HTML pages.
 * Similar to Express.js app.get('/route') pattern.
 */
@Controller
public class WebViewController {

    private final ResourceLoader resourceLoader;

    public WebViewController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Serve HTML file from front/screen directory
     */
    private ResponseEntity<String> serveHtmlFile(String filename) {
        try {
            Resource resource = resourceLoader.getResource("file:front/screen/" + filename);
            String content = StreamUtils.copyToString(
                resource.getInputStream(), 
                StandardCharsets.UTF_8
            );
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Login page - PUBLIC
     */
    @GetMapping("/login")
    public ResponseEntity<String> login() {
        return serveHtmlFile("Login.html");
    }

    /**
     * Menu page - PROTECTED
     * User must be authenticated to access
     */
    @GetMapping("/menu")
    public ResponseEntity<String> menu() {
        return serveHtmlFile("Menu.html");
    }

    /**
     * Lobby browser page - PROTECTED
     */
    @GetMapping("/lobby-browser")
    public ResponseEntity<String> lobbyBrowser() {
        return serveHtmlFile("LobbyBrowser.html");
    }

    @GetMapping("/saved-games")
    public ResponseEntity<String> load() {
        return serveHtmlFile("SavedGames.html");
    }

    @GetMapping("/ranking")
    public ResponseEntity<String> ranking() {
        return serveHtmlFile("Ranking.html");
    }
    /**
     * Lobby waiting page - PROTECTED
     */
    @GetMapping("/lobby-waiting")
    public ResponseEntity<String> lobbyWaiting() {
        return serveHtmlFile("LobbyWaiting.html");
    }

    /**
     * Game page - PROTECTED
     */
    @GetMapping("/game")
    public ResponseEntity<String> game() {
        return serveHtmlFile("Game.html");
    }
}
