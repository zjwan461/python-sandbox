<script setup lang="ts">
/**
 * ApiKey 管理（T-0029 前端部分，/admin-api/apikeys，views/apikey/index.vue 与种子菜单 component 对齐）：
 * - 列表仅展示 前缀+****+后四位掩码，永不出现明文（FR-APIKEY-02）。
 * - 创建/重新生成响应一次性携带明文 → 进入"一次性展示"对话框：可复制一次，关闭后不可再取（默认决策 #1）。
 * - 撤销不可逆（30009 状态冲突语义由后端透出）；重新生成即旧钥撤销+新明文一次性返回。
 * - 状态：1=启用 2=停用 3=已过期 4=已撤销。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  changeApiKeyStatus,
  createApiKey,
  pageApiKeys,
  regenerateApiKey,
  revokeApiKey,
  updateApiKey,
  type ApiKeyQuery
} from '@/api/apikey'
import { pageClients } from '@/api/client'
import type { ApiKeyCreateVO, ApiKeyUpsertRequest, ApiKeyVO, ClientApp } from '@/utils/types'
import { maskKey } from '@/utils/format'

const loading = ref(false)
const rows = ref<ApiKeyVO[]>([])
const total = ref(0)
const query = reactive<ApiKeyQuery>({ name: '', clientId: undefined, status: undefined, pageNum: 1, pageSize: 20 })

const clientOptions = ref<ClientApp[]>([])

// ===== 编辑对话框 =====
const editVisible = ref(false)
const editIsCreate = ref(true)
const editId = ref<number>()
const editFormRef = ref<FormInstance>()
const editForm = reactive<ApiKeyUpsertRequest>({
  name: '',
  clientId: 0,
  boundUserId: null,
  effectiveTime: null,
  expireTime: null,
  rateLimitExempt: 0,
  remark: ''
})
const editRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  clientId: [{ required: true, message: '请选择所属客户端', trigger: 'change' }]
}

// ===== 一次性明文展示 =====
const plaintextVisible = ref(false)
const plaintext = ref('')
const plaintextNotice = ref('')
const plaintextName = ref('')
const copied = ref(false)

const statusMap: Record<number, { label: string; type: string }> = {
  1: { label: '启用', type: 'success' },
  2: { label: '停用', type: 'warning' },
  3: { label: '已过期', type: 'info' },
  4: { label: '已撤销', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    const page = await pageApiKeys(query)
    rows.value = page.list || []
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadClients() {
  const page = await pageClients({ pageNum: 1, pageSize: 200 })
  clientOptions.value = page.list || []
}

function search() {
  query.pageNum = 1
  load()
}

function openCreate() {
  editIsCreate.value = true
  editId.value = undefined
  Object.assign(editForm, {
    name: '', clientId: clientOptions.value[0]?.id ?? 0, boundUserId: null,
    effectiveTime: null, expireTime: null, rateLimitExempt: 0, remark: ''
  })
  editVisible.value = true
}

function openEdit(row: ApiKeyVO) {
  editIsCreate.value = false
  editId.value = row.id
  Object.assign(editForm, {
    name: row.name,
    clientId: row.clientId,
    boundUserId: row.boundUserId ?? null,
    effectiveTime: row.effectiveTime || null,
    expireTime: row.expireTime || null,
    rateLimitExempt: row.rateLimitExempt ?? 0,
    remark: row.remark || ''
  })
  editVisible.value = true
}

function showPlatextOnce(res: ApiKeyCreateVO) {
  plaintext.value = res.plaintext || ''
  plaintextNotice.value = res.notice || '请立即复制并妥善保存；关闭后无法再次查看明文，只能重新生成。'
  plaintextName.value = res.apiKey?.name || ''
  copied.value = false
  plaintextVisible.value = true
}

async function submitEdit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate()
  if (editIsCreate.value) {
    const res = await createApiKey({ ...editForm })
    ElMessage.success('ApiKey 创建成功')
    editVisible.value = false
    await load()
    showPlatextOnce(res)
  } else {
    await updateApiKey(editId.value!, { ...editForm })
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  }
}

async function copyPlaintext() {
  try {
    await navigator.clipboard.writeText(plaintext.value)
    copied.value = true
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('浏览器拒绝剪贴板访问，请手动选择复制')
  }
}

function closePlaintext() {
  plaintext.value = ''
  plaintextVisible.value = false
}

async function toggleStatus(row: ApiKeyVO) {
  const target = row.status === 1 ? 2 : 1
  await changeApiKeyStatus(row.id, target)
  ElMessage.success(target === 1 ? '已启用' : '已停用')
  load()
}

async function doRevoke(row: ApiKeyVO) {
  try {
    await ElMessageBox.confirm(
      `确定撤销 ApiKey「${row.name}（${maskKey(row.keyPrefix, row.keySuffixMask)}）」吗？撤销不可逆，该密钥将永久无法调用沙箱服务。`,
      '撤销确认',
      { type: 'error', confirmButtonText: '确认撤销', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await revokeApiKey(row.id)
  ElMessage.success('已撤销')
  load()
}

async function doRegenerate(row: ApiKeyVO) {
  try {
    await ElMessageBox.confirm(
      `确定重新生成 ApiKey「${row.name}」吗？旧密钥将即刻被撤销且不可恢复，新明文仅展示一次。`,
      '重新生成确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  const res = await regenerateApiKey(row.id)
  ElMessage.success('已重新生成')
  await load()
  showPlatextOnce(res)
}

onMounted(async () => {
  await Promise.all([load(), loadClients()])
})
</script>

<template>
  <div class="app-page">
    <div class="app-card app-search">
      <el-form inline>
        <el-form-item label="名称">
          <el-input v-model="query.name" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="客户端">
          <el-select v-model="query.clientId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="c in clientOptions" :key="c.id" :label="`${c.clientName}（${c.clientCode}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="2" />
            <el-option label="已过期" :value="3" />
            <el-option label="已撤销" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="app-card">
      <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
        <span class="app-title" style="margin: 0">ApiKey 列表</span>
        <el-button v-permission="['apikey:add']" type="primary" :icon="'Plus'" @click="openCreate">创建 ApiKey</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" stripe border size="small">
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column label="密钥掩码" min-width="170">
          <template #default="{ row }">
            <code>{{ maskKey(row.keyPrefix, row.keySuffixMask) }}</code>
          </template>
        </el-table-column>
        <el-table-column label="客户端" min-width="140">
          <template #default="{ row }">{{ row.clientName }}（{{ row.clientCode }}）</template>
        </el-table-column>
        <el-table-column label="绑定用户" width="110">
          <template #default="{ row }">{{ row.boundUserName || '（按客户端维度）' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status] as any)?.type" size="small">{{ row.statusLabel || statusMap[row.status]?.label }}</el-tag>
            <el-tag v-if="row.rateLimitExempt === 1" size="small" type="primary" class="truncated-tag">白名单</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expireTime" label="过期时间" width="160">
          <template #default="{ row }">{{ row.expireTime || '永不过期' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status !== 4">
              <el-button v-permission="['apikey:edit']" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button v-permission="['apikey:disable']" link :type="row.status === 1 ? 'warning' : 'success'" size="small" @click="toggleStatus(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button v-permission="['apikey:revoke']" link type="danger" size="small" @click="doRevoke(row)">撤销</el-button>
              <el-button v-permission="['apikey:reset']" link type="primary" size="small" @click="doRegenerate(row)">重新生成</el-button>
            </template>
            <span v-else style="color: #909399">已撤销（终态）</span>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无 ApiKey" /></template>
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

    <!-- 创建/编辑 -->
    <el-dialog v-model="editVisible" :title="editIsCreate ? '创建 ApiKey' : '编辑 ApiKey 元数据'" width="540px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="110px">
        <el-form-item label="名称" prop="name"><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="所属客户端" prop="clientId">
          <el-select v-model="editForm.clientId" filterable style="width: 100%">
            <el-option v-for="c in clientOptions" :key="c.id" :label="`${c.clientName}（${c.clientCode}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定用户ID">
          <el-input-number v-model="(editForm as any).boundUserId" :min="1" style="width: 160px" placeholder="可空" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">留空 = 按客户端维度计</span>
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker v-model="editForm.effectiveTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="留空立即生效" style="width: 100%" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker v-model="editForm.expireTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="留空永不过期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="限流白名单">
          <el-switch v-model="editForm.rateLimitExempt" :active-value="1" :inactive-value="0" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">开启后跳过全部限流规则</span>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 一次性明文展示（design.md §6.2/§11.2：关闭后不可再取） -->
    <el-dialog v-model="plaintextVisible" :title="`ApiKey 明文 - ${plaintextName}`" width="560px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon :title="plaintextNotice" style="margin-bottom: 16px" />
      <el-input :model-value="plaintext" readonly>
        <template #append>
          <el-button :icon="'DocumentCopy'" @click="copyPlaintext">{{ copied ? '已复制' : '复制' }}</el-button>
        </template>
      </el-input>
      <div style="margin-top: 12px; color: #909399; font-size: 12px">
        明文不会保存于服务端任何存储；关闭本窗口后仅能通过掩码 <code>{{ plaintext ? maskKey(plaintext.slice(0, 12), plaintext.slice(-4)) : '' }}</code> 识别，遗失只能重新生成。
      </div>
      <template #footer>
        <el-button type="primary" @click="closePlaintext">我已保存，关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
