<script setup lang="ts">
/**
 * 角色管理（T-0019 前端部分，/admin-api/roles）：
 * 列表、新增/编辑、删除（内置角色保护 + 被引用阻断由后端语义透出）、启停用、分配菜单权限。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  assignRoleMenus,
  changeRoleStatus,
  createRole,
  deleteRole,
  pageRoles,
  roleMenuIds,
  updateRole
} from '@/api/role'
import { menuTree } from '@/api/menu'
import type { AdminMenu, AdminRole } from '@/utils/types'

const loading = ref(false)
const rows = ref<AdminRole[]>([])
const total = ref(0)
const query = reactive({ roleName: '', roleKey: '', status: undefined as number | undefined, pageNum: 1, pageSize: 20 })

// ===== 编辑 =====
const editVisible = ref(false)
const editIsCreate = ref(true)
const editId = ref<number>()
const editFormRef = ref<FormInstance>()
const editForm = reactive<AdminRole>({ roleName: '', roleKey: '', sortOrder: 100, status: 1, remark: '' })
const editRules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [
    { required: true, message: '请输入权限字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]{2,64}$/, message: '2-64位字母/数字/下划线/中划线', trigger: 'blur' }
  ]
}

// ===== 分配菜单 =====
const menusVisible = ref(false)
const menusTarget = ref<AdminRole>()
const menuTreeData = ref<AdminMenu[]>([])
const menuCheckedIds = ref<number[]>([])
const menuTreeRef = ref<any>()

function filterTree(nodes: AdminMenu[]): any[] {
  // el-tree 需要 label 字段；保留 children
  return (nodes || []).map((n) => ({
    id: n.id,
    label: `${n.menuName}${n.menuType === 'F' ? ` [${n.perms || ''}]` : ''}`,
    children: n.children?.length ? filterTree(n.children) : undefined
  }))
}

const treeData = reactive<{ data: any[] }>({ data: [] })

async function load() {
  loading.value = true
  try {
    const page = await pageRoles(query)
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
  Object.assign(editForm, { roleName: '', roleKey: '', sortOrder: 100, status: 1, remark: '' })
  editVisible.value = true
}

function openEdit(row: AdminRole) {
  editIsCreate.value = false
  editId.value = row.id
  Object.assign(editForm, { ...row })
  editVisible.value = true
}

async function submitEdit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate()
  if (editIsCreate.value) {
    await createRole({ ...editForm })
    ElMessage.success('角色创建成功')
  } else {
    await updateRole(editId.value!, { ...editForm })
    ElMessage.success('保存成功')
  }
  editVisible.value = false
  load()
}

async function doDelete(row: AdminRole) {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？内置角色与被引用角色将被后端拒绝。`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteRole(row.id!)
  ElMessage.success('已删除')
  load()
}

async function toggleStatus(row: AdminRole) {
  const target = row.status === 1 ? 0 : 1
  await changeRoleStatus(row.id!, target)
  ElMessage.success(target === 1 ? '已启用' : '已停用（受影响会话已作废）')
  load()
}

async function openMenus(row: AdminRole) {
  menusTarget.value = row
  if (!menuTreeData.value.length) {
    menuTreeData.value = await menuTree()
    treeData.data = filterTree(menuTreeData.value)
  }
  const ids = await roleMenuIds(row.id!)
  menuCheckedIds.value = ids || []
  menusVisible.value = true
  // 等待 tree 渲染后设置勾选
  setTimeout(() => {
    menuTreeRef.value?.setCheckedKeys(ids || [], false)
  }, 50)
}

async function submitMenus() {
  if (!menusTarget.value || !menuTreeRef.value) return
  const checked = menuTreeRef.value.getCheckedKeys(false) as number[]
  const half = menuTreeRef.value.getHalfCheckedKeys() as number[]
  await assignRoleMenus(menusTarget.value.id!, [...checked, ...half])
  ElMessage.success('菜单权限已更新（受影响会话已作废）')
  menusVisible.value = false
}

onMounted(load)
</script>

<template>
  <div class="app-page">
    <div class="app-card app-search">
      <el-form inline>
        <el-form-item label="角色名称">
          <el-input v-model="query.roleName" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="权限字符">
          <el-input v-model="query.roleKey" clearable style="width: 160px" @keyup.enter="search" />
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
        <span class="app-title" style="margin: 0">角色列表</span>
        <el-button v-permission="['role:add']" type="primary" :icon="'Plus'" @click="openCreate">新增角色</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" stripe border size="small">
        <el-table-column prop="roleName" label="角色名称" min-width="120" />
        <el-table-column prop="roleKey" label="权限字符" min-width="110" />
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column label="内置" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.builtIn === 1" size="small" type="info">内置</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['role:edit']" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="['role:edit']" link type="primary" size="small" @click="openMenus(row)">分配菜单</el-button>
            <el-button v-permission="['role:edit']" link :type="row.status === 1 ? 'warning' : 'success'" size="small" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-if="row.builtIn !== 1" v-permission="['role:delete']" link type="danger" size="small" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
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

    <el-dialog v-model="editVisible" :title="editIsCreate ? '新增角色' : '编辑角色'" width="480px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName"><el-input v-model="editForm.roleName" /></el-form-item>
        <el-form-item label="权限字符" prop="roleKey">
          <el-input v-model="editForm.roleKey" :disabled="editForm.builtIn === 1" placeholder="如 ops（内置角色不可修改）" />
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="editForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="menusVisible" :title="`分配菜单 - ${menusTarget?.roleName || ''}`" width="480px">
      <el-tree
        ref="menuTreeRef"
        :data="treeData.data"
        show-checkbox
        node-key="id"
        :default-expand-all="false"
        :props="{ children: 'children', label: 'label' }"
        style="max-height: 480px; overflow: auto"
      />
      <template #footer>
        <el-button @click="menusVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMenus">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
