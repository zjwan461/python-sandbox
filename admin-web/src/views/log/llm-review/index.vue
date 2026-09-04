<template>
  <div class="llm-review-container">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="query.taskStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="处理中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="大模型判定">
          <el-select v-model="query.llmLabel" placeholder="全部" clearable style="width: 120px">
            <el-option label="安全" value="SAFE" />
            <el-option label="危险" value="DANGEROUS" />
          </el-select>
        </el-form-item>
        <el-form-item label="人工复核">
          <el-select v-model="query.humanReviewStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="未复核" value="" />
            <el-option label="同意" value="AGREED" />
            <el-option label="不同意" value="DISAGREED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleExportJsonl">导出 JSONL</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="detectLogId" label="检测记录ID" width="110" />
        <el-table-column prop="taskStatus" label="任务状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.taskStatus)">{{ getStatusText(row.taskStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="smallModelLabel" label="小模型判定" width="100">
          <template #default="{ row }">
            <el-tag :type="row.smallModelLabel === 'DANGEROUS' ? 'danger' : 'success'" size="small">
              {{ row.smallModelLabel === 'DANGEROUS' ? '危险' : '安全' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="llmLabel" label="大模型判定" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.llmLabel" :type="row.llmLabel === 'DANGEROUS' ? 'danger' : 'success'" size="small">
              {{ row.llmLabel === 'DANGEROUS' ? '危险' : '安全' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="humanLabel" label="人工判定" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.humanLabel" :type="row.humanLabel === 'DANGEROUS' ? 'danger' : 'success'" size="small">
              {{ row.humanLabel === 'DANGEROUS' ? '危险' : '安全' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="humanReviewStatus" label="复核状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.humanReviewStatus" :type="row.humanReviewStatus === 'AGREED' ? 'success' : 'warning'" size="small">
              {{ row.humanReviewStatus === 'AGREED' ? '同意' : '不同意' }}
            </el-tag>
            <span v-else>未复核</span>
          </template>
        </el-table-column>
        <el-table-column prop="llmExplanation" label="大模型解释" min-width="200" show-overflow-tooltip />
        <el-table-column prop="llmLatencyMs" label="耗时(ms)" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.taskStatus === 'PENDING'" size="small" type="success" @click="handleExecute(row)">立即执行</el-button>
            <el-button v-if="row.taskStatus === 'SUCCESS' && !row.humanReviewStatus" size="small" type="primary" @click="handleReview(row)">复核</el-button>
            <el-button v-if="row.taskStatus === 'PENDING'" size="small" type="danger" @click="handleCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="复检任务详情" width="800px">
      <el-descriptions :column="2" border v-if="currentRow">
        <el-descriptions-item label="任务ID">{{ currentRow.id }}</el-descriptions-item>
        <el-descriptions-item label="检测记录ID">{{ currentRow.detectLogId }}</el-descriptions-item>
        <el-descriptions-item label="任务状态">
          <el-tag :type="getStatusType(currentRow.taskStatus)">{{ getStatusText(currentRow.taskStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ currentRow.retryCount }} / {{ currentRow.maxRetry }}</el-descriptions-item>
        <el-descriptions-item label="小模型判定">
          <el-tag :type="currentRow.smallModelLabel === 'DANGEROUS' ? 'danger' : 'success'">
            {{ currentRow.smallModelLabel === 'DANGEROUS' ? '危险' : '安全' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="大模型判定">
          <el-tag v-if="currentRow.llmLabel" :type="currentRow.llmLabel === 'DANGEROUS' ? 'danger' : 'success'">
            {{ currentRow.llmLabel === 'DANGEROUS' ? '危险' : '安全' }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="人工判定">
          <el-tag v-if="currentRow.humanLabel" :type="currentRow.humanLabel === 'DANGEROUS' ? 'danger' : 'success'">
            {{ currentRow.humanLabel === 'DANGEROUS' ? '危险' : '安全' }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="复核状态">
          <el-tag v-if="currentRow.humanReviewStatus" :type="currentRow.humanReviewStatus === 'AGREED' ? 'success' : 'warning'">
            {{ currentRow.humanReviewStatus === 'AGREED' ? '同意' : '不同意' }}
          </el-tag>
          <span v-else>未复核</span>
        </el-descriptions-item>
        <el-descriptions-item label="大模型提供商">{{ currentRow.llmProvider || '-' }}</el-descriptions-item>
        <el-descriptions-item label="大模型名称">{{ currentRow.llmModel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentRow.llmLatencyMs || '-' }} ms</el-descriptions-item>
        <el-descriptions-item label="复核人">{{ currentRow.reviewerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ currentRow.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ currentRow.updatedAt }}</el-descriptions-item>
        <el-descriptions-item label="代码片段" :span="2">
          <pre class="code-snippet">{{ currentRow.codeSnippet || '-' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="小模型输出" :span="2">
          {{ currentRow.smallModelRawOutput || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="大模型解释" :span="2">
          {{ currentRow.llmExplanation || '-' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentRow.llmErrorMessage" label="错误信息" :span="2">
          <span class="error-text">{{ currentRow.llmErrorMessage }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentRow.humanRemark" label="复核备注" :span="2">
          {{ currentRow.humanRemark }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 复核弹窗 -->
    <el-dialog v-model="reviewVisible" title="人工复核" width="500px">
      <el-form :model="reviewForm" label-width="100px">
        <el-form-item label="复核结果" required>
          <el-radio-group v-model="reviewForm.humanReviewStatus">
            <el-radio label="AGREED">同意大模型判定</el-radio>
            <el-radio label="DISAGREED">不同意大模型判定</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="reviewForm.humanReviewStatus === 'DISAGREED'" label="人工判定" required>
          <el-radio-group v-model="reviewForm.humanLabel">
            <el-radio label="SAFE">安全</el-radio>
            <el-radio label="DANGEROUS">危险</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="reviewForm.humanRemark" type="textarea" :rows="3" placeholder="请输入复核备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReview" :loading="reviewLoading">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageLlmReviews,
  getLlmReview,
  humanReviewLlm,
  cancelLlmReview,
  executeLlmReview,
  exportLlmReviewsJsonl,
  type LlmReviewQuery,
  type LlmReviewVO
} from '@/api/log'

// 查询参数
const query = reactive<LlmReviewQuery>({
  pageNum: 1,
  pageSize: 20,
  taskStatus: '',
  llmLabel: '',
  humanReviewStatus: ''
})

const timeRange = ref<[string, string] | null>(null)
const loading = ref(false)
const tableData = ref<LlmReviewVO[]>([])
const total = ref(0)

// 详情弹窗
const detailVisible = ref(false)
const currentRow = ref<LlmReviewVO | null>(null)

// 复核弹窗
const reviewVisible = ref(false)
const reviewLoading = ref(false)
const reviewForm = reactive({
  id: 0,
  humanReviewStatus: 'AGREED',
  humanLabel: '',
  humanRemark: ''
})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    if (timeRange.value) {
      query.beginTime = timeRange.value[0]
      query.endTime = timeRange.value[1]
    } else {
      query.beginTime = undefined
      query.endTime = undefined
    }
    const res = await pageLlmReviews(query)
    tableData.value = res.list || []
    total.value = res.total || 0
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

// 重置
const handleReset = () => {
  query.pageNum = 1
  query.pageSize = 20
  query.taskStatus = ''
  query.llmLabel = ''
  query.humanReviewStatus = ''
  query.beginTime = undefined
  query.endTime = undefined
  timeRange.value = null
  loadData()
}

// 分页
const handleSizeChange = (size: number) => {
  query.pageSize = size
  query.pageNum = 1
  loadData()
}

const handlePageChange = (page: number) => {
  query.pageNum = page
  loadData()
}

// 查看详情
const handleView = async (row: LlmReviewVO) => {
  try {
    const res = await getLlmReview(row.id)
    currentRow.value = res
    detailVisible.value = true
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

// 立即执行
const handleExecute = async (row: LlmReviewVO) => {
  try {
    await executeLlmReview(row.id)
    ElMessage.success('任务已提交执行，请稍后刷新查看结果')
    // 延迟刷新，等待异步任务状态变更
    setTimeout(() => loadData(), 1500)
  } catch {
    // 业务错误已由 request 拦截器自动弹出
  }
}

// 打开复核弹窗
const handleReview = (row: LlmReviewVO) => {
  reviewForm.id = row.id
  reviewForm.humanReviewStatus = 'AGREED'
  reviewForm.humanLabel = ''
  reviewForm.humanRemark = ''
  reviewVisible.value = true
}

// 提交复核
const submitReview = async () => {
  if (!reviewForm.humanReviewStatus) {
    ElMessage.warning('请选择复核结果')
    return
  }
  if (reviewForm.humanReviewStatus === 'DISAGREED' && !reviewForm.humanLabel) {
    ElMessage.warning('不同意大模型判定时，必须选择人工判定')
    return
  }

  reviewLoading.value = true
  try {
    await humanReviewLlm(reviewForm.id, {
      humanReviewStatus: reviewForm.humanReviewStatus,
      humanLabel: reviewForm.humanReviewStatus === 'DISAGREED' ? reviewForm.humanLabel : undefined,
      humanRemark: reviewForm.humanRemark || undefined
    })
    ElMessage.success('复核成功')
    reviewVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error('复核失败')
  } finally {
    reviewLoading.value = false
  }
}

// 取消任务
const handleCancel = async (row: LlmReviewVO) => {
  try {
    await ElMessageBox.confirm('确定要取消该复检任务吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cancelLlmReview(row.id)
    ElMessage.success('取消成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消失败')
    }
  }
}

// 导出 JSONL
const handleExportJsonl = async () => {
  try {
    await exportLlmReviewsJsonl(query)
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

// 状态显示
const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'info',
    RUNNING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger',
    CANCELLED: 'info'
  }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待处理',
    RUNNING: '处理中',
    SUCCESS: '成功',
    FAILED: '失败',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.llm-review-container {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.table-card {
  margin-bottom: 20px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.code-snippet {
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

.error-text {
  color: #f56c6c;
}
</style>
