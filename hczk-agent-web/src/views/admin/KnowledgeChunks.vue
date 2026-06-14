<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApiStore } from '@/stores/api'
import { ArrowLeft, Eye, ThumbsUp, X, Trash2, CheckCircle2, AlertCircle, Database, Search, Clock } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const api = useApiStore()

const collectionName = ref(decodeURIComponent(route.params.collectionName))
const loading = ref(false)
const chunks = ref([])
const searchKw = ref('')
const typeFilter = ref('')
const sourceFilter = ref('')
const showDetail = ref(null)

// ---- Adopt dialog ----
const adoptChunkId = ref('')
const showAdoptDialog = ref(false)
const adoptLoading = ref(false)

onMounted(loadChunks)

async function loadChunks() {
  loading.value = true
  // Extract agentId from kb_{agentId}_{collectionName}
  const parts = collectionName.value.replace('kb_', '').split('_')
  const agentId = parts[0]
  const collPart = parts.slice(1).join('_')

  let url = `/knowledge/collections/${collPart}/chunks?agentId=${agentId}&limit=500`
  if (typeFilter.value) url += `&chunkType=${typeFilter.value}`
  if (sourceFilter.value) url += `&source=${sourceFilter.value}`

  const res = await api.get(url)
  if (res.code === 200) chunks.value = res.data || []
  loading.value = false
}

async function doAdopt() {
  if (!adoptChunkId.value) return
  adoptLoading.value = true
  const parts = collectionName.value.replace('kb_', '').split('_')
  const agentId = parts[0]
  const collPart = parts.slice(1).join('_')

  const res = await api.post(`/knowledge/chunks/${adoptChunkId.value}/adopt`, {
    agentId: agentId,
    collection: collPart
  })
  if (res.code === 200) {
    showAdoptDialog.value = false
    loadChunks()
  } else {
    alert(res.message || '操作失败')
  }
  adoptLoading.value = false
}

function openAdopt(chunkId) {
  adoptChunkId.value = chunkId
  showAdoptDialog.value = true
}

const filteredChunks = computed(() => {
  if (!searchKw.value) return chunks.value
  const kw = searchKw.value.toLowerCase()
  return chunks.value.filter(c =>
    (c.content && c.content.toLowerCase().includes(kw)) ||
    (c.title && c.title.toLowerCase().includes(kw)) ||
    (c.question && c.question.toLowerCase().includes(kw))
  )
})

function goBack() {
  router.push('/admin/knowledge')
}

function fmtTime(ts) {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN')
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Header -->
    <div class="flex items-center gap-4">
      <button @click="goBack" class="p-2 rounded-lg bg-white/5 text-slate-300 hover:bg-white/10 transition-all">
        <ArrowLeft class="w-5 h-5" />
      </button>
      <div>
        <h1 class="text-xl font-bold text-white font-mono">{{ collectionName }}</h1>
        <p class="text-sm text-slate-400 mt-0.5">知识分块浏览 · {{ chunks.length }} 条</p>
      </div>
    </div>

    <!-- Filters -->
    <div class="glass-card p-4 flex flex-wrap items-center gap-4">
      <div class="flex items-center gap-2 flex-1 min-w-[200px]">
        <Search class="w-4 h-4 text-slate-500 shrink-0" />
        <input v-model="searchKw" class="bg-transparent border-none outline-none text-slate-300 flex-1 text-sm" placeholder="搜索内容 / 标题 / 问题..." />
      </div>
      <select v-model="typeFilter" @change="loadChunks" class="input-field w-auto text-sm">
        <option value="">全部类型</option>
        <option value="prose">prose</option>
        <option value="qa">qa</option>
      </select>
      <input v-model="sourceFilter" @keyup.enter="loadChunks" class="input-field w-32 text-sm" placeholder="来源筛选" />
      <button @click="loadChunks" class="btn-secondary text-sm">筛选</button>
      <button @click="loadChunks" class="btn-secondary text-sm flex items-center gap-1">
        <Clock class="w-3 h-3" /> 刷新
      </button>
    </div>

    <!-- Chunks List -->
    <div v-if="loading" class="text-slate-500 py-12 text-center">加载中...</div>
    <div v-else-if="filteredChunks.length === 0" class="glass-card p-12 text-center text-slate-500">
      <Database class="w-10 h-10 mx-auto mb-2 text-slate-600" />
      <p>暂无分块数据</p>
    </div>

    <div v-else class="space-y-2">
      <div v-for="chunk in filteredChunks" :key="chunk.id" class="glass-card p-4 hover:border-white/10 transition-all">
        <div class="flex items-start justify-between gap-4">
          <div class="flex-1 min-w-0">
            <!-- Header row -->
            <div class="flex items-center gap-2 mb-2 flex-wrap">
              <span class="px-1.5 py-0.5 text-xs rounded bg-white/5 text-slate-400 font-mono">{{ chunk.id?.substring(0, 12) }}...</span>
              <span :class="['px-1.5 py-0.5 text-xs rounded', chunk.chunkType === 'qa' ? 'bg-purple-500/10 text-purple-400' : 'bg-blue-500/10 text-blue-400']">
                {{ chunk.chunkType }}
              </span>
              <span class="text-xs text-slate-500">#{{ chunk.chunkIndex }}</span>
              <span class="text-xs text-slate-500">采纳 {{ chunk.adoptCount }} · 命中 {{ chunk.hitCount }}</span>
              <span class="text-xs" :class="chunk.relevanceScore >= 1 ? 'text-emerald-400' : 'text-slate-500'">评分 {{ chunk.relevanceScore?.toFixed(2) }}</span>
            </div>
            <!-- Question (for QA) -->
            <div v-if="chunk.question" class="mb-2 p-2 rounded bg-amber-500/5 border border-amber-500/10">
              <span class="text-xs text-amber-400 font-medium">Q: </span>
              <span class="text-sm text-slate-300">{{ chunk.question }}</span>
            </div>
            <!-- Content -->
            <p :class="['text-sm text-slate-300 whitespace-pre-wrap', showDetail === chunk.id ? '' : 'line-clamp-3']">
              {{ chunk.content }}
            </p>
            <!-- Meta -->
            <div class="flex flex-wrap items-center gap-3 mt-2 text-xs text-slate-500">
              <span v-if="chunk.title">标题: {{ chunk.title }}</span>
              <span v-if="chunk.source">来源: {{ chunk.source }}</span>
              <span>入库: {{ fmtTime(chunk.ingestTime) }}</span>
            </div>
          </div>
          <!-- Actions -->
          <div class="flex items-center gap-1 shrink-0">
            <button @click="showDetail = showDetail === chunk.id ? null : chunk.id"
              class="p-1.5 rounded text-slate-500 hover:text-white hover:bg-white/5 transition-all" title="展开/收起">
              {{ showDetail === chunk.id ? '收起' : '展开' }}
            </button>
            <button @click="openAdopt(chunk.id)"
              class="p-1.5 rounded text-slate-500 hover:text-emerald-400 hover:bg-emerald-500/5 transition-all" title="采纳">
              <ThumbsUp class="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Adopt Dialog -->
    <div v-if="showAdoptDialog" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="glass-card w-full max-w-sm p-6 animate-slide-up">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-bold text-white">确认采纳</h2>
          <button @click="showAdoptDialog = false" class="p-1 text-slate-400 hover:text-white">
            <X class="w-5 h-5" />
          </button>
        </div>
        <p class="text-sm text-slate-400 mb-2">确定将此知识块标记为已采纳？这将增加该块的采纳计数。</p>
        <p class="text-xs text-slate-500 font-mono mb-4">{{ adoptChunkId }}</p>
        <div class="flex gap-3">
          <button @click="showAdoptDialog = false" class="btn-secondary flex-1">取消</button>
          <button @click="doAdopt" :disabled="adoptLoading" class="btn-primary flex-1">
            {{ adoptLoading ? '处理中...' : '确认采纳' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
