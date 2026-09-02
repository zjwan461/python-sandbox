<script setup lang="ts">
/**
 * 调用记录（T-0032 前端部分，/admin-api/logs/**，views/log/api/index.vue 与种子菜单 component 对齐）。
 *
 * 两个页签：
 * 1. API 日志：时间/ApiKey/客户端/用户/方法/路径/状态码/traceId/IP/sessionId/限流命中筛选，
 *    默认 createdAt 倒序（FR-LOG-05 支持排序列切换）。
 * 2. 沙箱操作日志：时间/类型/结果/traceId/sessionId/归属筛选，展示 stdout/stderr/exitCode/errorMessage。
 * 截断以 *Truncated 标记明示（FR-LOG-04：不伪装完整内容）。
 * traceId 链路详情：GET /logs/trace/{traceId} 跨两表聚合同屏（FR-LOG-03），弹窗展示；
 * 支持从会话页 /business/apilog?traceId=xxx 直接进入链路视图。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  exportApiLogs,
  exportSandboxLogs,
  getTraceDetail,
  pageApiLogs,
  pageSandboxLogs,
  type ApiLogQuery,
  type SandboxLogQuery
} from '@/api/log'
import { pageClients } from '@/api/client'
import { pageApiKeys } from '@/api/apikey'
import type { ApiKeyVO, ApiLogVO, ClientApp, SandboxLogVO, TraceDetailVO } from '@/utils/types'
import { maskKey } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const tab = ref<'api' | 'sandbox'>('api')
const loading = ref(false)

// ===== API 日志 =====
const apiRows = ref<ApiLogVO[]>([])
const apiTotal = ref(0)
const apiQuery = reactive<ApiLogQuery>({
  beginTime: '', endTime: '', apiKeyId: undefined, clientId: undefined, httpMethod: '',
  apiPath: '', responseCode: undefined, traceId: '', clientIp: '', sessionId: '',
  rateLimitHit: undefined, orderBy: 'createdAt', asc: false, pageNum: 1, pageSize: 20
})

// ===== 沙箱操作日志 =====
const sbxRows = ref<SandboxLogVO[]>([])
const sbxTotal = ref(0)
const sbxQuery = reactive<SandboxLogQuery>({
  beginTime: '', endTime: '', operationType: '', result: '', traceId: '', sessionId: '',
  clientId: undefined, apiKeyId: undefined, orderBy: 'createdAt', asc: false, pageNum: 1, pageSize: 20
})

const timeRange = ref<[string, string] | null>(null)
const sbxTimeRange = ref<[string, string] | null>(null)

const clientOptions = ref<ClientApp[]>([])
const apiKeyOptions = ref<ApiKeyVO[]>([])

// ===== 详情 / 链路 =====
const detailVisible = ref(false)
const detailRow = ref<ApiLogVO | SandboxLogVO | null>(null)
const detailKind = ref<'api' | 'sandbox'>('api')
const traceVisible = ref(false)
const traceData = ref<TraceDetailVO | null>(null)
const traceLoading = ref(false)

const httpMethods = ['GET', 'POST', 'PUT', 'DELETE']
const opTypes = ['PYTHON_EXEC', 'SHELL_EXEC', 'PIP_INSTALL', 'PIP_UNINSTALL', 'PIP_LIST']

async function loadApi() {
  loading.value = true
  try {
    apiQuery.beginTime = timeRange.value?.[0] || undefined as any
    apiQuery.endTime = timeRange.value?.[1] || undefined as any
    const page = await pageApiLogs(apiQuery)
    apiRows.value = page.list || []
    apiTotal.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadSbx() {
  loading.value = true
  try {
    sbxQuery.beginTime = sbxTimeRange.value?.[0] || undefined as any
    sbxQuery.endTime = sbxTimeRange.value?.[1] || undefined as any
    const page = await pageSandboxLogs(sbxQuery)
    sbxRows.value = page.list || []
    sbxTotal.value = page.total
  } finally {
    loading.value = false
  }
}

function search() {
  if (tab.value === 'api') {
    apiQuery.pageNum = 1
    loadApi()
  } else {
    sbxQuery.pageNum = 1
    loadSbx()
  }
}

function onTabChange() {
  if (tab.value === 'sandbox' && !sbxRows.value.length) loadSbx()
}

function sortApi(prop: string, order: 'ascending' | 'descending' | null) {
  if (!order) return
  apiQuery.orderBy = prop
  apiQuery.asc = order === 'ascending'
  loadApi()
}
function sortSbx(prop: string, order: 'ascending' | 'descending' | null) {
  if (!order) return
  sbxQuery.orderBy = prop
  sbxQuery.asc = order === 'ascending'
  loadSbx()
}

// ===== T-0045 导出（与当前筛选/排序/数据权限一致；apilog:export） =====
const exporting = ref(false)

async function doExport(format: 'csv' | 'excel') {
  exporting.value = true
  try {
    if (tab.value === 'api') {
      const { pageNum: _p, pageSize: _s, ...filters } = apiQuery
      await exportApiLogs(filters as ApiLogQuery, format)
    } else {
      const { pageNum: _p, pageSize: _s, ...filters } = sbxQuery
      await exportSandboxLogs(filters as SandboxLogQuery, format)
    }
  } finally {
    exporting.value = false
  }
}

function openApiDetail(row: ApiLogVO) {
  detailKind.value = 'api'
  detailRow.value = row
  detailVisible.value = true
}

function openSbxDetail(row: SandboxLogVO) {
  detailKind.value = 'sandbox'
  detailRow.value = row
  detailVisible.value = true
}

async function openTrace(traceId?: string) {
  if (!traceId) return
  traceVisible.value = true
  traceLoading.value = true
  try {
    traceData.value = await getTraceDetail(traceId)
  } catch {
    traceData.value = null
  } finally {
    traceLoading.value = false
  }
}

onMounted(async () => {
  await loadApi()
  const [cPage, kPage] = await Promise.all([
    pageClients({ pageNum: 1, pageSize: 200 }).catch(() => null),
    pageApiKeys({ pageNum: 1, pageSize: 200 }).catch(() => null)
  ])
  clientOptions.value = cPage?.list || []
  apiKeyOptions.value = kPage?.list || []
  const t = route.query.traceId as string | undefined
  if (t) {
    openTrace(t)
    router.replace({ query: {} })
  }
})
</script>

<template>
  <div class="app-page">
    <el-tabs v-model="tab" class="app-card" style="padding-top: 4px" @tab-change="onTabChange">
      <!-- ================= API 日志 ================= -->
      <el-tab-pane label="API 调用日志" name="api">
        <el-form inline class="app-search">
          <el-form-item label="时间">
            <el-date-picker
              v-model="timeRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              range-separator="~"
              start-placeholder="起"
              end-placeholder="止"
              style="width: 340px"
            />
          </el-form-item>
          <el-form-item label="客户端">
            <el-select v-model="apiQuery.clientId" placeholder="全部" clearable filterable style="width: 170px">
              <el-option v-for="c in clientOptions" :key="c.id" :label="c.clientCode" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="ApiKey">
            <el-select v-model="apiQuery.apiKeyId" placeholder="全部" clearable filterable style="width: 180px">
              <el-option
                v-for="k in apiKeyOptions"
                :key="k.id"
                :label="`${k.name}（${maskKey(k.keyPrefix, k.keySuffixMask)}）`"
                :value="k.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="方法">
            <el-select v-model="apiQuery.httpMethod" placeholder="全部" clearable style="width: 100px">
              <el-option v-for="m in httpMethods" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="路径">
            <el-input v-model="apiQuery.apiPath" clearable style="width: 160px" />
          </el-form-item>
          <el-form-item label="状态码">
            <el-input-number v-model="apiQuery.responseCode" :min="100" :max="599" controls-position="right" style="width: 110px" placeholder="如 200" />
          </el-form-item>
          <el-form-item label="traceId">
            <el-input v-model="apiQuery.traceId" clearable style="width: 200px" @keyup.enter="search" />
          </el-form-item>
          <el-form-item label="IP">
            <el-input v-model="apiQuery.clientIp" clearable style="width: 130px" />
          </el-form-item>
          <el-form-item label="会话ID">
            <el-input v-model="apiQuery.sessionId" clearable style="width: 160px" />
          </el-form-item>
          <el-form-item label="限流命中">
            <el-select v-model="apiQuery.rateLimitHit" placeholder="全部" clearable style="width: 100px">
              <el-option label="仅命中" :value="true" />
              <el-option label="未命中" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
            <el-button v-permission="['apilog:export']" :icon="'Download'" :loading="exporting" @click="doExport('csv')">导出CSV</el-button>
            <el-button v-permission="['apilog:export']" :icon="'Document'" :loading="exporting" @click="doExport('excel')">导出Excel</el-button>
          </el-form-item>
        </el-form>

        <el-table
          v-loading="loading"
          :data="apiRows"
          stripe border size="small"
          @sort-change="(p: any) => sortApi(p.prop, p.order)"
        >
          <el-table-column prop="createdAt" label="时间" width="160" sortable="custom" />
          <el-table-column prop="httpMethod" label="方法" width="70" />
          <el-table-column prop="apiPath" label="路径" min-width="180" show-overflow-tooltip />
          <el-table-column prop="responseCode" label="状态码" width="80" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.responseCode === 200 ? 'success' : row.responseCode === 429 ? 'warning' : 'danger'" size="small">
                {{ row.responseCode }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="executionTime" label="耗时(ms)" width="90" sortable="custom" />
          <el-table-column label="归属" min-width="180">
            <template #default="{ row }">
              <span v-if="row.clientCode">{{ row.clientCode }}</span>
              <span v-if="row.apiKeyLabel"> / {{ row.apiKeyLabel }}</span>
              <span v-if="row.ownerUserName"> / {{ row.ownerUserName }}</span>
              <span v-if="!row.clientCode" style="color: #909399">-</span>
            </template>
          </el-table-column>
          <el-table-column label="限流" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.rateLimitHit === 1" size="small" type="warning" :title="`命中规则 #${row.rateLimitRuleId}`">命中</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="clientIp" label="IP" width="130" />
          <el-table-column prop="traceId" label="traceId" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <el-link type="primary" @click="openTrace(row.traceId)">{{ row.traceId }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openApiDetail(row)">详情</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无调用记录" /></template>
        </el-table>
        <el-pagination
          v-model:current-page="apiQuery.pageNum"
          v-model:page-size="apiQuery.pageSize"
          :total="apiTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 12px; justify-content: flex-end"
          @change="loadApi"
        />
      </el-tab-pane>

      <!-- ================= 沙箱操作日志 ================= -->
      <el-tab-pane label="沙箱操作日志" name="sandbox">
        <el-form inline class="app-search">
          <el-form-item label="时间">
            <el-date-picker
              v-model="sbxTimeRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              range-separator="~"
              start-placeholder="起"
              end-placeholder="止"
              style="width: 340px"
            />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="sbxQuery.operationType" placeholder="全部" clearable style="width: 150px">
              <el-option v-for="t in opTypes" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="结果">
            <el-select v-model="sbxQuery.result" placeholder="全部" clearable style="width: 110px">
              <el-option label="SUCCESS" value="SUCCESS" />
              <el-option label="FAILED" value="FAILED" />
            </el-select>
          </el-form-item>
          <el-form-item label="traceId">
            <el-input v-model="sbxQuery.traceId" clearable style="width: 200px" @keyup.enter="search" />
          </el-form-item>
          <el-form-item label="会话ID">
            <el-input v-model="sbxQuery.sessionId" clearable style="width: 170px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
            <el-button v-permission="['apilog:export']" :icon="'Download'" :loading="exporting" @click="doExport('csv')">导出CSV</el-button>
            <el-button v-permission="['apilog:export']" :icon="'Document'" :loading="exporting" @click="doExport('excel')">导出Excel</el-button>
          </el-form-item>
        </el-form>

        <el-table
          v-loading="loading"
          :data="sbxRows"
          stripe border size="small"
          @sort-change="(p: any) => sortSbx(p.prop, p.order)"
        >
          <el-table-column prop="createdAt" label="时间" width="160" sortable="custom" />
          <el-table-column prop="operationType" label="类型" width="120" />
          <el-table-column prop="operationContent" label="内容" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ (row.operationContent || '').slice(0, 80) }}</span>
              <el-tag v-if="row.operationContentTruncated" size="small" type="warning" class="truncated-tag">已截断</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="exitCode" label="退出码" width="80" />
          <el-table-column prop="executionTime" label="耗时(ms)" width="90" sortable="custom" />
          <el-table-column prop="traceId" label="traceId" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <el-link type="primary" @click="openTrace(row.traceId)">{{ row.traceId }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openSbxDetail(row)">详情</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无操作日志" /></template>
        </el-table>
        <el-pagination
          v-model:current-page="sbxQuery.pageNum"
          v-model:page-size="sbxQuery.pageSize"
          :total="sbxTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 12px; justify-content: flex-end"
          @change="loadSbx"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 单条详情 -->
    <el-dialog v-model="detailVisible" title="日志详情" width="680px">
      <template v-if="detailKind === 'api' && detailRow">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="ID">{{ (detailRow as ApiLogVO).id }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ (detailRow as ApiLogVO).createdAt }}</el-descriptions-item>
          <el-descriptions-item label="方法">{{ (detailRow as ApiLogVO).httpMethod }}</el-descriptions-item>
          <el-descriptions-item label="状态码">{{ (detailRow as ApiLogVO).responseCode }}</el-descriptions-item>
          <el-descriptions-item label="路径" :span="2">{{ (detailRow as ApiLogVO).apiPath }}</el-descriptions-item>
          <el-descriptions-item label="traceId" :span="2">{{ (detailRow as ApiLogVO).traceId }}</el-descriptions-item>
          <el-descriptions-item label="会话">{{ (detailRow as ApiLogVO).sessionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ (detailRow as ApiLogVO).executionTime }} ms</el-descriptions-item>
          <el-descriptions-item label="客户端">{{ (detailRow as ApiLogVO).clientCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="ApiKey">{{ (detailRow as ApiLogVO).apiKeyLabel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="归属用户">{{ (detailRow as ApiLogVO).ownerUserName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="IP">{{ (detailRow as ApiLogVO).clientIp }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 12px">
          <b>请求参数</b>
          <el-tag v-if="(detailRow as ApiLogVO).requestParamsTruncated" size="small" type="warning" class="truncated-tag">
            内容已截断（非完整数据）
          </el-tag>
          <pre class="app-log-pre">{{ (detailRow as ApiLogVO).requestParams || '-' }}</pre>
        </div>
      </template>
      <template v-else-if="detailRow">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="ID">{{ (detailRow as SandboxLogVO).id }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ (detailRow as SandboxLogVO).createdAt }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ (detailRow as SandboxLogVO).operationType }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ (detailRow as SandboxLogVO).result }}</el-descriptions-item>
          <el-descriptions-item label="traceId" :span="2">{{ (detailRow as SandboxLogVO).traceId }}</el-descriptions-item>
          <el-descriptions-item label="会话">{{ (detailRow as SandboxLogVO).sessionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="退出码">{{ (detailRow as SandboxLogVO).exitCode ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 12px">
          <b>操作内容</b>
          <el-tag v-if="(detailRow as SandboxLogVO).operationContentTruncated" size="small" type="warning" class="truncated-tag">已截断</el-tag>
          <pre class="app-log-pre">{{ (detailRow as SandboxLogVO).operationContent || '-' }}</pre>
        </div>
        <div style="margin-top: 8px">
          <b>标准输出</b>
          <el-tag v-if="(detailRow as SandboxLogVO).stdoutTruncated" size="small" type="warning" class="truncated-tag">已截断</el-tag>
          <pre class="app-log-pre">{{ (detailRow as SandboxLogVO).stdout || '-' }}</pre>
        </div>
        <div style="margin-top: 8px">
          <b>标准错误</b>
          <el-tag v-if="(detailRow as SandboxLogVO).stderrTruncated" size="small" type="warning" class="truncated-tag">已截断</el-tag>
          <pre class="app-log-pre">{{ (detailRow as SandboxLogVO).stderr || '-' }}</pre>
        </div>
        <div v-if="(detailRow as SandboxLogVO).errorMessage" style="margin-top: 8px">
          <b>错误信息</b>
          <pre class="app-log-pre" style="color: #F56C6C">{{ (detailRow as SandboxLogVO).errorMessage }}</pre>
        </div>
      </template>
    </el-dialog>

    <!-- traceId 链路聚合 -->
    <el-dialog v-model="traceVisible" :title="`链路详情 - ${traceData?.traceId || ''}`" width="820px" top="5vh">
      <div v-loading="traceLoading">
        <h4>API 调用（{{ traceData?.apiLogs?.length || 0 }}）</h4>
        <el-table :data="traceData?.apiLogs || []" size="small" border>
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column prop="httpMethod" label="方法" width="70" />
          <el-table-column prop="apiPath" label="路径" min-width="180" show-overflow-tooltip />
          <el-table-column prop="responseCode" label="状态码" width="80" />
          <el-table-column prop="executionTime" label="耗时" width="80" />
          <el-table-column label="操作" width="70">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openApiDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <h4 style="margin-top: 16px">沙箱操作（{{ traceData?.operationLogs?.length || 0 }}，按执行顺序）</h4>
        <el-table :data="traceData?.operationLogs || []" size="small" border>
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column prop="operationType" label="类型" width="120" />
          <el-table-column prop="operationContent" label="内容" min-width="220" show-overflow-tooltip />
          <el-table-column prop="result" label="结果" width="90" />
          <el-table-column prop="exitCode" label="退出码" width="80" />
          <el-table-column label="操作" width="70">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openSbxDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>
