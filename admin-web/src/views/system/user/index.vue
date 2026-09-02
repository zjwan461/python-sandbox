<script setup lang="ts">
/**
 * 用户管理（T-0018 前端部分 + 批次6 收尾，/admin-api/users）：
 * 分页筛选（用户名/昵称/状态/部门）、新增、编辑、启停用、重置密码、手动解锁、分配角色；
 * 批次6（T-0018）：单删/批删（软删除+历史归属转移；已登录/持有有效 ApiKey 后端 12006 阻止）；
 * 批次6（T-0043）：CSV 导出（范围=当前筛选+数据权限）与批量导入（逐行反馈，重复用户名不静默覆盖）。
 * 按钮均以 v-permission 控制（后端 @SaCheckPermission 独立校验，不依赖前端隐藏）。
 * 部门为可空文本字段；列表不展示密码或密码摘要。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  assignUserRoles,
  changeUserStatus,
  createUser,
  deleteUser,
  deleteUsers,
  exportUsers,
  importUsers,
  pageUsers,
  resetUserPassword,
  unlockUser,
  updateUser,
  type UserQuery
} from '@/api/user'
import { roleOptions } from '@/api/role'
import type { AdminRole, UserImportResult, UserUpsertRequest, UserVO } from '@/utils/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const rows = ref<UserVO[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const query = reactive<UserQuery>({ username: '', nickname: '', status: undefined, deptName: '', pageNum: 1, pageSize: 20 })

const roleList = ref<AdminRole[]>([])

// ===== 新增/编辑对话框 =====
const editVisible = ref(false)
const editIsCreate = ref(true)
const editId = ref<number>()
const editFormRef = ref<FormInstance>()
const editForm = reactive<UserUpsertRequest>({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  password: '',
  status: 1,
  deptName: '',
  remark: '',
  roleIds: []
})
const editRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]{3,64}$/, message: '3-64位字母/数字/下划线/中划线', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请输入初始密码（至少8位）', trigger: 'blur' }, { min: 8, message: '密码至少8位', trigger: 'blur' }]
}

// ===== 分配角色对话框 =====
const rolesVisible = ref(false)
const rolesTarget = ref<UserVO>()
const rolesChecked = ref<number[]>([])

// ===== 导入对话框（T-0043） =====
const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importPassword = ref('')
const importing = ref(false)
const importResult = ref<UserImportResult | null>(null)
const importFileInput = ref<HTMLInputElement>()

async function load() {
  loading.value = true
  try {
    const page = await pageUsers(query)
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

function resetQuery() {
  query.username = ''
  query.nickname = ''
  query.status = undefined
  query.deptName = ''
  search()
}

function onSelectionChange(selection: UserVO[]) {
  selectedIds.value = selection.map((r) => r.id)
}

function openCreate() {
  editIsCreate.value = true
  editId.value = undefined
  Object.assign(editForm, {
    username: '', nickname: '', email: '', phone: '', password: '',
    status: 1, deptName: '', remark: '', roleIds: []
  })
  editVisible.value = true
}

function openEdit(row: UserVO) {
  editIsCreate.value = false
  editId.value = row.id
  Object.assign(editForm, {
    username: row.username,
    nickname: row.nickname || '',
    email: row.email || '',
    phone: row.phone || '',
    password: '',
    status: row.status,
    deptName: row.deptName || '',
    remark: row.remark || '',
    roleIds: (row.roles || []).map((r) => r.id)
  })
  editVisible.value = true
}

async function submitEdit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate()
  if (!editForm.roleIds?.length) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  if (editIsCreate.value) {
    await createUser({ ...editForm })
    ElMessage.success('用户创建成功')
  } else {
    await updateUser(editId.value!, { ...editForm })
    ElMessage.success('保存成功')
  }
  editVisible.value = false
  load()
}

async function toggleStatus(row: UserVO) {
  const target = row.status === 1 ? 0 : 1
  const verb = target === 0 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      target === 0
        ? `确定停用用户「${row.username}」吗？停用将即时踢下线，其名下启用中的 ApiKey 将被沙箱服务拒绝。`
        : `确定启用用户「${row.username}」吗？`,
      `${verb}确认`,
      { type: 'warning' }
    )
  } catch {
    return
  }
  await changeUserStatus(row.id, target)
  ElMessage.success(`已${verb}`)
  load()
}

async function doResetPassword(row: UserVO) {
  try {
    const { value } = await ElMessageBox.prompt(
      `为用户「${row.username}」设置新密码（至少 8 位；用户下次登录须使用新密码）`,
      '重置密码',
      {
        inputType: 'password',
        inputPattern: /^.{8,128}$/,
        inputErrorMessage: '密码长度需在 8-128 之间'
      }
    )
    await resetUserPassword(row.id, value)
    ElMessage.success('密码已重置')
  } catch {
    /* 取消 */
  }
}

async function doUnlock(row: UserVO) {
  await unlockUser(row.id)
  ElMessage.success('账号已解锁')
  load()
}

function openRoles(row: UserVO) {
  rolesTarget.value = row
  rolesChecked.value = (row.roles || []).map((r) => r.id)
  rolesVisible.value = true
}

async function submitRoles() {
  if (!rolesTarget.value) return
  if (!rolesChecked.value.length) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  await assignUserRoles(rolesTarget.value.id, rolesChecked.value)
  ElMessage.success('角色已更新（受影响会话已作废）')
  rolesVisible.value = false
  load()
}

// ===== 删除（T-0018 批次6：软删除+历史归属转移） =====

async function doDelete(row: UserVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${row.username}」吗？删除为软删除；若该用户当前已登录或仍持有有效 ApiKey 将被拒绝；` +
        `其名下客户端与 ApiKey 的历史归属将转移至您（当前操作管理员）。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除' }
    )
  } catch {
    return
  }
  await deleteUser(row.id)
  ElMessage.success('用户已删除，历史数据归属已转移')
  load()
}

async function doBatchDelete() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选要删除的用户')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定批量删除选中的 ${selectedIds.value.length} 个用户吗？任一用户已登录或持有有效 ApiKey 将整批拒绝；` +
        `成功删除的用户历史归属将转移至您（当前操作管理员）。`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '确认批量删除' }
    )
  } catch {
    return
  }
  await deleteUsers(selectedIds.value)
  ElMessage.success('批量删除完成')
  selectedIds.value = []
  load()
}

// ===== 导入导出（T-0043） =====

async function doExport() {
  await exportUsers({ ...query, pageNum: 1, pageSize: 20 })
}

function openImport() {
  importFile.value = null
  importPassword.value = ''
  importResult.value = null
  importVisible.value = true
}

function pickImportFile() {
  importFileInput.value?.click()
}

function onImportFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    if (!/\.csv$/i.test(file.name)) {
      ElMessage.warning('仅支持 .csv 文件')
      input.value = ''
      return
    }
    importFile.value = file
  }
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请选择 CSV 文件')
    return
  }
  if (importPassword.value.length < 8) {
    ElMessage.warning('初始密码至少 8 位')
    return
  }
  importing.value = true
  try {
    importResult.value = await importUsers(importFile.value, importPassword.value)
    const ok = importResult.value.successUsernames.length
    ElMessage.success(`导入完成：成功 ${ok}，失败 ${importResult.value.failures.length}`)
    load()
  } finally {
    importing.value = false
  }
}

onMounted(async () => {
  await load()
  roleList.value = await roleOptions().catch(() => [])
})
</script>

<template>
  <div class="app-page">
    <div class="app-card app-search">
      <el-form inline>
        <el-form-item label="用户名">
          <el-input v-model="query.username" placeholder="模糊" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="query.nickname" placeholder="模糊" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="query.deptName" placeholder="模糊" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="app-card">
      <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
        <span class="app-title" style="margin: 0">用户列表</span>
        <div>
          <el-button v-permission="['user:export']" :icon="'Download'" @click="doExport">导出 CSV</el-button>
          <el-button v-permission="['user:import']" :icon="'Upload'" @click="openImport">导入 CSV</el-button>
          <el-button v-permission="['user:delete']" type="danger" plain :icon="'Delete'" :disabled="!selectedIds.length" @click="doBatchDelete">
            批量删除{{ selectedIds.length ? `（${selectedIds.length}）` : '' }}
          </el-button>
          <el-button v-permission="['user:add']" type="primary" :icon="'Plus'" @click="openCreate">新增用户</el-button>
        </div>
      </div>
      <el-table v-loading="loading" :data="rows" stripe border size="small" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="42" :selectable="(row: UserVO) => row.id !== userStore.userInfo?.userId" />
        <el-table-column prop="username" label="用户名" min-width="110" />
        <el-table-column prop="nickname" label="昵称" min-width="100" />
        <el-table-column prop="deptName" label="部门" min-width="90" />
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="r in row.roles || []" :key="r.id" size="small" style="margin-right: 4px">{{ r.roleName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
            <el-tag v-if="row.locked" type="warning" size="small" class="truncated-tag">已锁定</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最后登录" width="160" />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['user:edit']" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="['user:edit']" link type="primary" size="small" @click="openRoles(row)">分配角色</el-button>
            <el-button v-permission="['user:disable']" link :type="row.status === 1 ? 'warning' : 'success'" size="small" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-permission="['user:reset']" link type="danger" size="small" @click="doResetPassword(row)">重置密码</el-button>
            <el-button v-if="row.locked" v-permission="['user:edit']" link type="warning" size="small" @click="doUnlock(row)">解锁</el-button>
            <el-button
              v-if="row.id !== userStore.userInfo?.userId"
              v-permission="['user:delete']"
              link
              type="danger"
              size="small"
              @click="doDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户数据" />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end"
        @change="load"
      />
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="editVisible" :title="editIsCreate ? '新增用户' : '编辑用户'" width="520px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="editForm.username" :disabled="!editIsCreate" />
        </el-form-item>
        <el-form-item v-if="editIsCreate" label="初始密码" prop="password">
          <el-input v-model="editForm.password" type="password" show-password placeholder="至少 8 位" />
        </el-form-item>
        <el-form-item label="昵称"><el-input v-model="editForm.nickname" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="editForm.email" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="editForm.phone" /></el-form-item>
        <el-form-item label="部门"><el-input v-model="editForm.deptName" placeholder="可空文本" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.roleIds" multiple style="width: 100%" placeholder="至少一个角色">
            <el-option v-for="r in roleList" :key="r.id" :label="r.roleName" :value="r.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色 -->
    <el-dialog v-model="rolesVisible" :title="`分配角色 - ${rolesTarget?.username || ''}`" width="420px">
      <el-checkbox-group v-model="rolesChecked">
        <div v-for="r in roleList" :key="r.id" style="line-height: 30px">
          <el-checkbox :value="r.id!">{{ r.roleName }}（{{ r.roleKey }}）</el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="rolesVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRoles">保存</el-button>
      </template>
    </el-dialog>

    <!-- CSV 导入（T-0043） -->
    <el-dialog v-model="importVisible" title="批量导入用户（CSV）" width="560px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        <template #title>
          CSV 列：username,nickname,email,phone,deptName,roleKeys（角色权限字符，多个用分号分隔）。
          首行表头可带可不带；重复用户名与非法字段逐行拒绝，不覆盖既有账号；导入用户首登强制改密。
        </template>
      </el-alert>
      <div style="margin-bottom: 12px">
        <input ref="importFileInput" type="file" accept=".csv" style="display: none" @change="onImportFileChange" />
        <el-button :icon="'Document'" @click="pickImportFile">选择文件</el-button>
        <span style="margin-left: 8px; color: #606266">{{ importFile ? importFile.name : '未选择' }}</span>
      </div>
      <el-form label-width="110px">
        <el-form-item label="统一初始密码">
          <el-input v-model="importPassword" type="password" show-password placeholder="至少 8 位（导入用户首登强制改密）" style="width: 260px" />
        </el-form-item>
      </el-form>
      <template v-if="importResult">
        <el-divider content-position="left">导入结果（共 {{ importResult.total }} 行）</el-divider>
        <p>
          成功 <el-text type="success">{{ importResult.successUsernames.length }}</el-text> ：
          {{ importResult.successUsernames.join('、') || '—' }}
        </p>
        <p style="margin-top: 4px">失败 <el-text type="danger">{{ importResult.failures.length }}</el-text></p>
        <el-table v-if="importResult.failures.length" :data="importResult.failures" size="small" border max-height="180">
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column prop="reason" label="失败原因" />
        </el-table>
      </template>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>
