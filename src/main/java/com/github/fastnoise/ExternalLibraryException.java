package com.github.fastnoise;

public class ExternalLibraryException extends RuntimeException {
    public ExternalLibraryException(String message) {
        super(message);
    }

    public ExternalLibraryException(String message, Exception e) {
        super(message, e);
    }

    public ExternalLibraryException(String message, Throwable e) {
        super(message, e);
    }

    public ExternalLibraryException(Throwable e) {
        super(e);
    }
}
