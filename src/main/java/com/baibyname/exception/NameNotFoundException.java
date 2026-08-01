package com.baibyname.exception;

public class NameNotFoundException extends RuntimeException {

    public NameNotFoundException(String name) {
        super("Name not found: " + name);
    }
}
