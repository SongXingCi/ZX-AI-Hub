<template>
  <div class="quiz-game-container">
    <!-- 游戏头部 -->
    <div class="game-header">
      <div class="game-info">
        <h1>🎯 知识问答小游戏</h1>
        <p class="subtitle">让我来考考你的知识水平吧～</p>
      </div>
      
      <!-- 游戏状态指示器 -->
      <div class="game-status" v-if="gameState">
        <div class="round-info">
          <span class="round">第 {{ gameState.currentRound }}/10 题</span>
          <div class="progress-bar">
            <div class="progress" :style="{ width: (gameState.currentRound / 10) * 100 + '%' }"></div>
          </div>
        </div>
        <div class="score-info">
          <span class="score">得分: {{ gameState.totalScore }}</span>
        </div>
      </div>
    </div>

    <!-- 游戏主体 -->
    <div class="game-body">
      <!-- 等待开始 -->
      <div v-if="!gameState || gameState.status === 'WAITING'" class="waiting-state">
        <div class="upload-area">
          <h3>📚 请先上传PDF文档</h3>
          <p>上传你的学习资料，AI会基于文档内容出题考察你哦～</p>
          <input
            type="file"
            ref="fileInput"
            @change="handleFileUpload"
            accept=".pdf"
            style="display: none"
          />
          <button @click="($refs.fileInput as HTMLInputElement)?.click()" class="upload-btn" :disabled="uploading">
            {{ uploading ? '上传中...' : '选择PDF文件' }}
          </button>
          
          <div v-if="uploadedFile" class="file-info">
            <p>✅ {{ uploadedFile.name }}</p>
            <button @click="startGame" class="start-btn" :disabled="starting">
              {{ starting ? '准备中...' : '开始游戏' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 游戏进行中 -->
      <div v-else-if="gameState.status === 'PLAYING'" class="playing-state">
        <!-- 倒计时器 -->
        <div class="timer">
          <div class="timer-circle" :class="{ 'warning': remainingTime <= 30 }">
            <span class="time">{{ formatTime(remainingTime) }}</span>
          </div>
          <p class="timer-text">剩余时间</p>
        </div>

        <!-- 当前问题 -->
        <div class="question-area">
          <div class="ai-avatar">
            <div class="ai-icon">🧺</div>
          </div>
          <div class="question-bubble">
            <p class="question-prefix">智能AI考官：</p>
            <p class="question-text">{{ gameState.currentQuestion }}</p>
          </div>
        </div>

        <!-- 答案输入 -->
        <div class="answer-area">
          <textarea
            v-model="currentAnswer"
            placeholder="请在这里输入你的答案..."
            class="answer-input"
            :disabled="submitting"
            @keydown.ctrl.enter="submitAnswer"
          ></textarea>
          <button @click="submitAnswer" class="submit-btn" :disabled="!currentAnswer.trim() || submitting">
            {{ submitting ? '提交中...' : '提交答案 (Ctrl+Enter)' }}
          </button>
        </div>
      </div>

      <!-- 游戏结束 -->
      <div v-else-if="gameState.status === 'FINISHED'" class="finished-state">
        <div class="result-card">
          <h2>🎉 游戏结束！</h2>
          <div class="final-score">
            <span class="score-label">最终得分</span>
            <span class="score-value">{{ gameState.totalScore }}/100</span>
          </div>
          
          <div class="score-evaluation">
            <p class="evaluation-text">{{ getScoreEvaluation(gameState.totalScore) }}</p>
          </div>

          <!-- 详细成绩 -->
          <div class="detailed-results">
            <h3>📊 详细成绩</h3>
            <div class="round-results">
              <div 
                v-for="round in gameState.roundScores" 
                :key="round.round"
                class="round-result"
              >
                <div class="round-header">
                  <span class="round-num">第{{ round.round }}题</span>
                  <span class="round-score" :class="getScoreClass(round.score)">
                    {{ round.score }}/10分
                  </span>
                </div>
                <p class="round-question">{{ round.question }}</p>
                <p class="round-answer">你的答案: {{ round.userAnswer || '未回答' }}</p>
                <p class="round-feedback">{{ round.feedback }}</p>
              </div>
            </div>
          </div>

          <div class="action-buttons">
            <button @click="restartGame" class="restart-btn">再来一局</button>
            <button @click="goHome" class="home-btn">返回首页</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 答题历史弹窗 -->
    <div v-if="showHistory" class="history-modal" @click="showHistory = false">
      <div class="history-content" @click.stop>
        <h3>📝 答题历史</h3>
        <div class="history-list">
          <div v-for="round in gameState?.roundScores || []" :key="round.round" class="history-item">
            <div class="history-header">
              <span>第{{ round.round }}题 - {{ round.score }}/10分</span>
              <span class="timestamp">{{ round.timestamp }}</span>
            </div>
            <p class="history-question">{{ round.question }}</p>
            <p class="history-feedback">{{ round.feedback }}</p>
          </div>
        </div>
        <button @click="showHistory = false" class="close-btn">关闭</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'

// 类型定义
interface GameState {
  gameId: string
  currentRound: number
  totalScore: number
  status: 'WAITING' | 'PLAYING' | 'FINISHED' | 'WAITING_NEXT'
  currentQuestion?: string
  questionStartTime?: string
  remainingTime?: number
  roundScores: RoundScore[]
  pdfFileName?: string
}

interface RoundScore {
  round: number
  question: string
  userAnswer: string
  score: number
  feedback: string
  timeout: boolean
  timestamp?: string
}

const router = useRouter()

// 响应式数据
const gameState = ref<GameState | null>(null)
const uploadedFile = ref<File | null>(null)
const currentAnswer = ref('')
const remainingTime = ref(180) // 3分钟
const uploading = ref(false)
const starting = ref(false)
const submitting = ref(false)
const showHistory = ref(false)

// 定时器
let countdownTimer: number | null = null
let stateCheckTimer: number | null = null

// API基础URL
const BASE_URL = 'http://localhost:8080'

// 计算属性
const formatTime = (seconds: number) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

// 方法
const handleFileUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file || file.type !== 'application/pdf') {
    alert('请选择PDF文件')
    return
  }

  uploadedFile.value = file
  uploading.value = true

  try {
    // 生成临时chatId用于上传
    const chatId = Date.now().toString()
    const formData = new FormData()
    formData.append('file', file)

    const response = await fetch(`${BASE_URL}/ai/pdf/upload/${chatId}`, {
      method: 'POST',
      body: formData
    })

    if (!response.ok) {
      throw new Error('文件上传失败')
    }

    const result = await response.json()
    if (result.code !== 200) {
      throw new Error(result.message || '文件上传失败')
    }

    // 保存chatId以便后续使用
    sessionStorage.setItem('pdfChatId', chatId)
    
  } catch (error) {
    console.error('上传失败:', error)
    alert('文件上传失败，请重试')
    uploadedFile.value = null
  } finally {
    uploading.value = false
  }
}

const startGame = async () => {
  if (!uploadedFile.value) return
  
  starting.value = true
  try {
    const pdfChatId = sessionStorage.getItem('pdfChatId')
    if (!pdfChatId) {
      throw new Error('请重新上传文件')
    }

    const response = await fetch(`${BASE_URL}/ai/quiz/start/${pdfChatId}`, {
      method: 'POST'
    })

    if (!response.ok) {
      throw new Error('开始游戏失败')
    }

    const result = await response.json()
    if (result.code !== 200) {
      throw new Error(result.message || '开始游戏失败')
    }

    gameState.value = result.data
    
    // 生成第一题
    await generateNextQuestion()
    
  } catch (error) {
    console.error('开始游戏失败:', error)
    alert('开始游戏失败，请重试')
  } finally {
    starting.value = false
  }
}

const generateNextQuestion = async () => {
  if (!gameState.value) return
  
  try {
    console.log('生成下一题:', gameState.value.gameId, '当前状态:', gameState.value.status)
    
    const response = await fetch(`${BASE_URL}/ai/quiz/next/${gameState.value.gameId}`, {
      method: 'POST'
    })

    if (!response.ok) {
      throw new Error('生成问题失败')
    }

    const result = await response.json()
    console.log('生成问题的响应:', result)
    
    if (result.code !== 200) {
      throw new Error(result.message || '生成问题失败')
    }

    gameState.value = result.data
    currentAnswer.value = ''
    remainingTime.value = 180
    
    console.log('更新后的游戏状态:', gameState.value)
    
    // 开始倒计时
    startCountdown()
    
  } catch (error) {
    console.error('生成问题失败:', error)
    alert('生成问题失败，请重试')
  }
}

const submitAnswer = async () => {
  if (!gameState.value || !currentAnswer.value.trim()) return
  
  submitting.value = true
  stopCountdown()
  
  try {
    console.log('提交答案:', gameState.value.gameId, '答案:', currentAnswer.value)
    
    const response = await fetch(`${BASE_URL}/ai/quiz/answer`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        gameId: gameState.value.gameId,
        answer: currentAnswer.value,
        timeout: false
      })
    })

    if (!response.ok) {
      throw new Error('提交答案失败')
    }

    const result = await response.json()
    console.log('提交答案的响应:', result)
    
    if (result.code !== 200) {
      throw new Error(result.message || '提交答案失败')
    }

    gameState.value = result.data
    console.log('提交后的游戏状态:', gameState.value)
    
    // 如果游戏状态为WAITING_NEXT，0.3秒后生成下一题
    if (gameState.value && gameState.value.status === 'WAITING_NEXT') {
      console.log('0.3秒后生成下一题...')
      setTimeout(() => {
        generateNextQuestion()
      }, 300)
    }
    
  } catch (error) {
    console.error('提交答案失败:', error)
    alert('提交答案失败，请重试')
  } finally {
    submitting.value = false
  }
}

const startCountdown = () => {
  stopCountdown()
  countdownTimer = setInterval(() => {
    remainingTime.value--
    if (remainingTime.value <= 0) {
      // 时间到，自动提交
      handleTimeout()
    }
  }, 1000) as unknown as number
}

const stopCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

const handleTimeout = async () => {
  if (!gameState.value) return
  
  stopCountdown()
  submitting.value = true
  
  try {
    const response = await fetch(`${BASE_URL}/ai/quiz/answer`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        gameId: gameState.value.gameId,
        answer: currentAnswer.value,
        timeout: true
      })
    })

    if (!response.ok) {
      throw new Error('提交超时答案失败')
    }

    const result = await response.json()
    gameState.value = result.data
    
    // 如果游戏状态为WAITING_NEXT，0.3秒后生成下一题
    if (gameState.value && gameState.value.status === 'WAITING_NEXT') {
      setTimeout(() => {
        generateNextQuestion()
      }, 300)
    }
    
  } catch (error) {
    console.error('处理超时失败:', error)
  } finally {
    submitting.value = false
  }
}

const getScoreEvaluation = (score: number): string => {
  if (score >= 90) return '🏆 学霸级别！知识掌握得非常扎实！'
  if (score >= 80) return '🌟 优秀！大部分知识都掌握得很好！'
  if (score >= 70) return '👍 良好！还有进步空间哦～'
  if (score >= 60) return '📚 及格！继续加油学习吧！'
  return '💪 不要气馁，多多学习就会进步的！'
}

const getScoreClass = (score: number): string => {
  if (score >= 8) return 'high-score'
  if (score >= 5) return 'medium-score'
  return 'low-score'
}

const restartGame = () => {
  gameState.value = null
  uploadedFile.value = null
  currentAnswer.value = ''
  remainingTime.value = 180
  stopCountdown()
  sessionStorage.removeItem('pdfChatId')
}

const goHome = () => {
  router.push('/')
}

// 生命周期
onMounted(() => {
  // 检查是否有之前的游戏状态
  // 这里可以从sessionStorage恢复状态
})

onUnmounted(() => {
  stopCountdown()
  if (stateCheckTimer) {
    clearInterval(stateCheckTimer)
  }
})
</script>

<style scoped lang="scss">
.quiz-game-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
  color: white;
}

.game-header {
  text-align: center;
  margin-bottom: 30px;
  
  .game-info {
    h1 {
      font-size: 2.5rem;
      margin-bottom: 10px;
      text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
    }
    
    .subtitle {
      font-size: 1.2rem;
      opacity: 0.9;
    }
  }
  
  .game-status {
    display: flex;
    justify-content: space-between;
    align-items: center;
    max-width: 600px;
    margin: 20px auto;
    padding: 15px;
    background: rgba(255,255,255,0.1);
    border-radius: 15px;
    backdrop-filter: blur(10px);
    
    .round-info {
      flex: 1;
      
      .round {
        display: block;
        font-weight: bold;
        margin-bottom: 5px;
      }
      
      .progress-bar {
        width: 100%;
        height: 8px;
        background: rgba(255,255,255,0.2);
        border-radius: 4px;
        overflow: hidden;
        
        .progress {
          height: 100%;
          background: linear-gradient(90deg, #4facfe, #00f2fe);
          transition: width 0.3s ease;
        }
      }
    }
    
    .score-info {
      .score {
        font-size: 1.5rem;
        font-weight: bold;
        color: #ffd700;
      }
    }
  }
}

.game-body {
  max-width: 800px;
  margin: 0 auto;
}

.waiting-state {
  text-align: center;
  
  .upload-area {
    background: rgba(255,255,255,0.1);
    border-radius: 20px;
    padding: 40px;
    backdrop-filter: blur(10px);
    
    h3 {
      font-size: 1.8rem;
      margin-bottom: 15px;
    }
    
    p {
      margin-bottom: 30px;
      opacity: 0.9;
    }
    
    .upload-btn, .start-btn {
      padding: 12px 30px;
      font-size: 1.1rem;
      border: none;
      border-radius: 25px;
      cursor: pointer;
      transition: all 0.3s ease;
      
      &:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
    }
    
    .upload-btn {
      background: linear-gradient(45deg, #4facfe, #00f2fe);
      color: white;
      
      &:hover:not(:disabled) {
        transform: translateY(-2px);
        box-shadow: 0 5px 15px rgba(79,172,254,0.4);
      }
    }
    
    .start-btn {
      background: linear-gradient(45deg, #43e97b, #38f9d7);
      color: white;
      margin-top: 20px;
      
      &:hover:not(:disabled) {
        transform: translateY(-2px);
        box-shadow: 0 5px 15px rgba(67,233,123,0.4);
      }
    }
    
    .file-info {
      margin-top: 20px;
      padding: 15px;
      background: rgba(255,255,255,0.1);
      border-radius: 10px;
    }
  }
}

.playing-state {
  .timer {
    text-align: center;
    margin-bottom: 30px;
    
    .timer-circle {
      width: 120px;
      height: 120px;
      border: 4px solid rgba(255,255,255,0.3);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 10px;
      background: rgba(255,255,255,0.1);
      transition: all 0.3s ease;
      
      &.warning {
        border-color: #ff6b6b;
        animation: pulse 1s infinite;
      }
      
      .time {
        font-size: 1.8rem;
        font-weight: bold;
      }
    }
    
    .timer-text {
      opacity: 0.8;
    }
  }
  
  .question-area {
    display: flex;
    align-items: flex-start;
    margin-bottom: 30px;
    
    .ai-avatar {
      width: 60px;
      height: 60px;
      border-radius: 50%;
      overflow: hidden;
      margin-right: 15px;
      flex-shrink: 0;
      
      .ai-icon {
        width: 100%;
        height: 100%;
        border-radius: 50%;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 28px;
        color: white;
        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      }
    }
    
    .question-bubble {
      flex: 1;
      background: rgba(255,255,255,0.15);
      border-radius: 20px;
      padding: 20px;
      position: relative;
      backdrop-filter: blur(10px);
      
      &::before {
        content: '';
        position: absolute;
        left: -10px;
        top: 20px;
        border: 10px solid transparent;
        border-right-color: rgba(255,255,255,0.15);
      }
      
      .question-prefix {
        font-size: 1rem;
        font-weight: bold;
        color: #ffd700;
        margin: 0 0 8px 0;
      }
      
      .question-text {
        font-size: 1.2rem;
        line-height: 1.6;
        margin: 0;
      }
    }
  }
  
  .answer-area {
    .answer-input {
      width: 100%;
      min-height: 120px;
      padding: 15px;
      border: none;
      border-radius: 15px;
      font-size: 1.1rem;
      background: rgba(255,255,255,0.1);
      color: white;
      backdrop-filter: blur(10px);
      resize: vertical;
      margin-bottom: 15px;
      
      &::placeholder {
        color: rgba(255,255,255,0.6);
      }
      
      &:focus {
        outline: none;
        background: rgba(255,255,255,0.15);
      }
    }
    
    .submit-btn {
      width: 100%;
      padding: 15px;
      font-size: 1.2rem;
      border: none;
      border-radius: 15px;
      background: linear-gradient(45deg, #43e97b, #38f9d7);
      color: white;
      cursor: pointer;
      transition: all 0.3s ease;
      
      &:hover:not(:disabled) {
        transform: translateY(-2px);
        box-shadow: 0 5px 15px rgba(67,233,123,0.4);
      }
      
      &:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
    }
  }
}

.finished-state {
  .result-card {
    background: rgba(255,255,255,0.1);
    border-radius: 20px;
    padding: 40px;
    text-align: center;
    backdrop-filter: blur(10px);
    
    h2 {
      font-size: 2.5rem;
      margin-bottom: 30px;
    }
    
    .final-score {
      margin-bottom: 20px;
      
      .score-label {
        display: block;
        font-size: 1.2rem;
        opacity: 0.8;
        margin-bottom: 10px;
      }
      
      .score-value {
        font-size: 3rem;
        font-weight: bold;
        color: #ffd700;
        text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
      }
    }
    
    .score-evaluation {
      margin-bottom: 30px;
      
      .evaluation-text {
        font-size: 1.3rem;
        font-weight: 500;
      }
    }
    
    .detailed-results {
      text-align: left;
      margin-bottom: 30px;
      
      h3 {
        text-align: center;
        margin-bottom: 20px;
      }
      
      .round-results {
        max-height: 400px;
        overflow-y: auto;
        
        .round-result {
          background: rgba(255,255,255,0.05);
          border-radius: 10px;
          padding: 15px;
          margin-bottom: 15px;
          
          .round-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
            
            .round-num {
              font-weight: bold;
            }
            
            .round-score {
              font-weight: bold;
              
              &.high-score { color: #4ade80; }
              &.medium-score { color: #fbbf24; }
              &.low-score { color: #f87171; }
            }
          }
          
          .round-question {
            font-weight: 500;
            margin-bottom: 5px;
          }
          
          .round-answer {
            opacity: 0.8;
            margin-bottom: 5px;
          }
          
          .round-feedback {
            font-style: italic;
            opacity: 0.7;
          }
        }
      }
    }
    
    .action-buttons {
      display: flex;
      gap: 15px;
      justify-content: center;
      
      button {
        padding: 12px 30px;
        font-size: 1.1rem;
        border: none;
        border-radius: 25px;
        cursor: pointer;
        transition: all 0.3s ease;
      }
      
      .restart-btn {
        background: linear-gradient(45deg, #4facfe, #00f2fe);
        color: white;
        
        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 5px 15px rgba(79,172,254,0.4);
        }
      }
      
      .home-btn {
        background: rgba(255,255,255,0.2);
        color: white;
        
        &:hover {
          background: rgba(255,255,255,0.3);
        }
      }
    }
  }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

@media (max-width: 768px) {
  .quiz-game-container {
    padding: 10px;
  }
  
  .game-header .game-info h1 {
    font-size: 2rem;
  }
  
  .game-status {
    flex-direction: column;
    gap: 15px;
  }
  
  .question-area {
    flex-direction: column;
    
    .ai-avatar {
      align-self: center;
      margin-bottom: 15px;
      margin-right: 0;
    }
    
    .question-bubble::before {
      display: none;
    }
  }
  
  .finished-state .action-buttons {
    flex-direction: column;
  }
}
</style>