package com.baibyname.exception;

/**
 * Exception thrown when a filter value is not recognized.
 * <p>
 * This exception is used to reject invalid filter parameters such as
 * an unrecognized sex value in the browse filter endpoint.
 */
public class InvalidFilterValueException extends RuntimeException {

    public InvalidFilterValueException(String message) {
        super(message);
    }

    public InvalidFilterValueException(String value, String allowedValues) {
        super("Invalid value '" + value + "'. Allowed values: " + allowedValues);
    }
}
