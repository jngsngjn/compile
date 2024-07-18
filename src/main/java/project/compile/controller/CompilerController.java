package project.compile.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Controller
public class CompilerController {

    private final WebClient webClient;

    public CompilerController(WebClient.Builder webClientBuilder, @Value("${apiKey}") String key) {
        this.webClient = webClientBuilder
                .baseUrl("https://judge0-ce.p.rapidapi.com")
                .defaultHeader("x-rapidapi-key", key)
                .defaultHeader("x-rapidapi-host", "judge0-ce.p.rapidapi.com")
                .build();
    }

    @GetMapping("/")
    public String index(Model model) {
        String defaultCode = """
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
            """;
        model.addAttribute("sourceCode", defaultCode);
        return "index";
    }

    @PostMapping("/compile")
    public Mono<ResponseEntity<Map<String, String>>> compileCode(@RequestBody Map<String, String> requestBody) {
        String sourceCode = requestBody.get("sourceCode");
        String encodedSourceCode = Base64.getEncoder().encodeToString(sourceCode.getBytes());

        Map<String, String> requestPayload = new HashMap<>();
        requestPayload.put("language_id", "62"); // Java
        requestPayload.put("source_code", encodedSourceCode);
        requestPayload.put("stdin", "");

        return this.webClient.post()
                .uri("/submissions?base64_encoded=true&wait=false")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestPayload)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    String token = (String) response.get("token");
                    return checkResult(token);
                })
                .map(result -> {
                    Map<String, String> responseMap = new HashMap<>();
                    StringBuilder output = new StringBuilder();
                    String stdout = (String) result.get("stdout");
                    String stderr = (String) result.get("stderr");
                    String compileOutput = (String) result.get("compile_output");
                    String message = (String) result.get("message");


                    if (stdout != null) {
                        output.append(new String(Base64.getDecoder().decode(stdout)));
                    }
                    if (stderr != null) {
                        output.append(new String(Base64.getDecoder().decode(stderr)));
                    }
                    if (compileOutput != null) {
                        output.append(new String(Base64.getDecoder().decode(compileOutput)));
                    }
                    if (message != null) {
                        output.append(message);
                    }

                    responseMap.put("output", output.toString());
                    return ResponseEntity.ok(responseMap);
                })
                .onErrorResume(e -> {
                    Map<String, String> responseMap = new HashMap<>();
                    responseMap.put("output", "An error occurred: " + e.getMessage());
                    return Mono.just(ResponseEntity.ok(responseMap));
                });
    }

    private Mono<Map> checkResult(String token) {
        return this.webClient.get()
                .uri("/submissions/" + token + "?base64_encoded=true")
                .retrieve()
                .bodyToMono(Map.class);
    }
}