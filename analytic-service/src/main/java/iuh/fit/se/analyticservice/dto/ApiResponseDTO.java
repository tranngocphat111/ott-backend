package iuh.fit.se.analyticservice.dto;

import lombok.Data;

@Data
public class ApiResponseDTO<T> {
    private int code;
    private String message;
    private T result;
}
