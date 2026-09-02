<script setup lang="ts">
/**
 * 菜单管理（T-0019/T-0039 前端部分，/admin-api/menus）：
 * 树形列表（M=目录 C=菜单 F=按钮）、新增/编辑、删除（子节点阻断由后端 12005 语义透出）、
 * 按钮权限字符维护（F 必填 perms）。保存后可影响其他用户下次登录的动态路由。
 * T-0039（FR-MENU-03）：同级排序（上移/下移提交 batch-sort；无拖拽依赖，采用步进式排序等价交互）、
 * 目录/菜单可见性快捷切换（changeMenuVisible，保存后立即反映到当前用户可见路由）。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { batchSortMenus, changeMenuVisible, createMenu, deleteMenu, menuTree, updateMenu } from '@/api/menu'
import type { AdminMenu } from '@/utils/types'

const loading = ref(false)
const tree = ref<AdminMenu[]>([])

const typeTag: Record<string, { label: string; type: string }> = {
  M: { label: '目录', type: 'info' },
  C: { label: '菜单', type: 'primary' },
  F: { label: '按钮', type: 'success' }
}

const editVisible = ref(false)
const editIsCreate = ref(true)
const editId = ref<number>()
const editFormRef = ref<FormInstance>()
const editForm = reactive<AdminMenu>({
  parentId: 0,
  menuType: 'C',
  menuName: '',
  icon: '',
  sortOrder: 10,
  routePath: '',
  routeName: '',
  component: '',
  isExternal: 0,
  isCache: 0,
  isVisible: 1,
  perms: '',
  status: 1
})
const editRules: FormRules = {
  menuName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  perms: [
    {
      validator: (_r, _v, cb) =>
        editForm.menuType === 'F' && !editForm.perms ? cb(new Error('按钮必须填写权限字符')) : cb(),
      trigger: 'blur'
    }
  ]
}

/** 扁平化树供上级选择器使用 */
const parentOptions = ref<{ value: number; label: string }[]>([])
function flatten(nodes: AdminMenu[], depth = 0) {
  for (const n of nodes) {
    if (n.menuType !== 'F') {
      parentOptions.value.push({ value: n.id!, label: `${'　'.repeat(depth)}${n.menuName}` })
    }
    if (n.children?.length) flatten(n.children, depth + 1)
  }
}

async function load() {
  loading.value = true
  try {
    tree.value = await menuTree()
    parentOptions.value = [{ value: 0, label: '根目录' }]
    flatten(tree.value)
  } finally {
    loading.value = false
  }
}

function openCreate(parent?: AdminMenu) {
  editIsCreate.value = true
  editId.value = undefined
  Object.assign(editForm, {
    parentId: parent?.id ?? 0,
    menuType: parent ? (parent.menuType === 'M' ? 'C' : 'F') : 'M',
    menuName: '',
    icon: '',
    sortOrder: 10,
    routePath: '',
    routeName: '',
    component: '',
    isExternal: 0,
    isCache: 0,
    isVisible: 1,
    perms: '',
    status: 1
  })
  editVisible.value = true
}

function openEdit(row: AdminMenu) {
  editIsCreate.value = false
  editId.value = row.id
  Object.assign(editForm, {
    parentId: row.parentId,
    menuType: row.menuType,
    menuName: row.menuName,
    icon: row.icon || '',
    sortOrder: row.sortOrder ?? 10,
    routePath: row.routePath || '',
    routeName: row.routeName || '',
    component: row.component || '',
    isExternal: row.isExternal ?? 0,
    isCache: row.isCache ?? 0,
    isVisible: row.isVisible ?? 1,
    perms: row.perms || '',
    status: row.status ?? 1
  })
  editVisible.value = true
}

async function submitEdit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate()
  const payload: AdminMenu = { ...editForm }
  if (payload.menuType === 'F') {
    // 按钮不参与路由
    payload.routePath = ''
    payload.routeName = ''
    payload.component = ''
  }
  if (editIsCreate.value) {
    await createMenu(payload)
    ElMessage.success('菜单已新增')
  } else {
    await updateMenu(editId.value!, payload)
    ElMessage.success('保存成功')
  }
  editVisible.value = false
  load()
}

async function doDelete(row: AdminMenu) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.menuName}」吗？存在子节点时将被后端拒绝。`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteMenu(row.id!)
  ElMessage.success('已删除')
  load()
}

// ===== T-0039：同级排序与可见性快捷切换 =====

/** 在树中定位 row 所在的兄弟数组（含根层） */
function findSiblings(row: AdminMenu, nodes: AdminMenu[] = tree.value): AdminMenu[] | null {
  const idx = nodes.findIndex((n) => n.id === row.id)
  if (idx >= 0) return nodes
  for (const n of nodes) {
    if (n.children?.length) {
      const found = findSiblings(row, n.children)
      if (found) return found
    }
  }
  return null
}

function siblingIndexOf(row: AdminMenu): { list: AdminMenu[]; index: number } | null {
  const list = findSiblings(row)
  if (!list) return null
  const index = list.findIndex((n) => n.id === row.id)
  return { list, index }
}

async function moveSibling(row: AdminMenu, dir: -1 | 1) {
  const loc = siblingIndexOf(row)
  if (!loc) return
  const target = loc.index + dir
  if (target < 0 || target >= loc.list.length) {
    ElMessage.info('已在同级边界，无法继续移动')
    return
  }
  const ordered = [...loc.list]
  const [item] = ordered.splice(loc.index, 1)
  ordered.splice(target, 0, item)
  await batchSortMenus(ordered.map((n) => n.id!))
  ElMessage.success('排序已保存，立即生效')
  load()
}

async function toggleVisible(row: AdminMenu) {
  const next = row.isVisible === 0 ? 1 : 0
  await changeMenuVisible(row.id!, next)
  row.isVisible = next
  ElMessage.success(next === 1 ? '已设为显示（当前用户路由即时生效）' : '已设为隐藏')
}

onMounted(load)
</script>

<template>
  <div class="app-page">
    <div class="app-card">
      <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
        <span class="app-title" style="margin: 0">菜单树</span>
        <el-button v-permission="['menu:add']" type="primary" :icon="'Plus'" @click="openCreate()">新增顶级</el-button>
      </div>
      <el-table
        v-loading="loading"
        :data="tree"
        row-key="id"
        border
        size="small"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column prop="menuName" label="名称" min-width="200" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="(typeTag[row.menuType] as any)?.type" size="small">{{ typeTag[row.menuType]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="110" />
        <el-table-column prop="routePath" label="路由路径" min-width="150" />
        <el-table-column prop="component" label="组件" min-width="160" />
        <el-table-column prop="perms" label="权限字符" min-width="140" />
        <el-table-column prop="sortOrder" label="排序" width="60" />
        <el-table-column label="可见" width="110">
          <template #default="{ row }">
            <template v-if="row.menuType !== 'F'">
              <el-switch
                v-permission="['menu:edit']"
                :model-value="row.isVisible !== 0"
                size="small"
                @update:model-value="() => toggleVisible(row)"
              />
              <span style="margin-left: 4px; font-size: 12px; color: #909399">{{ row.isVisible === 0 ? '隐藏' : '显示' }}</span>
            </template>
            <span v-else>{{ row.isVisible === 0 ? '隐藏' : '显示' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.menuType !== 'F'" v-permission="['menu:add']" link type="primary" size="small" @click="openCreate(row)">新增子项</el-button>
            <el-button v-permission="['menu:edit']" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="['menu:edit']" link size="small" title="同级上移" @click="moveSibling(row, -1)">↑</el-button>
            <el-button v-permission="['menu:edit']" link size="small" title="同级下移" @click="moveSibling(row, 1)">↓</el-button>
            <el-button v-permission="['menu:delete']" link type="danger" size="small" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="editVisible" :title="editIsCreate ? '新增菜单' : '编辑菜单'" width="560px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="上级">
          <el-select v-model="editForm.parentId" style="width: 100%">
            <el-option v-for="p in parentOptions" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="menuType">
          <el-radio-group v-model="editForm.menuType">
            <el-radio value="M">目录</el-radio>
            <el-radio value="C">菜单</el-radio>
            <el-radio value="F">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" prop="menuName"><el-input v-model="editForm.menuName" /></el-form-item>
        <template v-if="editForm.menuType !== 'F'">
          <el-form-item label="图标"><el-input v-model="editForm.icon" placeholder="Element Plus 图标名，如 User" /></el-form-item>
          <el-form-item label="路由路径"><el-input v-model="editForm.routePath" placeholder="如 /system/user" /></el-form-item>
          <el-form-item label="路由名称"><el-input v-model="editForm.routeName" placeholder="如 SystemUser" /></el-form-item>
        </template>
        <el-form-item v-if="editForm.menuType === 'C'" label="组件路径">
          <el-input v-model="editForm.component" placeholder="views 相对路径，如 system/user/index" />
        </el-form-item>
        <el-form-item label="权限字符" prop="perms">
          <el-input v-model="editForm.perms" placeholder="按钮必填，如 apikey:edit" />
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="editForm.sortOrder" :min="0" /></el-form-item>
        <template v-if="editForm.menuType !== 'F'">
          <el-form-item label="可见">
            <el-switch v-model="editForm.isVisible" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item label="缓存">
            <el-switch v-model="editForm.isCache" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item label="外链">
            <el-switch v-model="editForm.isExternal" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
