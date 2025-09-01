package com.itheima.ai.entity.vo;
 
import lombok.Data;
import lombok.NoArgsConstructor;
 
@Data
@NoArgsConstructor
public class Result<T> {
    private Integer code;
    private String message;
    private T data;
 
    private Result(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
    
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
 
    public static Result<Void> ok() {
        return new Result<>(200, "ok");
    }
    
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "ok", data);
    }
 
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message);
    }
}