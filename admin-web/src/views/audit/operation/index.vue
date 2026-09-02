<script setup lang="ts">
/**
 * 操作日志（T-0020 前端部分，/admin-api/audit/operations，只读，views/audit/operation/index.vue）。
 * 记录操作人、模块、类型、对象主键+名称、关键变更、IP/UA/traceId 与结果；traceId 可跳转链路详情。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getOpLog, pageOpLogs } from '@/api/audit'
import type { AdminOpLog } from '@/utils/types'

const router = useRouter()
const loading = ref(false)
const rows = ref<AdminOpLog[]>([])
const total = ref(0)
const query = reactive({
  module: '', operationType: '', operatorName: '', targetId: '',
  beginTime: '' as string | undefined, endTime: '' as string | undefined, pageNum: 1, pageSize: 20
})
const timeRange = ref<[string, string] | null>(null)

const modules = ['user', 'role', 'menu', 'client', 'apikey', 'ratelimit', 'session', 'bridge']
const opTypes = ['add', 'edit', 'delete', 'enable', 'disable', 'revoke', 'reset', 'force']

const detailVisible = ref(false)
const detail = ref<AdminOpLog | null>(null)

async function load() {
  loading.value = true
  try {
    query.beginTime = timeRange.value?.[0] || undefined
    query.endTime = timeRange.value?.[1] || undefined
    const page = await pageOpLogs(query)
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

async function openDetail(row: AdminOpLog) {
  detail.value = (await getOpLog(row.id)) || row
  detailVisible.value = true
}

function goTrace(traceId?: string) {
  if (traceId) router.push({ path: '/business/apilog', query: { traceId } })
}

onMounted(load)
</script>

<template>
  <div class="app-page">
    <div class="app-card app-search">
      <el-form inline>
        <el-form-item label="模块">
          <el-select v-model="query.module" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="query.operationType" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="t in opTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="query.operatorName" clearable style="width: 140px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="目标ID">
          <el-input v-model="query.targetId" clearable style="width: 130px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="timeRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" range-separator="~" start-placeholder="起" end-placeholder="止" style="width: 340px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="app-card">
      <span class="app-title" style="margin: 0 0 12px; display: block">操作日志（只读，仅追加）</span>
      <el-table v-loading="loading" :data="rows" stripe border size="small">
        <el-table-column prop="opTime" label="时间" width="160" />
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column prop="module" label="模块" width="90" />
        <el-table-column prop="operationType" label="类型" width="80" />
        <el-table-column label="对象" min-width="160">
          <template #default="{ row }">#{{ row.targetId }} {{ row.targetName }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="traceId" label="traceId" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link v-if="row.traceId" type="primary" @click="goTrace(row.traceId)">{{ row.traceId }}</el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无操作日志" /></template>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @change="load"
      />
    </div>

    <el-dialog v-model="detailVisible" title="操作日志详情" width="640px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detail.opTime }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.operatorName }}（#{{ detail.operatorId }}）</el-descriptions-item>
        <el-descriptions-item label="结果">{{ detail.result }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.module }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.operationType }}</el-descriptions-item>
        <el-descriptions-item label="对象">#{{ detail.targetId }} {{ detail.targetName }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="traceId" :span="2">{{ detail.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="UA" :span="2">{{ detail.userAgent || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.failReason" label="失败原因" :span="2">{{ detail.failReason }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top: 12px">
        <b>关键变更摘要</b>
        <pre class="app-log-pre">{{ detail?.changeSummary || '-' }}</pre>
      </div>
    </el-dialog>
  </div>
</template>
