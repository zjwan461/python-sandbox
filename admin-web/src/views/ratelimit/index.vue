<script setup lang="ts">
/**
 * 限流规则管理（T-0030 前端部分，/admin-api/ratelimits，views/ratelimit/index.vue）：
 * 维度（API_KEY/CLIENT/GLOBAL）+ 目标 + 窗口（MINUTE/HOUR/DAY）+ 阈值 + 优先级 + 状态 + 有效期；
 * 保存成功后端自动触发 python-sandbox 重拉（失败由定时拉取兜底），页面另提供手动"刷新规则"入口。
 * 30010（重复规则）/30011（目标不可见）语义由后端 message 透出。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  changeRuleStatus,
  createRule,
  deleteRule,
  pageRules,
  reloadRules,
  updateRule,
  type RatelimitQuery
} from '@/api/ratelimit'
import { pageClients } from '@/api/client'
import { pageApiKeys } from '@/api/apikey'
import type { ApiKeyVO, ClientApp, RatelimitRule, RatelimitUpsertRequest } from '@/utils/types'
import { maskKey } from '@/utils/format'

const loading = ref(false)
const rows = ref<RatelimitRule[]>([])
const total = ref(0)
const query = reactive<RatelimitQuery>({ dimension: undefined, windowType: undefined, status: undefined, pageNum: 1, pageSize: 20 })

const clientOptions = ref<ClientApp[]>([])
const apiKeyOptions = ref<ApiKeyVO[]>([])

const dimensionLabels: Record<string, string> = { API_KEY: 'ApiKey', CLIENT: '客户端', GLOBAL: '全局默认' }
const windowLabels: Record<string, string> = { MINUTE: '每分钟', HOUR: '每小时', DAY: '每天' }

const editVisible = ref(false)
const editIsCreate = ref(true)
const editId = ref<number>()
const editFormRef = ref<FormInstance>()
const editForm = reactive<RatelimitUpsertRequest>({
  dimension: 'API_KEY',
  targetId: 0,
  windowType: 'MINUTE',
  threshold: 60,
  priority: 100,
  status: 1,
  effectiveTime: null,
  expireTime: null,
  remark: ''
})
const editRules: FormRules = {
  dimension: [{ required: true, message: '请选择维度', trigger: 'change' }],
  windowType: [{ required: true, message: '请选择窗口', trigger: 'change' }],
  threshold: [
    { required: true, message: '请输入阈值', trigger: 'blur' },
    { type: 'integer', min: 1, message: '阈值须为正整数', trigger: 'blur' }
  ]
}

function targetLabel(row: RatelimitRule): string {
  if (row.dimension === 'GLOBAL') return '全部调用方'
  if (row.dimension === 'CLIENT') {
    const c = clientOptions.value.find((x) => x.id === row.targetId)
    return c ? `${c.clientName}（${c.clientCode}）` : `#${row.targetId}`
  }
  const k = apiKeyOptions.value.find((x) => x.id === row.targetId)
  return k ? `${k.name}（${maskKey(k.keyPrefix, k.keySuffixMask)}）` : `#${row.targetId}`
}

async function load() {
  loading.value = true
  try {
    const page = await pageRules(query)
    rows.value = page.list || []
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadTargets() {
  const [cPage, kPage] = await Promise.all([
    pageClients({ pageNum: 1, pageSize: 200 }).catch(() => ({ list: [], total: 0, pageNum: 1, pageSize: 200 })),
    pageApiKeys({ pageNum: 1, pageSize: 200 }).catch(() => ({ list: [], total: 0, pageNum: 1, pageSize: 200 }))
  ])
  clientOptions.value = cPage.list || []
  apiKeyOptions.value = kPage.list || []
}

function search() {
  query.pageNum = 1
  load()
}

function openCreate() {
  editIsCreate.value = true
  editId.value = undefined
  Object.assign(editForm, {
    dimension: 'API_KEY', targetId: 0, windowType: 'MINUTE', threshold: 60,
    priority: 100, status: 1, effectiveTime: null, expireTime: null, remark: ''
  })
  editVisible.value = true
}

function openEdit(row: RatelimitRule) {
  editIsCreate.value = false
  editId.value = row.id
  Object.assign(editForm, {
    dimension: row.dimension,
    targetId: row.targetId,
    windowType: row.windowType,
    threshold: row.threshold,
    priority: row.priority ?? 100,
    status: row.status,
    effectiveTime: row.effectiveTime || null,
    expireTime: row.expireTime || null,
    remark: row.remark || ''
  })
  editVisible.value = true
}

async function submitEdit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate()
  const payload = { ...editForm }
  if (payload.dimension === 'GLOBAL') {
    payload.targetId = 0
  }
  if (!payload.targetId && payload.dimension !== 'GLOBAL') {
    ElMessage.warning('请选择规则目标')
    return
  }
  if (editIsCreate.value) {
    await createRule(payload)
    ElMessage.success('规则已保存（已通知沙箱服务刷新）')
  } else {
    await updateRule(editId.value!, payload)
    ElMessage.success('保存成功（已通知沙箱服务刷新）')
  }
  editVisible.value = false
  load()
}

async function toggleStatus(row: RatelimitRule) {
  const target = row.status === 1 ? 0 : 1
  await changeRuleStatus(row.id, target)
  ElMessage.success(target === 1 ? '已启用' : '已停用')
  load()
}

async function doDelete(row: RatelimitRule) {
  try {
    await ElMessageBox.confirm(`确定删除规则 #${row.id}（${dimensionLabels[row.dimension]} · ${windowLabels[row.windowType]} · ${row.threshold}）吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteRule(row.id)
  ElMessage.success('已删除')
  load()
}

async function doReload() {
  const res = await reloadRules()
  if (res.success) {
    ElMessage.success('已通知沙箱服务立即重拉规则')
  } else {
    ElMessage.warning('沙箱服务刷新失败（将由定时拉取兜底）')
  }
}

onMounted(async () => {
  await Promise.all([load(), loadTargets()])
})
</script>

<template>
  <div class="app-page">
    <div class="app-card app-search">
      <el-form inline>
        <el-form-item label="维度">
          <el-select v-model="query.dimension" placeholder="全部" clearable style="width: 120px">
            <el-option label="ApiKey" value="API_KEY" />
            <el-option label="客户端" value="CLIENT" />
            <el-option label="全局默认" value="GLOBAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="窗口">
          <el-select v-model="query.windowType" placeholder="全部" clearable style="width: 120px">
            <el-option label="每分钟" value="MINUTE" />
            <el-option label="每小时" value="HOUR" />
            <el-option label="每天" value="DAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="app-card">
      <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
        <span class="app-title" style="margin: 0">限流规则</span>
        <div>
          <el-button v-permission="['ratelimit:edit']" :icon="'Refresh'" @click="doReload">手动刷新沙箱规则</el-button>
          <el-button v-permission="['ratelimit:add']" type="primary" :icon="'Plus'" @click="openCreate">新增规则</el-button>
        </div>
      </div>
      <el-table v-loading="loading" :data="rows" stripe border size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="维度" width="90">
          <template #default="{ row }">{{ dimensionLabels[row.dimension] || row.dimension }}</template>
        </el-table-column>
        <el-table-column label="目标" min-width="200">
          <template #default="{ row }">{{ targetLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="窗口/阈值" min-width="140">
          <template #default="{ row }">{{ windowLabels[row.windowType] || row.windowType }} ≤ {{ row.threshold }}</template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="300">
          <template #default="{ row }">
            {{ row.effectiveTime || '立即' }} ~ {{ row.expireTime || '永不' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['ratelimit:edit']" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="['ratelimit:disable']" link :type="row.status === 1 ? 'warning' : 'success'" size="small" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-permission="['ratelimit:delete']" link type="danger" size="small" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无限流规则" /></template>
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

    <el-dialog v-model="editVisible" :title="editIsCreate ? '新增规则' : '编辑规则'" width="540px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="维度" prop="dimension">
          <el-radio-group v-model="editForm.dimension">
            <el-radio value="API_KEY">ApiKey</el-radio>
            <el-radio value="CLIENT">客户端</el-radio>
            <el-radio value="GLOBAL">全局默认</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editForm.dimension === 'API_KEY'" label="目标 ApiKey">
          <el-select v-model="editForm.targetId" filterable style="width: 100%">
            <el-option
              v-for="k in apiKeyOptions"
              :key="k.id"
              :label="`${k.name}（${maskKey(k.keyPrefix, k.keySuffixMask)} · ${k.clientCode || ''}）`"
              :value="k.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editForm.dimension === 'CLIENT'" label="目标客户端">
          <el-select v-model="editForm.targetId" filterable style="width: 100%">
            <el-option v-for="c in clientOptions" :key="c.id" :label="`${c.clientName}（${c.clientCode}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editForm.dimension === 'GLOBAL'" label="目标">
          <span style="color: #909399">全局默认规则作用于所有未命中专属规则的调用方（仅管理员可配置）</span>
        </el-form-item>
        <el-form-item label="窗口类型" prop="windowType">
          <el-select v-model="editForm.windowType" style="width: 160px">
            <el-option label="每分钟" value="MINUTE" />
            <el-option label="每小时" value="HOUR" />
            <el-option label="每天" value="DAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值" prop="threshold">
          <el-input-number v-model="editForm.threshold" :min="1" :max="1000000000" style="width: 180px" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">窗口内最大请求数</span>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="editForm.priority" :min="1" style="width: 140px" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">数值越小越先判定；多规则叠加任一命中即拒绝</span>
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker v-model="editForm.effectiveTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="留空立即生效" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效时间">
          <el-date-picker v-model="editForm.expireTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="留空永不失效" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
