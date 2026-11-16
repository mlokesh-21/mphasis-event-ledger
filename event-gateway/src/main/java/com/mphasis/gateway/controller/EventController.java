package com.mphasis.gateway.controller;

import com.mphasis.gateway.client.AccountClient;
import com.mphasis.gateway.dto.*;
import com.mphasis.gateway.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
public class EventController {
    private final EventService eventService;
    private final AccountClient accountClient;
    public EventController(EventService eventService, AccountClient accountClient) {
        this.eventService=eventService; this.accountClient=accountClient;
    }
    @PostMapping("/events")
    public ResponseEntity<EventResponse> submit(@Valid @RequestBody EventRequest request) {
        var result=eventService.submit(request);
        return ResponseEntity.status(result.duplicate()?HttpStatus.OK:HttpStatus.CREATED).body(result.event());
    }

    @GetMapping("/events/{id}")
    public EventResponse get(@PathVariable("id") String id) {
        return eventService.get(id);
    }

    @GetMapping("/events")
    public List<EventResponse> list(
            @RequestParam("account") String account) {
        return eventService.list(account);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public Map<?, ?> balance(
            @PathVariable("accountId") String accountId) {
        return accountClient.getBalance(accountId);
    }

}
