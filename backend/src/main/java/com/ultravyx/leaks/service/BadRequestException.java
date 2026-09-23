package com.ultravyx.leaks.service;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
