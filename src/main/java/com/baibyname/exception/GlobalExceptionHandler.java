package com.baibyname.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Global exception handler for the application.
 * Handles NameNotFoundException and returns 404 status.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle NameNotFoundException by returning 404 Not Found.
     *
     * @param request the HTTP request
     * @param ex      the exception
     * @return error message
     */
    @ExceptionHandler(NameNotFoundException.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNameNotFoundException(
            HttpServletRequest request,
            NameNotFoundException ex) {
        return "Name not found: " + ex.getMessage();
    }
}
