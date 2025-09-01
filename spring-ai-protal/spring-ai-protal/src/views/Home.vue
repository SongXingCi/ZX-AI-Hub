<template>
  <div class="home" :class="{ 'dark': isDark }">
    <div class="container">
      <h1 class="title">
        智学 AI 应用中心
        <div class="title-decoration">
          <div class="floating-cube"></div>
          <div class="floating-sphere"></div>
          <div class="floating-pyramid"></div>
        </div>
      </h1>
      <div class="cards-grid">
        <router-link 
          v-for="app in aiApps" 
          :key="app.id"
          :to="app.route"
          class="card"
          :class="`card-${app.id}`"
        >
          <div class="card-background"></div>
          <div class="card-glow"></div>
          <div class="card-content">
            <div class="icon-container">
              <component :is="app.icon" class="icon" :class="app.iconClass" />
              <div class="icon-backdrop"></div>
            </div>
            <h2>{{ app.title }}</h2>
            <p>{{ app.description }}</p>
            <div class="card-decoration">
              <div class="floating-dot"></div>
              <div class="floating-ring"></div>
            </div>
          </div>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useDark } from '@vueuse/core'
import { 
  ChatBubbleLeftRightIcon,
  HeartIcon,
  UserGroupIcon,
  DocumentTextIcon,
  AcademicCapIcon
} from '@heroicons/vue/24/outline'

const isDark = useDark()

const aiApps = ref([
  {
    id: 1,
    title: 'AI 聊天',
    description: '多模态对话机器人，支持图片、音频等',
    route: '/ai-chat',
    icon: ChatBubbleLeftRightIcon
  },
  {
    id: 2,
    title: '哄哄模拟器',
    description: '一个帮助你练习哄女朋友开心的小游戏',
    route: '/game',
    icon: HeartIcon,
    iconClass: 'heart-icon'
  },
  {
    id: 3,
    title: '智学智能客服',
    description: '24小时在线的智能课程咨询师',
    route: '/customer-service',
    icon: UserGroupIcon
  },
  {
    id: 4,
    title: 'ChatPDF',
    description: '打造你的个人知识库，与知识库自由对话',
    route: '/chat-pdf',
    icon: DocumentTextIcon
  },
  {
    id: 5,
    title: '知识问答游戏',
    description: '基于PDF知识的问答小游戏，考验你的学习成果',
    route: '/quiz-game',
    icon: AcademicCapIcon,
    iconClass: 'quiz-icon'
  }
])
</script>

<style scoped lang="scss">
.home {
  min-height: 100vh;
  padding: 2rem;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: 
      radial-gradient(circle at 25% 25%, rgba(245, 158, 11, 0.1) 0%, transparent 50%),
      radial-gradient(circle at 75% 75%, rgba(139, 92, 246, 0.1) 0%, transparent 50%),
      radial-gradient(circle at 50% 50%, rgba(6, 182, 212, 0.08) 0%, transparent 50%);
    pointer-events: none;
    animation: backgroundShift 15s ease-in-out infinite;
  }

  .container {
    max-width: 1600px;
    margin: 0 auto;
    padding: 0 2rem;
    position: relative;
    z-index: 1;
  }

  .title {
    text-align: center;
    font-size: clamp(2.5rem, 5vw, 4rem);
    font-weight: 800;
    margin-bottom: 4rem;
    background: linear-gradient(135deg, #f59e0b, #8b5cf6, #06b6d4);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    position: relative;
    animation: titleGlow 3s ease-in-out infinite;
    
    .title-decoration {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 100%;
      height: 100%;
      pointer-events: none;
      
      .floating-cube {
        position: absolute;
        width: 20px;
        height: 20px;
        background: linear-gradient(45deg, #f59e0b, #fb923c);
        top: -10px;
        right: 20%;
        transform: rotateX(45deg) rotateY(45deg);
        animation: floatCube 4s ease-in-out infinite;
        border-radius: 4px;
        box-shadow: 0 10px 20px rgba(245, 158, 11, 0.3);
      }
      
      .floating-sphere {
        position: absolute;
        width: 16px;
        height: 16px;
        background: radial-gradient(circle, #8b5cf6, #7c3aed);
        border-radius: 50%;
        top: -20px;
        left: 15%;
        animation: floatSphere 3s ease-in-out infinite reverse;
        box-shadow: 0 8px 16px rgba(139, 92, 246, 0.4);
      }
      
      .floating-pyramid {
        position: absolute;
        width: 0;
        height: 0;
        border-left: 8px solid transparent;
        border-right: 8px solid transparent;
        border-bottom: 14px solid #06b6d4;
        bottom: -20px;
        right: 25%;
        animation: floatPyramid 5s ease-in-out infinite;
        filter: drop-shadow(0 6px 12px rgba(6, 182, 212, 0.3));
      }
    }
  }

  .cards-grid {
    display: grid;
    grid-template-columns: repeat(1, 1fr);
    gap: 3rem;
    justify-items: center;
    padding: 2rem;
    perspective: 1000px;

    @media (min-width: 768px) {
      grid-template-columns: repeat(2, 1fr);
    }
    
    @media (min-width: 1200px) {
      grid-template-columns: repeat(5, 1fr);
    }
  }

  .card {
    position: relative;
    width: 100%;
    max-width: 320px;
    height: 280px;
    text-decoration: none;
    color: inherit;
    transform-style: preserve-3d;
    transition: all 0.4s cubic-bezier(0.23, 1, 0.320, 1);
    border-radius: 24px;
    overflow: hidden;
    
    .card-background {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(255, 255, 255, 0.08);
      backdrop-filter: blur(20px);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 24px;
      z-index: 1;
    }
    
    .card-glow {
      position: absolute;
      top: -2px;
      left: -2px;
      right: -2px;
      bottom: -2px;
      border-radius: 26px;
      background: linear-gradient(135deg, 
        rgba(245, 158, 11, 0.2), 
        rgba(139, 92, 246, 0.2), 
        rgba(6, 182, 212, 0.2)
      );
      opacity: 0;
      transition: opacity 0.4s ease;
      z-index: 0;
    }

    &:hover {
      transform: translateY(-12px) rotateX(5deg) rotateY(5deg);
      
      .card-glow {
        opacity: 1;
      }
      
      .card-background {
        background: rgba(255, 255, 255, 0.12);
      }
      
      .icon {
        transform: scale(1.1) rotateY(10deg);
      }
      
      .floating-dot {
        animation-play-state: running;
      }
      
      .floating-ring {
        animation-play-state: running;
      }
    }

    .card-content {
      position: relative;
      z-index: 2;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      padding: 2rem;
      height: 100%;
      justify-content: center;
    }

    .icon-container {
      position: relative;
      margin-bottom: 1.5rem;
      
      .icon-backdrop {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        width: 80px;
        height: 80px;
        background: radial-gradient(circle, rgba(255, 255, 255, 0.1), transparent);
        border-radius: 50%;
        z-index: -1;
      }
    }

    .icon {
      width: 56px;
      height: 56px;
      transition: all 0.4s cubic-bezier(0.23, 1, 0.320, 1);
      filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.2));
      
      &.heart-icon {
        color: #f59e0b;
        animation: heartPulse 2s ease-in-out infinite;
      }
      
      &.quiz-icon {
        color: #06b6d4;
        animation: quizBounce 3s ease-in-out infinite;
      }
      
      // 默认颜色渐变
      &:not(.heart-icon):not(.quiz-icon) {
        color: #8b5cf6;
      }
    }

    h2 {
      font-size: 1.4rem;
      font-weight: 700;
      margin-bottom: 0.75rem;
      color: var(--text-color);
    }

    p {
      color: rgba(248, 250, 252, 0.7);
      font-size: 0.95rem;
      line-height: 1.5;
      .dark & {
        color: rgba(241, 245, 249, 0.6);
      }
    }
    
    .card-decoration {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      pointer-events: none;
      
      .floating-dot {
        position: absolute;
        width: 8px;
        height: 8px;
        background: #f59e0b;
        border-radius: 50%;
        top: 20px;
        right: 20px;
        animation: floatDot 3s ease-in-out infinite;
        animation-play-state: paused;
      }
      
      .floating-ring {
        position: absolute;
        width: 24px;
        height: 24px;
        border: 2px solid #06b6d4;
        border-radius: 50%;
        bottom: 20px;
        left: 20px;
        animation: floatRing 4s ease-in-out infinite;
        animation-play-state: paused;
      }
    }
    
    // 不同卡片的特殊样式
    &.card-1 {
      .card-glow {
        background: linear-gradient(135deg, 
          rgba(139, 92, 246, 0.3), 
          rgba(167, 139, 250, 0.2)
        );
      }
    }
    
    &.card-2 {
      .card-glow {
        background: linear-gradient(135deg, 
          rgba(245, 158, 11, 0.3), 
          rgba(251, 191, 36, 0.2)
        );
      }
    }
    
    &.card-3 {
      .card-glow {
        background: linear-gradient(135deg, 
          rgba(6, 182, 212, 0.3), 
          rgba(34, 197, 94, 0.2)
        );
      }
    }
    
    &.card-4 {
      .card-glow {
        background: linear-gradient(135deg, 
          rgba(236, 72, 153, 0.3), 
          rgba(244, 114, 182, 0.2)
        );
      }
    }
    
    &.card-5 {
      .card-glow {
        background: linear-gradient(135deg, 
          rgba(34, 197, 94, 0.3), 
          rgba(74, 222, 128, 0.2)
        );
      }
    }
  }
}

// 动画定义
@keyframes backgroundShift {
  0%, 100% {
    transform: translateX(0) translateY(0);
  }
  25% {
    transform: translateX(20px) translateY(-10px);
  }
  50% {
    transform: translateX(-15px) translateY(15px);
  }
  75% {
    transform: translateX(10px) translateY(-20px);
  }
}

@keyframes titleGlow {
  0%, 100% {
    filter: drop-shadow(0 0 20px rgba(139, 92, 246, 0.3));
  }
  50% {
    filter: drop-shadow(0 0 30px rgba(245, 158, 11, 0.4));
  }
}

@keyframes floatCube {
  0%, 100% {
    transform: rotateX(45deg) rotateY(45deg) translateY(0);
  }
  50% {
    transform: rotateX(45deg) rotateY(45deg) translateY(-10px);
  }
}

@keyframes floatSphere {
  0%, 100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-8px) scale(1.1);
  }
}

@keyframes floatPyramid {
  0%, 100% {
    transform: translateY(0) rotateZ(0deg);
  }
  50% {
    transform: translateY(-6px) rotateZ(10deg);
  }
}

@keyframes heartPulse {
  0%, 100% {
    transform: scale(1);
    filter: drop-shadow(0 0 10px rgba(245, 158, 11, 0.3));
  }
  50% {
    transform: scale(1.1);
    filter: drop-shadow(0 0 20px rgba(245, 158, 11, 0.5));
  }
}

@keyframes quizBounce {
  0%, 100% {
    transform: translateY(0);
  }
  25% {
    transform: translateY(-4px);
  }
  50% {
    transform: translateY(-2px);
  }
  75% {
    transform: translateY(-6px);
  }
}

@keyframes floatDot {
  0%, 100% {
    transform: translateY(0) scale(1);
    opacity: 0.6;
  }
  50% {
    transform: translateY(-8px) scale(1.2);
    opacity: 1;
  }
}

@keyframes floatRing {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
    opacity: 0.5;
  }
  50% {
    transform: translateY(-6px) rotate(180deg);
    opacity: 0.8;
  }
}

// 响应式设计
@media (max-width: 768px) {
  .home {
    padding: 1rem;
    
    .container {
      padding: 0 1rem;
    }
    
    .title {
      font-size: 2.5rem;
      margin-bottom: 3rem;
    }

    .cards-grid {
      gap: 2rem;
      padding: 1rem;
    }

    .card {
      max-width: 100%;
      height: 260px;
      
      .card-content {
        padding: 1.5rem;
      }
      
      .icon {
        width: 48px;
        height: 48px;
      }
    }
  }
}

@media (max-width: 480px) {
  .home {
    .title {
      font-size: 2rem;
    }
    
    .card {
      height: 240px;
      
      h2 {
        font-size: 1.2rem;
      }
      
      p {
        font-size: 0.9rem;
      }
    }
  }
}
</style> 