package com.itheima.ai.service;

import com.itheima.ai.entity.vo.QuizGameState;
import com.itheima.ai.entity.vo.QuizAnswerRequest;

/**
 * 知识问答游戏服务
 */
public interface QuizGameService {
    
    /**
     * 开始新游戏
     * @param pdfChatId PDF聊天会话ID
     * @return 游戏状态
     */
    QuizGameState startGame(String pdfChatId);
    
    /**
     * 获取游戏状态
     * @param gameId 游戏ID
     * @return 游戏状态
     */
    QuizGameState getGameState(String gameId);
    
    /**
     * 提交答案
     * @param request 答题请求
     * @return 更新后的游戏状态
     */
    QuizGameState submitAnswer(QuizAnswerRequest request);
    
    /**
     * 生成下一题
     * @param gameId 游戏ID
     * @return 更新后的游戏状态
     */
    QuizGameState generateNextQuestion(String gameId);
    
    /**
     * 结束游戏
     * @param gameId 游戏ID
     * @return 最终游戏状态
     */
    QuizGameState finishGame(String gameId);
}