package com.itheima.ai.entity.vo;

import lombok.Data;

/**
 * 游戏答题请求
 */
@Data
public class QuizAnswerRequest {
    
    /**
     * 游戏ID
     */
    private String gameId;
    
    /**
     * 用户答案
     */
    private String answer;
    
    /**
     * 是否超时
     */
    private Boolean timeout = false;
}