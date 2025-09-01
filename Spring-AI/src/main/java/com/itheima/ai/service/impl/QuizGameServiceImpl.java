package com.itheima.ai.service.impl;

import com.itheima.ai.entity.vo.QuizGameState;
import com.itheima.ai.entity.vo.QuizAnswerRequest;
import com.itheima.ai.repository.FileRepository;
import com.itheima.ai.service.QuizGameService;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识问答游戏服务实现
 */
@Slf4j
@Service
public class QuizGameServiceImpl implements QuizGameService {
    
    private final FileRepository fileRepository;
    private final VectorStore vectorStore;
    private final ChatClient quizGameChatClient;
    private final EmbeddingModel embeddingModel;
    
    public QuizGameServiceImpl(FileRepository fileRepository, 
                              VectorStore vectorStore,
                              @Qualifier("quizGameChatClient") ChatClient quizGameChatClient,
                              @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        this.fileRepository = fileRepository;
        this.vectorStore = vectorStore;
        this.quizGameChatClient = quizGameChatClient;
        this.embeddingModel = embeddingModel;
    }
    
    // 游戏状态存储 (实际项目中应该用Redis或数据库)
    private final Map<String, QuizGameState> gameStates = new ConcurrentHashMap<>();
    
    // 存储每个游戏已生成的问题，防止重复
    private final Map<String, Set<String>> gameQuestions = new ConcurrentHashMap<>();
    
    // 存储每个游戏的预生成问题列表
    private final Map<String, List<String>> preGeneratedQuestions = new ConcurrentHashMap<>();
    
    // 游戏配置
    private static final int TOTAL_ROUNDS = 10;
    private static final long QUESTION_TIMEOUT_SECONDS = 180; // 3分钟
    private static final int MAX_SCORE_PER_QUESTION = 10;
    
    @Override
    public QuizGameState startGame(String pdfChatId) {
        // 1. 验证PDF文件是否存在
        Resource file = fileRepository.getFile(pdfChatId);
        if (!file.exists()) {
            throw new RuntimeException("PDF文件不存在，无法开始游戏");
        }
        
        // 2. 创建游戏状态
        String gameId = UUID.randomUUID().toString();
        QuizGameState gameState = new QuizGameState();
        gameState.setGameId(gameId);
        gameState.setCurrentRound(0);
        gameState.setTotalScore(0);
        gameState.setStatus("WAITING");
        gameState.setPdfFileName(file.getFilename());
        
        // 3. 保存游戏状态
        gameStates.put(gameId, gameState);
        
        // 4. 初始化问题集合
        gameQuestions.put(gameId, new HashSet<>());
        
        // 5. 预生成所有问题（在后台异步进行）
        generateAllQuestionsAsync(gameId, file.getFilename());
        
        log.info("开始新游戏: gameId={}, pdfFile={}", gameId, file.getFilename());
        return gameState;
    }
    
    @Override
    public QuizGameState getGameState(String gameId) {
        QuizGameState gameState = gameStates.get(gameId);
        if (gameState == null) {
            throw new RuntimeException("游戏不存在");
        }
        
        // 更新剩余时间
        if ("PLAYING".equals(gameState.getStatus()) && gameState.getQuestionStartTime() != null) {
            long elapsed = ChronoUnit.SECONDS.between(gameState.getQuestionStartTime(), LocalDateTime.now());
            long remaining = Math.max(0, QUESTION_TIMEOUT_SECONDS - elapsed);
            gameState.setRemainingTime(remaining);
            
            // 检查是否超时
            if (remaining <= 0) {
                handleTimeout(gameState);
            }
        }
        
        return gameState;
    }
    
    @Override
    public QuizGameState submitAnswer(QuizAnswerRequest request) {
        QuizGameState gameState = gameStates.get(request.getGameId());
        if (gameState == null) {
            throw new RuntimeException("游戏不存在");
        }
        
        if (!"PLAYING".equals(gameState.getStatus())) {
            throw new RuntimeException("游戏状态不正确");
        }
        
        // 检查是否已经回答过当前问题（防止重复提交）
        int currentRound = gameState.getCurrentRound();
        boolean alreadyAnswered = gameState.getRoundScores().stream()
                .anyMatch(rs -> rs.getRound() == currentRound);
        
        if (alreadyAnswered) {
            log.warn("重复提交答案: gameId={}, round={}", request.getGameId(), currentRound);
            return gameState; // 直接返回当前状态，不重复计分
        }
        
        // 检查是否超时
        boolean timeout = request.getTimeout() || isQuestionTimeout(gameState);
        
        // 评分
        int score = 0;
        String feedback = "超时未答题";
        
        if (!timeout && request.getAnswer() != null && !request.getAnswer().trim().isEmpty()) {
            ScoreResult scoreResult = evaluateAnswer(gameState.getCurrentQuestion(), request.getAnswer(), gameState.getPdfFileName());
            score = scoreResult.score;
            feedback = scoreResult.feedback;
        }
        
        // 记录本轮结果
        QuizGameState.RoundScore roundScore = new QuizGameState.RoundScore();
        roundScore.setRound(gameState.getCurrentRound());
        roundScore.setQuestion(gameState.getCurrentQuestion());
        roundScore.setUserAnswer(request.getAnswer());
        roundScore.setScore(score);
        roundScore.setFeedback(feedback);
        roundScore.setTimeout(timeout);
        
        gameState.getRoundScores().add(roundScore);
        gameState.setTotalScore(gameState.getTotalScore() + score);
        
        // 检查是否游戏结束
        if (gameState.getCurrentRound() >= TOTAL_ROUNDS) {
            gameState.setStatus("FINISHED");
            // 清理游戏问题缓存
            gameQuestions.remove(request.getGameId());
            preGeneratedQuestions.remove(request.getGameId());
            log.info("游戏结束: gameId={}, 总分={}", request.getGameId(), gameState.getTotalScore());
        } else {
            // 设置为等待下一题状态
            gameState.setStatus("WAITING_NEXT");
            log.info("答题完成: gameId={}, 当前轮次={}, 本题得分={}", 
                    request.getGameId(), gameState.getCurrentRound(), score);
        }
        
        return gameState;
    }
    
    @Override
    public QuizGameState generateNextQuestion(String gameId) {
        QuizGameState gameState = gameStates.get(gameId);
        if (gameState == null) {
            throw new RuntimeException("游戏不存在");
        }
        
        // 只有在WAITING_NEXT或WAITING状态下才能生成下一题
        if (!"WAITING_NEXT".equals(gameState.getStatus()) && !"WAITING".equals(gameState.getStatus())) {
            log.warn("无法生成下一题: gameId={}, currentStatus={}", gameId, gameState.getStatus());
            return gameState;
        }
        
        // 对于初始状态WAITING，不增加轮次；对于WAITING_NEXT，增加轮次
        if ("WAITING_NEXT".equals(gameState.getStatus())) {
            gameState.setCurrentRound(gameState.getCurrentRound() + 1);
        } else if ("WAITING".equals(gameState.getStatus())) {
            // 第一题，轮次从0开始，设置为1
            gameState.setCurrentRound(1);
        }
        
        if (gameState.getCurrentRound() > TOTAL_ROUNDS) {
            gameState.setStatus("FINISHED");
            return gameState;
        }
        
        // 生成问题（优先使用预生成的问题）
        String question = getPreGeneratedQuestion(gameId, gameState.getCurrentRound());
        if (question == null) {
            // 如果预生成失败，则实时生成
            question = generateUniqueQuestion(gameState.getPdfFileName(), gameState.getCurrentRound(), gameId);
        }
        
        gameState.setCurrentQuestion(question);
        gameState.setQuestionStartTime(LocalDateTime.now());
        gameState.setRemainingTime(QUESTION_TIMEOUT_SECONDS);
        gameState.setStatus("PLAYING");
        
        log.info("生成第{}题: gameId={}, question={}", gameState.getCurrentRound(), gameId, question);
        return gameState;
    }
    
    @Override
    public QuizGameState finishGame(String gameId) {
        QuizGameState gameState = gameStates.get(gameId);
        if (gameState == null) {
            throw new RuntimeException("游戏不存在");
        }
        
        gameState.setStatus("FINISHED");
        
        // 清理游戏问题缓存
        gameQuestions.remove(gameId);
        preGeneratedQuestions.remove(gameId);
        
        log.info("手动结束游戏: gameId={}, 总分={}", gameId, gameState.getTotalScore());
        return gameState;
    }
    
    /**
     * 生成不重复的问题
     */
    private String generateUniqueQuestion(String pdfFileName, int round, String gameId) {
        Set<String> usedQuestions = gameQuestions.get(gameId);
        if (usedQuestions == null) {
            usedQuestions = new HashSet<>();
            gameQuestions.put(gameId, usedQuestions);
        }
        
        String question;
        int attempts = 0;
        final int maxAttempts = 8; // 增加尝试次数
        
        do {
            question = generateQuestion(pdfFileName, round, attempts);
            attempts++;
            
            // 语义去重检查
            if (!isQuestionSimilar(question, usedQuestions)) {
                break;
            }
            
            // 如果尝试次数过多，使用带轮次信息的默认问题
            if (attempts >= maxAttempts) {
                question = getUniqueDefaultQuestion(round, usedQuestions);
                break;
            }
        } while (attempts < maxAttempts);
        
        // 记录已使用的问题
        usedQuestions.add(question);
        
        log.info("生成唯一问题: round={}, attempts={}, question={}", round, attempts, question);
        return question;
    }
    
    /**
     * 检查问题是否语义相似（简化版）
     */
    private boolean isQuestionSimilar(String newQuestion, Set<String> usedQuestions) {
        if (usedQuestions.isEmpty()) {
            return false;
        }
        
        // 提取新问题的关键词
        String[] newKeywords = extractKeywords(newQuestion);
        
        for (String usedQuestion : usedQuestions) {
            String[] usedKeywords = extractKeywords(usedQuestion);
            
            // 计算关键词重叠度
            int commonCount = 0;
            for (String newKeyword : newKeywords) {
                for (String usedKeyword : usedKeywords) {
                    if (newKeyword.equals(usedKeyword)) {
                        commonCount++;
                    }
                }
            }
            
            // 如果关键词重叠度超过60%，认为是相似问题
            double similarity = (double) commonCount / Math.max(newKeywords.length, usedKeywords.length);
            if (similarity > 0.6) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 提取问题关键词
     */
    private String[] extractKeywords(String question) {
        // 简化的关键词提取，去除常见词汇
        String[] commonWords = {"什么", "是", "的", "如何", "怎样", "为什么", "哪些", "请", "简述", "解释", "说明", "？", "。", "，"};
        
        String cleaned = question;
        for (String word : commonWords) {
            cleaned = cleaned.replace(word, " ");
        }
        
        return cleaned.trim().split("\\s+");
    }
    
    /**
     * 异步预生成所有问题
     */
    private void generateAllQuestionsAsync(String gameId, String pdfFileName) {
        // 使用新线程异步生成，避免阻塞主线程
        new Thread(() -> {
            try {
                List<String> questions = new ArrayList<>();
                Set<String> usedQuestions = new HashSet<>();
                
                log.info("开始预生成问题: gameId={}, pdfFileName={}", gameId, pdfFileName);
                
                for (int round = 1; round <= TOTAL_ROUNDS; round++) {
                    String question = generateUniqueQuestionSync(pdfFileName, round, usedQuestions);
                    questions.add(question);
                    usedQuestions.add(question);
                    
                    log.info("预生成第{}题: {}", round, question);
                }
                
                // 保存预生成的问题
                preGeneratedQuestions.put(gameId, questions);
                
                log.info("预生成问题完成: gameId={}, total={}", gameId, questions.size());
                
            } catch (Exception e) {
                log.error("预生成问题失败: gameId={}", gameId, e);
            }
        }).start();
    }
    
    /**
     * 同步生成唯一问题（用于预生成）
     */
    private String generateUniqueQuestionSync(String pdfFileName, int round, Set<String> usedQuestions) {
        String question;
        int attempts = 0;
        final int maxAttempts = 5;
        
        do {
            question = generateSimpleQuestion(pdfFileName, round, attempts);
            attempts++;
            
            if (!isQuestionSimilar(question, usedQuestions)) {
                break;
            }
            
            if (attempts >= maxAttempts) {
                question = getUniqueDefaultQuestion(round, usedQuestions);
                break;
            }
        } while (attempts < maxAttempts);
        
        return question;
    }
    
    /**
     * 简化的问题生成（用于预生成）
     */
    private String generateSimpleQuestion(String pdfFileName, int round, int attempts) {
        String difficulty = getDifficultyByRound(round);
        String contextInfo = getRelevantContext(pdfFileName);
        
        String prompt = String.format(
            "你是一个专业AI考官，请基于PDF文档内容出一道%s难度的问题。\n" +
            "第%d次尝试，请确保问题的独特性。\n" +
            "要求：1.具体明确 2.直接提问 3.包含具体知识点 4.不超过40字\n" +
            "文档内容：%s\n" +
            "请直接输出问题：",
            difficulty, attempts + 1, 
            contextInfo != null && !contextInfo.trim().isEmpty() ? contextInfo.substring(0, Math.min(200, contextInfo.length())) : "根据文档内容"
        );
        
        try {
            String question = quizGameChatClient.prompt()
                    .user(prompt)
                    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "file_name == '" + pdfFileName + "'"))
                    .call()
                    .content();
            
            return question.trim();
        } catch (Exception e) {
            log.warn("生成问题失败: round={}, attempts={}", round, attempts, e);
            return getSimpleDefaultQuestion(round);
        }
    }
    
    /**
     * 获取简单默认问题
     */
    private String getSimpleDefaultQuestion(int round) {
        String[] questions = {
            "什么是面向对象编程的封装原则？",
            "Java中的继承机制如何工作？",
            "数据库中的主键约束有什么作用？",
            "HTTP协议中的状态码200表示什么？",
            "Spring框架中的依赖注入是如何实现的？",
            "数据结构中链表的优势是什么？",
            "MySQL中的B+树索引原理是什么？",
            "JavaScript中的原型链是如何工作的？",
            "Redis中的哈希表适用于什么场景？",
            "什么是微服务架构中的服务注册与发现？"
        };
        
        int index = (round - 1) % questions.length;
        return questions[index];
    }
        
    /**
     * 获取预生成的问题
     */
    private String getPreGeneratedQuestion(String gameId, int round) {
        List<String> questions = preGeneratedQuestions.get(gameId);
        if (questions != null && questions.size() >= round && round > 0) {
            String question = questions.get(round - 1);
            log.info("使用预生成问题: gameId={}, round={}, question={}", gameId, round, question);
            return question;
        }
        
        log.warn("预生成问题不可用: gameId={}, round={}, available={}", 
                gameId, round, questions != null ? questions.size() : 0);
        return null;
    }
    private String getUniqueDefaultQuestion(int round, Set<String> usedQuestions) {
        String[] defaultQuestions = {
            "什么是面向对象编程中的封装原则？",
            "Java中String和StringBuilder的区别是什么？",
            "数据库中的主键和外键分别有什么作用？",
            "什么是HTTP协议中的GET和POST方法区别？",
            "Spring框架中的IOC容器如何工作？",
            "数据结构中栈和队列的主要区别是什么？",
            "MySQL中的索引是如何提高查询效率的？",
            "JavaScript中的闭包概念是怎样的？",
            "Redis中的哪些数据类型及其应用场景？",
            "什么是软件工程中的敏捷开发模式？",
            "TCP/IP协议栈的四层结构分别是什么？",
            "Linux系统中的文件权限管理是如何实现的？",
            "Docker容器技术的核心优势是什么？",
            "什么是微服务架构中的服务注册与发现？",
            "Vue.js中的响应式数据原理是怎样的？"
        };
        
        // 先尝试基础问题
        for (String defaultQuestion : defaultQuestions) {
            if (!usedQuestions.contains(defaultQuestion)) {
                return defaultQuestion;
            }
        }
        
        // 如果所有默认问题都用过了，使用新的具体问题库
        String[] additionalQuestions = {
            "Spring Boot中的依赖注入机制原理是什么？",
            "MySQL中事务的隔离级别有哪些？",
            "Java中多线程同步的方式有哪些？",
            "Vue.js中生命周期钩子的执行顺序是什么？",
            "Redis中的数据结构String的底层实现原理是什么？"
        };
        int additionalIndex = (round - 1) % additionalQuestions.length;
        return additionalQuestions[additionalIndex];
    }
    private String generateQuestion(String pdfFileName, int round, int attempts) {
        String difficulty = getDifficultyByRound(round);
        
        log.info("开始生成问题: round={}, difficulty={}, pdfFileName={}, attempts={}", round, difficulty, pdfFileName, attempts);
        
        // 先检索相关文档内容
        String contextInfo = getRelevantContext(pdfFileName);
        
        String prompt;
        if (contextInfo != null && !contextInfo.trim().isEmpty()) {
            prompt = String.format(
                "你是一个专业AI考官，请基于PDF文档内容出一道%s难度的问题。\n" +
                "第%d次尝试，请确保问题的多样性和独特性。\n\n" +
                "严格要求：\n" +
                "1. 问题必须具体、明确、可操作，不允许模糊不清\n" +
                "2. 必须直接提问，不要任何开场白或结尾\n" +
                "3. 严格禁止使用：'请简述'、'请解释'、'请谈谈'、'你认为'、'你如何理解'、'你能举例'等模糊词汇\n" +
                "4. 必须包含具体的技术名词、知识点名称或技术概念\n" +
                "5. 禁止使用'第几题'、'现在是'等轮次表述\n" +
                "6. 问题长度控制在40字以内\n" +
                "7. 答案必须能在文档中找到明确依据\n" +
                "8. 问题必须以“什么是”、“如何”、“为什么”、“哪些”等开头\n\n" +
                "文档内容摘要：%s\n\n" +
                "现在请直接输出一个符合要求的问题：",
                difficulty, attempts + 1, contextInfo
            );
        } else {
            // 如果没有检索到相关内容，使用通用问题
            prompt = String.format(
                "你是一个专业AI考官，请基于PDF文档内容出一道%s难度的问题。\n\n" +
                "严格要求：\n" +
                "1. 问题必须具体、明确、可操作，不允许模糊不清\n" +
                "2. 必须直接提问，不要任何开场白或结尾\n" +
                "3. 严格禁止使用：'请简述'、'请解释'、'请谈谈'、'你认为'、'你如何理解'、'你能举例'等模糊词汇\n" +
                "4. 必须包含具体的技术名词、知识点名称或技术概念\n" +
                "5. 禁止使用'第几题'、'现在是'等轮次表述\n" +
                "6. 问题长度控制在40字以内\n" +
                "7. 答案必须能在文档中找到明确依据\n" +
                "8. 问题必须以“什么是”、“如何”、“为什么”、“哪些”等开头\n\n" +
                "现在请直接输出一个符合要求的问题：",
                difficulty
            );
        }
        
        log.info("使用的prompt: {}", prompt);
        
        try {
            String question = quizGameChatClient.prompt()
                    .user(prompt)
                    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "file_name == '" + pdfFileName + "'"))
                    .call()
                    .content();
            
            log.info("生成问题成功: round={}, difficulty={}, hasContext={}, question={}", 
                    round, difficulty, (contextInfo != null && !contextInfo.trim().isEmpty()), question);
            
            return question;
        } catch (Exception e) {
            log.error("生成问题失败: round={}, difficulty={}", round, difficulty, e);
            // 如果AI生成失败，返回具体的技术问题
            String[] specificQuestions = {
                "Spring Boot自动配置的核心原理是什么？",
                "MySQL中B+树索引相比于哈希索引的优势是什么？",
                "Java虚拟机中垃圾回收的标记-清除算法如何工作？",
                "Redis中的持久化机制RDB和AOF有什么区别？",
                "Spring框架中AOP的底层实现原理是什么？",
                "HTTP协议中GET和POST请求的本质区别是什么？",
                "数据库事务的ACID四个特性分别指什么？",
                "Java并发编程中synchronized关键字的作用机制是什么？",
                "Vue.js中的响应式数据绑定原理是如何实现的？",
                "Docker容器技术相比传统虚拟机的核心优势是什么？",
                "算法中快速排序的时间复杂度和空间复杂度是多少？",
                "设计模式中单例模式的线程安全实现方式有哪些？",
                "数据结构中红黑树相比普通二叉搜索树的优势是什么？",
                "TCP协议三次握手过程中每一步的作用是什么？",
                "微服务架构中服务熔断和服务降级的区别是什么？"
            };
            int index = (round - 1) % specificQuestions.length;
            String specificQuestion = specificQuestions[index];
            log.info("使用具体技术问题: {}", specificQuestion);
            return specificQuestion;
        }
    }
    
    /**
     * 评估答案
     */
    private ScoreResult evaluateAnswer(String question, String userAnswer, String pdfFileName) {
        // 检查答案是否为空或太短
        if (userAnswer == null || userAnswer.trim().isEmpty() || userAnswer.trim().length() < 5) {
            return new ScoreResult(0, "答案太短或为空，请提供有意义的回答。");
        }
        
        // 检查是否为明显的无意义回答
        if (isInvalidAnswer(userAnswer)) {
            return new ScoreResult(0, "答案无意义或与问题不相关，请认真回答。");
        }
        
        // 1. AI语义理解评分 (70%)
        int aiScore = getStrictAIScore(question, userAnswer, pdfFileName);
        
        // 2. 关键词匹配评分 (30%)
        int keywordScore = getEnhancedKeywordScore(question, userAnswer, pdfFileName);
        
        int totalScore = Math.min(MAX_SCORE_PER_QUESTION, aiScore + keywordScore);
        
        String feedback = generateDetailedFeedback(question, userAnswer, totalScore, pdfFileName);
        
        log.info("评分结果: question={}, userAnswer={}, aiScore={}, keywordScore={}, totalScore={}", 
                question, userAnswer, aiScore, keywordScore, totalScore);
        
        return new ScoreResult(totalScore, feedback);
    }
    
    /**
     * 检查是否为无意义答案
     */
    private boolean isInvalidAnswer(String userAnswer) {
        String answer = userAnswer.toLowerCase().trim();
        
        // 检查是否为常见的无意义回答
        String[] invalidPatterns = {
            "asdf", "qwer", "1234", "abcd", "test", "xxx", "...", "???",
            "不知道", "不会", "没有", "随便", "不清楚", "不太明白",
            "a", "b", "c", "d", "aa", "bb", "cc", "dd"
        };
        
        for (String pattern : invalidPatterns) {
            if (answer.equals(pattern) || answer.contains(pattern.repeat(3))) {
                return true;
            }
        }
        
        // 检查是否是重复字符
        if (answer.length() > 0) {
            char firstChar = answer.charAt(0);
            boolean allSame = true;
            for (char c : answer.toCharArray()) {
                if (c != firstChar && c != ' ') {
                    allSame = false;
                    break;
                }
            }
            if (allSame && answer.length() > 3) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 严格AI评分
     */
    private int getStrictAIScore(String question, String userAnswer, String pdfFileName) {
        String prompt = String.format(
            "你是一个严格的AI评分员，请对以下回答进行严格评分（0-7分）。\n\n" +
            "问题：%s\n" +
            "回答：%s\n\n" +
            "评分标准：\n" +
            "7分：回答完全正确、详细、逻辑清晰\n" +
            "5-6分：回答基本正确，有小缺陷\n" +
            "3-4分：回答部分正确，有明显错误\n" +
            "1-2分：回答大部分错误，但有一定相关性\n" +
            "0分：回答完全错误、无关或无意义\n\n" +
            "请只输出分数数字（不要其他内容）：",
            question, userAnswer
        );
        
        try {
            String result = quizGameChatClient.prompt()
                    .user(prompt)
                    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "file_name == '" + pdfFileName + "'"))
                    .call()
                    .content();
            
            // 提取数字
            String scoreStr = result.trim().replaceAll("\\D", "");
            if (!scoreStr.isEmpty()) {
                int score = Integer.parseInt(scoreStr);
                return Math.max(0, Math.min(7, score)); // 限制在0-7分
            }
        } catch (Exception e) {
            log.warn("AI评分失败", e);
        }
        
        return 0; // 默认为0分
    }
    
    /**
     * 增强的关键词评分
     */
    private int getEnhancedKeywordScore(String question, String userAnswer, String pdfFileName) {
        // 获取相关文档内容
        String context = getRelevantContext(pdfFileName);
        if (context == null || context.trim().isEmpty()) {
            return 0;
        }
        
        // 提取问题和文档的关键词
        String[] questionKeywords = extractKeywords(question);
        String[] contextKeywords = extractKeywords(context);
        String[] answerKeywords = extractKeywords(userAnswer);
        
        int matchCount = 0;
        
        // 检查答案中是否包含问题或文档的关键词
        for (String answerKeyword : answerKeywords) {
            for (String questionKeyword : questionKeywords) {
                if (answerKeyword.length() > 2 && questionKeyword.length() > 2 && 
                    answerKeyword.contains(questionKeyword)) {
                    matchCount++;
                }
            }
            for (String contextKeyword : contextKeywords) {
                if (answerKeyword.length() > 2 && contextKeyword.length() > 2 && 
                    answerKeyword.contains(contextKeyword)) {
                    matchCount++;
                }
            }
        }
        
        // 根据匹配数量计算分数
        if (matchCount >= 3) return 3;
        if (matchCount >= 2) return 2;
        if (matchCount >= 1) return 1;
        
        return 0;
    }
    
    /**
     * 生成详细反馈
     */
    private String generateDetailedFeedback(String question, String userAnswer, int score, String pdfFileName) {
        if (score == 0) {
            return "答案不符合要求或与问题不相关，请仔细阅读文档内容后再回答。";
        } else if (score <= 3) {
            return "答案有一定相关性，但还需要更加准确和详细。";
        } else if (score <= 6) {
            return "答案较好，基本符合要求，但还可以更完善。";
        } else {
            return "优秀的答案！内容准确、详细，完全符合要求。";
        }
    }
    
    /**
     * 计算向量相似度
     */
    private double calculateVectorSimilarity(String question, String userAnswer, String pdfFileName) {
        try {
            // 这里可以实现更复杂的向量相似度计算
            // 简化实现，返回模拟值
            return Math.random() * 0.8 + 0.2; // 0.2-1.0之间
        } catch (Exception e) {
            log.warn("计算向量相似度失败", e);
            return 0.5;
        }
    }
    
    /**
     * AI评分
     */
    private int getAIScore(String question, String userAnswer, String pdfFileName) {
        String prompt = String.format(
            "作为AI评分员，请对以下回答进行评分（0-4分）：\n" +
            "问题：%s\n" +
            "回答：%s\n" +
            "评分标准：准确性、完整性、逻辑性。请只输出分数数字。",
            question, userAnswer
        );
        
        try {
            String result = quizGameChatClient.prompt()
                    .user(prompt)
                    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "file_name == '" + pdfFileName + "'"))
                    .call()
                    .content();
            
            return Integer.parseInt(result.trim());
        } catch (Exception e) {
            log.warn("AI评分失败", e);
            return 2; // 默认分数
        }
    }
    
    /**
     * 关键词匹配评分
     */
    private int getKeywordScore(String question, String userAnswer, String pdfFileName) {
        // 简化实现：基于答案长度和关键词数量
        if (userAnswer.length() < 10) return 0;
        if (userAnswer.length() < 30) return 1;
        return 2;
    }
    
    /**
     * 生成反馈
     */
    private String generateFeedback(String question, String userAnswer, int score, String pdfFileName) {
        String prompt = String.format(
            "作为AI面试官，请对以下回答给出简短评价（50字以内）：\n" +
            "问题：%s\n" +
            "回答：%s\n" +
            "得分：%d/%d\n" +
            "请给出鼓励性的建议。",
            question, userAnswer, score, MAX_SCORE_PER_QUESTION
        );
        
        try {
            return quizGameChatClient.prompt()
                    .user(prompt)
                    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "file_name == '" + pdfFileName + "'"))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("生成反馈失败", e);
            return score >= 7 ? "回答很好，继续保持！" : "还可以更详细一些哦～";
        }
    }
    
    /**
     * 获取相关文档内容
     */
    private String getRelevantContext(String pdfFileName) {
        try {
            // 使用多个关键词检索文档内容
            String[] searchTerms = {"知识", "学习", "内容", "教育", "课程"};
            
            StringBuilder contextBuilder = new StringBuilder();
            for (String term : searchTerms) {
                try {
                    SearchRequest request = SearchRequest.builder()
                            .query(term)
                            .topK(2)
                            .similarityThreshold(0.3) // 降低阈值以获取更多结果
                            .filterExpression("file_name == '" + pdfFileName + "'")
                            .build();
                    
                    List<Document> docs = vectorStore.similaritySearch(request);
                    if (docs != null && !docs.isEmpty()) {
                        for (Document doc : docs) {
                            String content = doc.getText(); // 使用getText()方法
                            if (content != null && content.length() > 50) {
                                contextBuilder.append(content.substring(0, Math.min(200, content.length())))
                                             .append("\n");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("检索关键词 '{}'  失败", term, e);
                }
            }
            
            String result = contextBuilder.toString().trim();
            log.info("检索相关内容: fileName={}, contextLength={}", pdfFileName, result.length());
            return result.isEmpty() ? null : result;
            
        } catch (Exception e) {
            log.warn("获取相关文档内容失败", e);
            return null;
        }
    }
    
    /**
     * 根据轮次确定难度
     */
    private String getDifficultyByRound(int round) {
        if (round <= 3) return "简单";
        if (round <= 7) return "中等";
        return "困难";
    }
    
    /**
     * 检查问题是否超时
     */
    private boolean isQuestionTimeout(QuizGameState gameState) {
        if (gameState.getQuestionStartTime() == null) return false;
        long elapsed = ChronoUnit.SECONDS.between(gameState.getQuestionStartTime(), LocalDateTime.now());
        return elapsed >= QUESTION_TIMEOUT_SECONDS;
    }
    
    /**
     * 处理超时
     */
    private void handleTimeout(QuizGameState gameState) {
        if ("PLAYING".equals(gameState.getStatus())) {
            QuizAnswerRequest timeoutRequest = new QuizAnswerRequest();
            timeoutRequest.setGameId(gameState.getGameId());
            timeoutRequest.setTimeout(true);
            submitAnswer(timeoutRequest);
        }
    }
    
    /**
     * 评分结果
     */
    private static class ScoreResult {
        final int score;
        final String feedback;
        
        ScoreResult(int score, String feedback) {
            this.score = score;
            this.feedback = feedback;
        }
    }
}