package com.itheima.ai.controller;

import com.itheima.ai.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class CustomerServiceController {

    private final ChatClient serviceChatClient;

    private final ChatHistoryRepository chatHistoryRepository;
    @RequestMapping(value = "/service",produces = "text/html;charset=utf-8")
    public Flux<String> service(String prompt,String chatId){
        //1.保存会话ID
        chatHistoryRepository.save("service",chatId);
        //2.请求模型
        return serviceChatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY,chatId))//设置会话ID
                .stream()
                .content();
    }
}

