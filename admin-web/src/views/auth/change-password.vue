<script setup lang="ts">
/**
 * 修改密码（T-0017，FR-AUTH-05~07）：
 * - PUT /auth/password（旧密码+新密码+确认；11005 旧密码错误 / 11006 不一致）
 * - 成功后后端作废全部旧会话 → 前端清态回登录页
 * - forced=1 表示首登强制改密，不可跳过（无返回入口，守卫亦拦截其他路由）
 * - 不展示任何密码或密码摘要
 */
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permStore = usePermissionStore()

const forced = computed(() => route.query.forced === '1')
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 128, message: '新密码长度需在 8-128 之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_r, v: string, cb) => (v === form.newPassword ? cb() : cb(new Error('两次输入的密码不一致'))),
      trigger: 'blur'
    }
  ]
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    await changePassword({ ...form })
    ElMessage.success('密码修改成功，请重新登录')
    // 后端已作废旧会话：前端同步清态
    userStore.reset()
    permStore.resetRoutes()
    router.replace('/login')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="blank-layout">
    <div class="pwd-card">
      <h3 class="pwd-title">{{ forced ? '首次登录，请设置新密码' : '修改密码' }}</h3>
      <el-alert
        v-if="forced"
        type="warning"
        :closable="false"
        title="为保障账号安全，首次登录必须修改初始密码"
        style="margin-bottom: 16px"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" @keyup.enter="submit">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="submit">确认修改</el-button>
          <el-button v-if="!forced" @click="router.back()">返回</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.pwd-card {
  width: 420px;
  background: #fff;
  border-radius: var(--app-card-radius);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  padding: 32px;
}
.pwd-title {
  margin: 0 0 20px;
  text-align: center;
}
</style>
