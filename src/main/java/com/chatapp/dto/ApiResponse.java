package com.chatapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private boolean isSuccess;

    private T data;
    private String error;
    private String message;

    public ApiResponse() {
    }

    public ApiResponse(boolean isSuccess, T data, String error, String message) {
        this.isSuccess = isSuccess;
        this.data = data;
        this.error = error;
        this.message = message != null ? message : "";
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, null, message);
    }

    public static <T> ApiResponse<T> failure(String error) {
        return new ApiResponse<>(false, null, error, "");
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
