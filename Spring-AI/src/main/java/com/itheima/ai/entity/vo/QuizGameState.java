package com.itheima.ai.entity.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识问答游戏状态
 */
@Data
public class QuizGameState {
    
    /**
     * 游戏ID
     */
    private String gameId;
    
    /**
     * 当前轮次 (1-10)
     */
    private Integer currentRound;
    
    /**
     * 总得分
     */
    private Integer totalScore;
    
    /**
     * 游戏状态：WAITING, PLAYING, FINISHED
     */
    private String status;
    
    /**
     * 当前问题
     */
    private String currentQuestion;
    
    /**
     * 当前问题开始时间
     */
    private LocalDateTime questionStartTime;
    
    /**
     * 剩余时间(秒)
     */
    private Long remainingTime;
    
    /**
     * 每轮得分记录
     */
    private List<RoundScore> roundScores = new ArrayList<>();
    
    /**
     * 使用的PDF文件名
     */
    private String pdfFileName;
    
    @Data
    public static class RoundScore {
        /**
         * 轮次
         */
        private Integer round;
        
        /**
         * 问题
         */
        private String question;
        
        /**
         * 用户答案
         */
        private String userAnswer;
        
        /**
         * 得分
         */
        private Integer score;
        
        /**
         * 评分详情
         */
        private String feedback;
        
        /**
         * 是否超时
         */
        private Boolean timeout;
    }
}