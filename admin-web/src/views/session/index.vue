<script setup lang="ts">
/**
 * 运行中会话（T-0031/T-0037/T-0044 前端部分，/admin-api/sessions）：
 * - 列表来自 python-sandbox 内存快照（Bridge 拉取），孤儿会话不伪造；
 * - 普通用户仅见本人归属会话（后端 SELF 过滤，富化字段展示）；
 * - T-0037：筛选扩展（会话ID/创建时间范围/ApiKey/客户端/用户/不活跃分钟/仅默认），
 *   详情弹窗内联"关联最近日志"（API 日志 + 沙箱操作日志，遵守数据权限）；
 *   默认会话销毁高危显式二次确认（FR-SESSION-05）；
 * - T-0044：批量清理——按不活跃阈值预览目标数量确认，或表内多选显式目标；
 *   逐项回执、任一失败不虚构全部成功；自动阈值口径后端强制排除默认会话；
 * - 回执 {success,message,remainingSessions}：失败不移除前端记录（默认决策 #7）。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchDestroyPreview,
  batchDestroySessions,
  destroySession,
  pageSessions,
  sessionRelatedLogs,
  type SessionQuery,
  type SessionRelatedLogsVO
} from '@/api/session'
import type { SandboxSessionVO, SessionDestroyResultVO } from '@/utils/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const rows = ref<SandboxSessionVO[]>([])
const total = ref(0)
const query = reactive<SessionQuery>({
  sessionId: '',
  createdBegin: '',
  createdEnd: '',
  isDefault: undefined,
  inactiveMinutes: undefined,
  pageNum: 1,
  pageSize: 20
})

const selected = ref<SandboxSessionVO[]>([])

// ===== 详情 + 关联日志（T-0037） =====
const detailVisible = ref(false)
const detail = ref<SandboxSessionVO | null>(null)
const related = ref<SessionRelatedLogsVO | null>(null)
const relatedLoading = ref(false)

// ===== 批量清理（T-0044） =====
const batchVisible = ref(false)
const batchMinutes = ref(30)
const batchPreview = ref<number | null>(null)
const batchRunning = ref(false)
const batchResults = ref<SessionDestroyResultVO[]>([])

async function load() {
  loading.value = true
  try {
    const page = await pageSessions({
      ...query,
      sessionId: query.sessionId || undefined,
      createdBegin: query.createdBegin || undefined,
      createdEnd: query.createdEnd || undefined
    })
    rows.value = page.list || []
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function onSelectionChange(selection: SandboxSessionVO[]) {
  selected.value = selection
}

async function openDetail(row: SandboxSessionVO) {
  detail.value = row
  related.value = null
  detailVisible.value = true
  relatedLoading.value = true
  try {
    related.value = await sessionRelatedLogs(row.sessionId, 20)
  } catch {
    /* 关联日志失败不阻断详情展示 */
  } finally {
    relatedLoading.value = false
  }
}

async function doDestroy(row: SandboxSessionVO) {
  // 管理员二次确认；默认会话额外强调（FR-SESSION-05；后端 session:force 独立校验）
  const extra = row.isDefault ? '\n注意：这是【默认会话】，销毁将影响未显式指定 sessionId 的调用！' : ''
  try {
    await ElMessageBox.confirm(
      `确定强制销毁会话 ${row.sessionId}（容器 ${row.containerName || row.containerId || '-'}）吗？${extra}`,
      row.isDefault ? '默认会话销毁 - 高危确认' : '销毁确认',
      { type: 'error', confirmButtonText: '确认销毁', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  const res = await destroySession(row.sessionId)
  if (res.success) {
    ElMessage.success(`${res.message || '销毁成功'}（剩余会话 ${res.remainingSessions ?? '-'}）`)
    load()
  } else {
    ElMessage.error(`销毁失败：${res.message || '未知原因'}（剩余会话 ${res.remainingSessions ?? '-'}）`)
  }
}

// ===== T-0044 批量清理 =====

function openBatch() {
  batchResults.value = []
  batchPreview.value = null
  batchVisible.value = true
}

async function previewBatch() {
  if (!batchMinutes.value || batchMinutes.value <= 0) {
    ElMessage.warning('请填写不活跃阈值（分钟）')
    return
  }
  batchPreview.value = await batchDestroyPreview(batchMinutes.value)
}

async function submitBatchByThreshold() {
  if (batchPreview.value === null) {
    await previewBatch()
  }
  if (!batchPreview.value) {
    ElMessage.info('没有符合条件的不活跃会话（默认会话已被排除）')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将清理最后活跃超过 ${batchMinutes.value} 分钟的 ${batchPreview.value} 个会话（不含默认会话）。确认执行？`,
      '批量清理确认',
      { type: 'warning', confirmButtonText: '确认清理' }
    )
  } catch {
    return
  }
  batchRunning.value = true
  try {
    batchResults.value = await batchDestroySessions({ inactiveMinutes: batchMinutes.value })
    reportBatch()
    load()
  } finally {
    batchRunning.value = false
  }
}

async function submitBatchBySelection() {
  if (!selected.value.length) {
    ElMessage.warning('请先在列表勾选目标会话')
    return
  }
  const defaults = selected.value.filter((r) => r.isDefault)
  try {
    await ElMessageBox.confirm(
      `将清理选中的 ${selected.value.length} 个会话` +
        (defaults.length ? `，其中包含 ${defaults.length} 个【默认会话】（高危，将影响未指定 sessionId 的调用）！` : '。'),
      defaults.length ? '批量清理 - 含默认会话高危确认' : '批量清理确认',
      { type: defaults.length ? 'error' : 'warning', confirmButtonText: '确认清理' }
    )
  } catch {
    return
  }
  batchRunning.value = true
  try {
    batchResults.value = await batchDestroySessions({ sessionIds: selected.value.map((r) => r.sessionId) })
    reportBatch()
    load()
  } finally {
    batchRunning.value = false
  }
}

function reportBatch() {
  const ok = batchResults.value.filter((r) => r.success).length
  const fail = batchResults.value.length - ok
  if (fail === 0) {
    ElMessage.success(`批量清理完成：${ok} 个会话全部销毁`)
  } else {
    ElMessage.warning(`批量清理完成：成功 ${ok}，失败 ${fail}（详见逐项结果，未虚构全部成功）`)
  }
}

onMounted(load)
</script>

<template>
  <div class="app-page">
    <div class="app-card app-search">
      <el-form inline>
        <el-form-item label="会话ID">
          <el-input v-model="query.sessionId" placeholder="模糊" clearable style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="query.createdBegin"
            type="datetime"
            placeholder="起"
            value-format="YYYY-MM-DD HH:mm:ss"
            clearable
            style="width: 180px"
          />
          <span style="margin: 0 4px">-</span>
          <el-date-picker
            v-model="query.createdEnd"
            type="datetime"
            placeholder="止"
            value-format="YYYY-MM-DD HH:mm:ss"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="仅默认会话">
          <el-switch v-model="query.isDefault" />
        </el-form-item>
        <el-form-item label="不活跃超过(分钟)">
          <el-input-number v-model="query.inactiveMinutes" :min="1" placeholder="不过滤" style="width: 150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
          <el-button :icon="'Refresh'" @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="app-card">
      <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
        <span class="app-title" style="margin: 0">活跃会话（沙箱服务内存快照）</span>
        <el-button
          v-if="userStore.dataIsAll"
          v-permission="['session:force']"
          type="danger"
          plain
          :icon="'Delete'"
          @click="openBatch"
        >批量清理</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" stripe border size="small" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="sessionId" label="Session ID" min-width="200" show-overflow-tooltip />
        <el-table-column label="默认" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="danger" size="small">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="containerName" label="容器名" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column prop="lastActiveTime" label="最后活跃" width="160" />
        <el-table-column label="归属" min-width="220">
          <template #default="{ row }">
            <span v-if="row.ownerClientCode">客户端 {{ row.ownerClientCode }}</span>
            <span v-if="row.ownerApiKeyLabel"> / {{ row.ownerApiKeyLabel }}</span>
            <span v-if="row.ownerUserName"> / {{ row.ownerUserName }}</span>
            <span v-if="!row.ownerClientCode && !row.ownerApiKeyLabel && !row.ownerUserId" style="color: #909399">（匿名/内部）</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-permission="['session:force']" link type="danger" size="small" @click="doDestroy(row)">销毁</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="当前无活跃会话" /></template>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @change="load"
      />
    </div>

    <!-- 详情 + 关联日志（T-0037，FR-SESSION-03） -->
    <el-dialog v-model="detailVisible" title="会话详情与关联日志" width="760px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="Session ID" :span="2">{{ detail.sessionId }}</el-descriptions-item>
        <el-descriptions-item label="容器 ID">{{ detail.containerId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="容器名">{{ detail.containerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="默认会话">{{ detail.isDefault ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最后活跃">{{ detail.lastActiveTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="归属">{{ detail.ownerClientCode || '-' }} / {{ detail.ownerApiKeyLabel || '-' }} / {{ detail.ownerUserName || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-tabs model-value="api" style="margin-top: 12px">
        <el-tab-pane label="最近 API 日志" name="api">
          <el-table v-loading="relatedLoading" :data="related?.apiLogs || []" size="small" border max-height="220">
            <el-table-column prop="createdAt" label="时间" width="160" />
            <el-table-column prop="httpMethod" label="方法" width="70" />
            <el-table-column prop="apiPath" label="路径" show-overflow-tooltip />
            <el-table-column prop="responseCode" label="状态码" width="80" />
            <el-table-column prop="executionTime" label="耗时(ms)" width="90" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="最近沙箱操作日志" name="op">
          <el-table v-loading="relatedLoading" :data="related?.operationLogs || []" size="small" border max-height="220">
            <el-table-column prop="createdAt" label="时间" width="160" />
            <el-table-column prop="operationType" label="类型" width="120" />
            <el-table-column prop="result" label="结果" width="80" />
            <el-table-column prop="operationContent" label="内容" show-overflow-tooltip />
            <el-table-column prop="errorMessage" label="错误" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 批量清理（T-0044，FR-SESSION-06） -->
    <el-dialog v-model="batchVisible" title="批量清理不活跃会话" width="640px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
        <template #title>
          仅管理员可执行；按阈值自动清理时【默认会话不被隐式纳入】；在列表勾选目标后提交则包含所选默认会话（需上方高危确认）；
          逐项回执，任一失败不虚构全部成功，动作与逐项结果进入管理端审计。
        </template>
      </el-alert>
      <el-form inline>
        <el-form-item label="不活跃超过(分钟)">
          <el-input-number v-model="batchMinutes" :min="1" style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button @click="previewBatch">预览目标数量</el-button>
          <el-tag v-if="batchPreview !== null" type="warning" style="margin-left: 8px">将清理 {{ batchPreview }} 个</el-tag>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" :loading="batchRunning" @click="submitBatchByThreshold">按阈值清理</el-button>
          <el-button
            type="danger"
            plain
            :loading="batchRunning"
            :disabled="!selected.length"
            style="margin-left: 8px"
            @click="submitBatchBySelection"
          >清理勾选（{{ selected.length }}）</el-button>
        </el-form-item>
      </el-form>
      <template v-if="batchResults.length">
        <el-divider content-position="left">逐项结果</el-divider>
        <el-table :data="batchResults" size="small" border max-height="240">
          <el-table-column prop="sessionId" label="Session ID" min-width="200" show-overflow-tooltip />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="回执" min-width="180" show-overflow-tooltip />
          <el-table-column prop="remainingSessions" label="剩余会话" width="90" />
        </el-table>
      </template>
      <template #footer>
        <el-button @click="batchVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
