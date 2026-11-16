package com.mphasis.gateway.controller;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping("/health")
    public Map<String,Object> health(){
        jdbc.queryForObject("SELECT 1",Integer.class);
        return Map.of("service","event-gateway","status","UP","database","UP","timestamp", Instant.now());
    }
}
