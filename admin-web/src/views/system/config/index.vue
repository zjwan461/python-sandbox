<script setup lang="ts">
/**
 * 系统设置（T-0041 前端部分，/admin-api/sys/configs，views/system/config/index.vue）：
 * 受控 KV 管理：新注册开关、登录失败阈值、锁定时长、最长免登天数、
 * 默认限流值（分/时/天）、匿名调用灰度开关。
 * - 仅展示 sys_config 已登记键（后端拒绝未识别键，11008/11009）；
 * - 普通用户无菜单入口且后端 sysconfig:* 权限码独立拒绝；审计员只读（无编辑按钮权限）；
 * - 敏感内部凭证/ApiKey 不在 sys_config 登记范围，天然不出现在本页。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { batchUpdateConfigs, listConfigs } from '@/api/sys'
import type { SysConfigItem } from '@/utils/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canEdit = computed(() => userStore.permissions.has('sysconfig:edit') || userStore.roles.includes('superadmin'))

const loading = ref(false)
const saving = ref(false)
const configs = ref<SysConfigItem[]>([])
/** configKey -> 编辑中的值 */
const draft = reactive<Record<string, string>>({})

async function load() {
  loading.value = true
  try {
    configs.value = await listConfigs()
    configs.value.forEach((c) => {
      draft[c.configKey] = c.configValue
    })
  } finally {
    loading.value = false
  }
}

const dirtyKeys = computed(() =>
  configs.value.filter((c) => draft[c.configKey] !== c.configValue).map((c) => c.configKey)
)

async function save() {
  if (!dirtyKeys.value.length) {
    ElMessage.info('没有变更需要保存')
    return
  }
  const updates: Record<string, string> = {}
  dirtyKeys.value.forEach((k) => {
    updates[k] = draft[k]
  })
  saving.value = true
  try {
    await batchUpdateConfigs(updates)
    ElMessage.success(`已保存 ${dirtyKeys.value.length} 项设置（立即对登录/限流/认证链路生效）`)
    await load()
  } finally {
    saving.value = false
  }
}

function booleanValue(key: string): boolean {
  return draft[key] === 'true'
}

function setBoolean(key: string, val: boolean | string | number) {
  draft[key] = val ? 'true' : 'false'
}

onMounted(load)
</script>

<template>
  <div class="app-page">
    <div class="app-card">
      <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
        <span class="app-title" style="margin: 0">系统设置</span>
        <div>
          <el-tag v-if="!canEdit" type="info" style="margin-right: 8px">只读模式</el-tag>
          <el-button v-permission="['sysconfig:edit']" type="primary" :loading="saving" :disabled="!dirtyKeys.length" @click="save">
            保存变更{{ dirtyKeys.length ? `（${dirtyKeys.length}）` : '' }}
          </el-button>
        </div>
      </div>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        <template #title>
          仅可更新已登记的稳定键（按值类型校验）；敏感内部凭证与 ApiKey 不在此配置。
          匿名调用灰度开关默认严格（false）。
        </template>
      </el-alert>
      <el-table v-loading="loading" :data="configs" stripe border size="small">
        <el-table-column prop="configName" label="设置项" min-width="150" />
        <el-table-column prop="configKey" label="稳定键" min-width="190" />
        <el-table-column prop="valueType" label="类型" width="90" />
        <el-table-column label="值" min-width="200">
          <template #default="{ row }">
            <el-switch
              v-if="row.valueType === 'BOOLEAN'"
              :model-value="booleanValue(row.configKey)"
              :disabled="!canEdit"
              @update:model-value="(v: any) => setBoolean(row.configKey, v)"
            />
            <el-input
              v-else
              v-model="draft[row.configKey]"
              :disabled="!canEdit"
              size="small"
              style="max-width: 220px"
            />
            <el-tag v-if="draft[row.configKey] !== row.configValue" type="warning" size="small" style="margin-left: 6px">未保存</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="160" />
        <template #empty><el-empty description="无已登记设置项" /></template>
      </el-table>
    </div>
  </div>
</template>
