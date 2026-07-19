package com.example.api.exception;

public class SubsidiaryNotFoundException extends RuntimeException {
    public SubsidiaryNotFoundException(Long id) {
        super("Subsidiary not found: " + id);
    }
}
