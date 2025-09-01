package com.itheima.ai.controller;

import com.itheima.ai.entity.vo.QuizGameState;
import com.itheima.ai.entity.vo.QuizAnswerRequest;
import com.itheima.ai.entity.vo.Result;
import com.itheima.ai.service.QuizGameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 知识问答游戏控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/quiz")
public class QuizGameController {
    
    private final QuizGameService quizGameService;
    
    /**
     * 开始新游戏
     */
    @PostMapping("/start/{pdfChatId}")
    public Result<QuizGameState> startGame(@PathVariable String pdfChatId) {
        try {
            QuizGameState gameState = quizGameService.startGame(pdfChatId);
            return Result.ok(gameState);
        } catch (Exception e) {
            log.error("开始游戏失败", e);
            return Result.fail("开始游戏失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取游戏状态
     */
    @GetMapping("/state/{gameId}")
    public Result<QuizGameState> getGameState(@PathVariable String gameId) {
        try {
            QuizGameState gameState = quizGameService.getGameState(gameId);
            return Result.ok(gameState);
        } catch (Exception e) {
            log.error("获取游戏状态失败", e);
            return Result.fail("获取游戏状态失败：" + e.getMessage());
        }
    }
    
    /**
     * 提交答案
     */
    @PostMapping("/answer")
    public Result<QuizGameState> submitAnswer(@RequestBody QuizAnswerRequest request) {
        try {
            QuizGameState gameState = quizGameService.submitAnswer(request);
            return Result.ok(gameState);
        } catch (Exception e) {
            log.error("提交答案失败", e);
            return Result.fail("提交答案失败：" + e.getMessage());
        }
    }
    
    /**
     * 生成下一题
     */
    @PostMapping("/next/{gameId}")
    public Result<QuizGameState> generateNextQuestion(@PathVariable String gameId) {
        try {
            QuizGameState gameState = quizGameService.generateNextQuestion(gameId);
            return Result.ok(gameState);
        } catch (Exception e) {
            log.error("生成下一题失败", e);
            return Result.fail("生成下一题失败：" + e.getMessage());
        }
    }
    
    /**
     * 结束游戏
     */
    @PostMapping("/finish/{gameId}")
    public Result<QuizGameState> finishGame(@PathVariable String gameId) {
        try {
            QuizGameState gameState = quizGameService.finishGame(gameId);
            return Result.ok(gameState);
        } catch (Exception e) {
            log.error("结束游戏失败", e);
            return Result.fail("结束游戏失败：" + e.getMessage());
        }
    }
}