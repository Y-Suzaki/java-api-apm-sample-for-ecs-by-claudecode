package com.example.api.exception;

public class CompanyAlreadyExistsException extends RuntimeException {
    public CompanyAlreadyExistsException(String name) {
        super("Company already exists: " + name);
    }
}