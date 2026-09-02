<script setup lang="ts">
/**
 * 通知公告管理（T-0042 前端部分，/admin-api/sys/notices，views/system/notice/index.vue；FR-SYS-02）。
 * - 管理员：新增（草稿）→ 发布 → 可下线/编辑/删除；写操作进入管理端审计（后端 @OperationLog）；
 * - 普通用户：仅有 notice:view 菜单授权时进入"阅读"形态（管理按钮被 v-permission 隐藏，
 *   后端独立拒绝其管理动作）；投递侧的通栏+站内信在 DefaultLayout。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createNotice,
  deleteNotice,
  pageNotices,
  publishNotice,
  unpublishNotice,
  updateNotice
} from '@/api/sys'
import type { NoticeUpsertRequest, SysNoticeVO } from '@/utils/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canManage = computed(
  () => userStore.permissions.has('notice:add') || userStore.roles.includes('superadmin')
)

const loading = ref(false)
const rows = ref<SysNoticeVO[]>([])
const total = ref(0)
const query = reactive<{ title: string; status?: number; pageNum: number; pageSize: number }>({
  title: '',
  status: undefined,
  pageNum: 1,
  pageSize: 20
})

const editVisible = ref(false)
const editIsCreate = ref(true)
const editId = ref<number>()
const editFormRef = ref<FormInstance>()
const editForm = reactive<NoticeUpsertRequest>({
  title: '',
  content: '',
  effectiveTime: null,
  expireTime: null,
  isTop: 0
})
const editRules: FormRules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const page = await pageNotices({ ...query })
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
  Object.assign(editForm, { title: '', content: '', effectiveTime: null, expireTime: null, isTop: 0 })
  editVisible.value = true
}

function openEdit(row: SysNoticeVO) {
  editIsCreate.value = false
  editId.value = row.id
  Object.assign(editForm, {
    title: row.title,
    content: row.content,
    effectiveTime: row.effectiveTime || null,
    expireTime: row.expireTime || null,
    isTop: row.top ? 1 : 0
  })
  editVisible.value = true
}

async function submitEdit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate()
  if (editIsCreate.value) {
    await createNotice({ ...editForm })
    ElMessage.success('公告已创建（草稿），请点"发布"投递')
  } else {
    await updateNotice(editId.value!, { ...editForm })
    ElMessage.success('保存成功')
  }
  editVisible.value = false
  load()
}

async function doPublish(row: SysNoticeVO) {
  await ElMessageBox.confirm(
    `确定发布公告「${row.title}」吗？发布后对全部登录用户投递（生效/失效时间窗口内展示）。`,
    '发布确认',
    { type: 'warning' }
  )
  await publishNotice(row.id)
  ElMessage.success('已发布')
  load()
}

async function doUnpublish(row: SysNoticeVO) {
  await ElMessageBox.confirm(`确定下线公告「${row.title}」吗？下线后不再向用户展示。`, '下线确认', { type: 'warning' })
  await unpublishNotice(row.id)
  ElMessage.success('已下线')
  load()
}

async function doDelete(row: SysNoticeVO) {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '删除确认', { type: 'warning' })
  await deleteNotice(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div class="app-page">
    <div class="app-card app-search">
      <el-form inline>
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="模糊" clearable style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="草稿/下线" :value="0" />
            <el-option label="已发布" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="app-card">
      <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
        <span class="app-title" style="margin: 0">通知公告</span>
        <el-button v-permission="['notice:add']" type="primary" :icon="'Plus'" @click="openCreate">新增公告</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" stripe border size="small">
        <el-table-column label="标题" min-width="200">
          <template #default="{ row }">
            <el-tag v-if="row.top" type="danger" size="small" style="margin-right: 4px">置顶</el-tag>
            {{ row.title }}
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '已发布' : '草稿/下线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效窗口" width="200">
          <template #default="{ row }">
            <span style="font-size: 12px">{{ row.effectiveTime || '发布即生效' }} ~ {{ row.expireTime || '长期' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="publisherName" label="发布人" width="100" />
        <el-table-column prop="publishTime" label="发布时间" width="160" />
        <el-table-column v-if="canManage" label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['notice:edit']" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status !== 1" v-permission="['notice:edit']" link type="success" size="small" @click="doPublish(row)">发布</el-button>
            <el-button v-else v-permission="['notice:edit']" link type="warning" size="small" @click="doUnpublish(row)">下线</el-button>
            <el-button v-permission="['notice:delete']" link type="danger" size="small" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无公告" /></template>
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

    <el-dialog v-model="editVisible" :title="editIsCreate ? '新增公告' : '编辑公告'" width="560px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="标题" prop="title"><el-input v-model="editForm.title" maxlength="200" /></el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="editForm.content" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker v-model="editForm.effectiveTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="留空=发布即生效" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效时间">
          <el-date-picker v-model="editForm.expireTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="留空=长期有效" style="width: 100%" />
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch :model-value="editForm.isTop === 1" @update:model-value="(v: any) => (editForm.isTop = v ? 1 : 0)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
