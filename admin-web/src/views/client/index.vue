<script setup lang="ts">
/**
 * 客户端管理（T-0028 + T-0035 前端部分，/admin-api/clients）：
 * 分页筛选（名称/编码/归属人/状态）、新增/编辑、启停用、删除；
 * T-0035：行内"统计"弹窗（ApiKey 数/活跃数/今日调用/累计调用）与"归属转移"（仅管理员）。
 * - 删除仍持有有效 ApiKey 的客户端：后端 30008 阻断 → 拦截器透出消息（FR-CLIENT-04）。
 * - 停用后其名下启用 ApiKey 即刻被 python-sandbox 拒绝（CLIENT_DISABLED 语义）。
 * - 普通用户仅见并管理自己归属的客户端（SELF 行过滤在后端）。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  changeClientStatus,
  clientStats,
  createClient,
  deleteClient,
  pageClients,
  transferClientOwner,
  updateClient,
  type ClientQuery
} from '@/api/client'
import type { ClientApp, ClientStatsVO, ClientUpsertRequest } from '@/utils/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const rows = ref<ClientApp[]>([])
const total = ref(0)
const query = reactive<ClientQuery>({ clientName: '', clientCode: '', status: undefined, pageNum: 1, pageSize: 20 })

const editVisible = ref(false)
const editIsCreate = ref(true)
const editId = ref<number>()
const editFormRef = ref<FormInstance>()
const editForm = reactive<ClientUpsertRequest>({
  clientCode: '',
  clientName: '',
  description: '',
  ownerUserId: null,
  remark: ''
})
const editRules: FormRules = {
  clientCode: [
    { required: true, message: '请输入客户端编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]{1,64}$/, message: '仅允许字母、数字、下划线与连字符', trigger: 'blur' }
  ],
  clientName: [{ required: true, message: '请输入客户端名称', trigger: 'blur' }]
}

// ===== 统计卡片（T-0035） =====
const statsVisible = ref(false)
const statsTarget = ref<ClientApp | null>(null)
const stats = ref<ClientStatsVO | null>(null)
const statsLoading = ref(false)

async function load() {
  loading.value = true
  try {
    const page = await pageClients(query)
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

function openCreate() {
  editIsCreate.value = true
  editId.value = undefined
  Object.assign(editForm, { clientCode: '', clientName: '', description: '', ownerUserId: null, remark: '' })
  editVisible.value = true
}

function openEdit(row: ClientApp) {
  editIsCreate.value = false
  editId.value = row.id
  Object.assign(editForm, {
    clientCode: row.clientCode,
    clientName: row.clientName,
    description: row.description || '',
    ownerUserId: row.ownerUserId ?? null,
    remark: row.remark || ''
  })
  editVisible.value = true
}

async function submitEdit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate()
  if (editIsCreate.value) {
    await createClient({ ...editForm })
    ElMessage.success('客户端创建成功')
  } else {
    await updateClient(editId.value!, { ...editForm })
    ElMessage.success('保存成功')
  }
  editVisible.value = false
  load()
}

async function toggleStatus(row: ClientApp) {
  const target = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      target === 0
        ? `确定停用客户端「${row.clientName}」吗？停用后其名下启用中的 ApiKey 将被沙箱服务立即拒绝（CLIENT_DISABLED）。`
        : `确定启用客户端「${row.clientName}」吗？`,
      `${target === 0 ? '停用' : '启用'}确认`,
      { type: 'warning' }
    )
  } catch {
    return
  }
  await changeClientStatus(row.id, target)
  ElMessage.success(`已${target === 0 ? '停用' : '启用'}`)
  load()
}

async function doDelete(row: ClientApp) {
  try {
    await ElMessageBox.confirm(
      `确定删除客户端「${row.clientName}（${row.clientCode}）」吗？若其仍持有未撤销且未过期的 ApiKey，删除将被阻断，请先处理相关密钥。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await deleteClient(row.id)
  ElMessage.success('已删除')
  load()
}

/** 统计弹窗（口径与当前客户端一致，后端聚合） */
async function openStats(row: ClientApp) {
  statsTarget.value = row
  stats.value = null
  statsVisible.value = true
  statsLoading.value = true
  try {
    stats.value = await clientStats(row.id)
  } finally {
    statsLoading.value = false
  }
}

/** 归属转移（T-0035，仅管理员；转移后该客户端及历史调用记录按新归属展示） */
async function doTransfer(row: ClientApp) {
  try {
    const { value } = await ElMessageBox.prompt(
      `将客户端「${row.clientName}（${row.clientCode}）」的归属转移到新的用户ID（当前归属：${row.ownerUserId ? '#' + row.ownerUserId : '未指定'}）。` +
        `转移后该客户端及其历史调用记录按新归属用户展示。`,
      '归属转移',
      {
        inputPattern: /^\d+$/,
        inputErrorMessage: '请输入目标用户ID（正整数）',
        inputPlaceholder: '目标 admin_user.id'
      }
    )
    await transferClientOwner(row.id, Number(value))
    ElMessage.success('归属已转移（历史调用记录归属同步更新，已落审计）')
    load()
  } catch {
    /* 取消 */
  }
}

onMounted(load)
</script>

<template>
  <div class="app-page">
    <div class="app-card app-search">
      <el-form inline>
        <el-form-item label="名称">
          <el-input v-model="query.clientName" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="query.clientCode" clearable style="width: 160px" @keyup.enter="search" />
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
        <span class="app-title" style="margin: 0">客户端列表</span>
        <el-button v-permission="['client:add']" type="primary" :icon="'Plus'" @click="openCreate">新增客户端</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" stripe border size="small">
        <el-table-column prop="clientCode" label="编码" min-width="140" />
        <el-table-column prop="clientName" label="名称" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="归属用户" width="110">
          <template #default="{ row }">
            <span v-if="row.ownerUserId">#{{ row.ownerUserId }}</span>
            <span v-else style="color: #909399">未指定（按客户端维度）</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openStats(row)">统计</el-button>
            <el-button v-permission="['client:edit']" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="userStore.dataIsAll"
              v-permission="['client:edit']"
              link
              type="primary"
              size="small"
              @click="doTransfer(row)"
            >转移归属</el-button>
            <el-button v-permission="['client:disable']" link :type="row.status === 1 ? 'warning' : 'success'" size="small" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-permission="['client:delete']" link type="danger" size="small" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无客户端" /></template>
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

    <el-dialog v-model="editVisible" :title="editIsCreate ? '新增客户端' : '编辑客户端'" width="520px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="客户端编码" prop="clientCode">
          <el-input v-model="editForm.clientCode" :disabled="!editIsCreate" placeholder="全局唯一，如 demo-app" />
        </el-form-item>
        <el-form-item label="客户端名称" prop="clientName"><el-input v-model="editForm.clientName" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="editForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item v-if="userStore.dataIsAll" label="归属用户ID">
          <el-input-number v-model="editForm.ownerUserId as number" :min="1" placeholder="可空" style="width: 160px" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">留空 = 按客户端维度计</span>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 统计卡片（T-0035，FR-CLIENT-05） -->
    <el-dialog v-model="statsVisible" :title="`客户端统计 - ${statsTarget?.clientName || ''}`" width="560px">
      <div v-loading="statsLoading" style="display: flex; gap: 12px; flex-wrap: wrap">
        <el-card shadow="never" style="flex: 1; min-width: 120px; text-align: center">
          <div class="stat-num">{{ stats?.apiKeyCount ?? '-' }}</div>
          <div class="stat-label">ApiKey 总数</div>
        </el-card>
        <el-card shadow="never" style="flex: 1; min-width: 120px; text-align: center">
          <div class="stat-num" style="color: #67c23a">{{ stats?.activeApiKeyCount ?? '-' }}</div>
          <div class="stat-label">活跃 ApiKey</div>
        </el-card>
        <el-card shadow="never" style="flex: 1; min-width: 120px; text-align: center">
          <div class="stat-num" style="color: #409eff">{{ stats?.todayCalls ?? '-' }}</div>
          <div class="stat-label">今日调用</div>
        </el-card>
        <el-card shadow="never" style="flex: 1; min-width: 120px; text-align: center">
          <div class="stat-num">{{ stats?.totalCalls ?? '-' }}</div>
          <div class="stat-label">累计调用</div>
        </el-card>
      </div>
      <template #footer>
        <el-button @click="statsVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stat-num {
  font-size: 24px;
  font-weight: 600;
}
.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}
</style>
