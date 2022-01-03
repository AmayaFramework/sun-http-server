package io.github.amayaframework.server.implementations;

public class HttpException extends RuntimeException {
    public HttpException(String message) {
        super(message);
    }
}
