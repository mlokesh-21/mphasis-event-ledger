package com.mphasis.gateway.controller;

import com.mphasis.gateway.exception.AccountServiceUnavailableException;
import com.mphasis.gateway.service.EventService;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        String message=ex.getBindingResult().getFieldErrors().stream()
            .map(e->e.getField()+": "+e.getDefaultMessage()).collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(body(400,"Bad Request",message));
    }
    @ExceptionHandler(AccountServiceUnavailableException.class)
    ResponseEntity<?> unavailable(AccountServiceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body(503,"Service Unavailable",ex.getMessage()));
    }
    @ExceptionHandler(EventService.EventNotFoundException.class)
    ResponseEntity<?> notFound(EventService.EventNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(404,"Not Found",ex.getMessage()));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> other(Exception ex) {
        return ResponseEntity.status(500).body(body(500,"Internal Server Error","Unexpected server error"));
    }
    private Map<String,Object> body(int status,String error,String message){
        return Map.of("timestamp",Instant.now(),"status",status,"error",error,"message",message);
    }
}
