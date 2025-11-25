package com.insidergame.insider_api.common;

import org.springframework.http.HttpStatus;
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private HttpStatus status;

    public ApiResponse() {
    }

    public ApiResponse(boolean success, String message, T data, HttpStatus status) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.status = status;
    }


    public ApiResponse(boolean success, String message, T data) {
        this(success, message, data, success ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
    }


    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public void setStatus(HttpStatus status) {
        this.status = status;
    }

}
