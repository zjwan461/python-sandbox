<script setup lang="ts">
/**
 * 登录日志（T-0020 前端部分，/admin-api/audit/logins，只读，views/audit/login/index.vue）。
 * 登录成功/失败/锁定与原因完整留痕；普通用户无授权即无法访问（后端 loginlog:view 拦截 + 菜单不下发）。
 */
import { onMounted, reactive, ref } from 'vue'
import { pageLoginLogs } from '@/api/audit'
import type { AdminLoginLog } from '@/utils/types'

const loading = ref(false)
const rows = ref<AdminLoginLog[]>([])
const total = ref(0)
const query = reactive({ username: '', result: '', beginTime: '' as string | undefined, endTime: '' as string | undefined, pageNum: 1, pageSize: 20 })
const timeRange = ref<[string, string] | null>(null)

const resultMap: Record<string, { label: string; type: string }> = {
  SUCCESS: { label: '成功', type: 'success' },
  FAIL: { label: '失败', type: 'danger' },
  LOCKED: { label: '锁定', type: 'warning' }
}

async function load() {
  loading.value = true
  try {
    query.beginTime = timeRange.value?.[0] || undefined
    query.endTime = timeRange.value?.[1] || undefined
    const page = await pageLoginLogs(query)
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

onMounted(load)
</script>

<template>
  <div class="app-page">
    <div class="app-card app-search">
      <el-form inline>
        <el-form-item label="用户名">
          <el-input v-model="query.username" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.result" placeholder="全部" clearable style="width: 110px">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAIL" />
            <el-option label="锁定" value="LOCKED" />
          </el-select>
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
      <span class="app-title" style="margin: 0 0 12px; display: block">登录日志（只读，仅追加）</span>
      <el-table v-loading="loading" :data="rows" stripe border size="small">
        <el-table-column prop="loginTime" label="时间" width="160" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="(resultMap[row.result] as any)?.type" size="small">{{ resultMap[row.result]?.label || row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="失败/锁定原因" min-width="160" />
        <el-table-column prop="loginType" label="方式" width="100" />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="userAgent" label="UA" min-width="200" show-overflow-tooltip />
        <template #empty><el-empty description="暂无登录日志" /></template>
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
  </div>
</template>
