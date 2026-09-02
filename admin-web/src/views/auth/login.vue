<script setup lang="ts">
/**
 * 登录页（T-0015/T-0034，design.md §9.3/§11.1）：
 * - 加载 GET /auth/captcha（本批次策略：始终要求验证码）
 * - 提交 POST /auth/login：账密 + captchaId + captchaAnswer + rememberMe
 * - 记住我（T-0034）：勾选后后端经 HttpOnly Cookie 下发长期 token（前端不读取、不存储）
 * - 进入登录页时尝试自动续登（POST /auth/auto-login，凭 Cookie）：
 *   成功且非首次登录直接跳转目标页；失败静默停留在登录表单
 * - 验证码错误（11001）不触发账号锁定，仅刷新验证码
 * - 登录成功：保存短期 token（Pinia + sessionStorage 同页镜像，不落 localStorage）
 * - firstLogin=true 强制跳转改密页（T-0017）
 * - 被踢下线/未登录（20001/20004）由 request 拦截器统一清理并回登录页
 */
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getCaptcha } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { CaptchaVO } from '@/utils/types'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const captcha = ref<CaptchaVO | null>(null)
const captchaLoading = ref(false)

const form = reactive({
  username: '',
  password: '',
  captchaAnswer: '',
  rememberMe: false // T-0034：勾选后经 HttpOnly Cookie 下发长期 token
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaAnswer: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    captcha.value = await getCaptcha()
    form.captchaAnswer = ''
  } finally {
    captchaLoading.value = false
  }
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (!captcha.value) {
    ElMessage.warning('请先获取验证码')
    return
  }
  loading.value = true
  try {
    const res = await userStore.login({
      username: form.username,
      password: form.password,
      captchaId: captcha.value.captchaId,
      captchaAnswer: form.captchaAnswer,
      rememberMe: form.rememberMe
    })
    if (res.firstLogin) {
      ElMessage.warning('首次登录必须修改密码')
      router.replace({ path: '/change-password', query: { forced: '1' } })
      return
    }
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch (e: any) {
    // 任何登录失败（含验证码错误 11001 / 账密错误 11002 / 锁定 11003 / 停用 11004）均刷新验证码
    await refreshCaptcha()
    // 拦截器已弹出错误消息，此处无需重复
  } finally {
    loading.value = false
  }
}

/**
 * 自动续登（T-0034）：进入登录页先尝试凭 HttpOnly Cookie 续登。
 * 失败/无长期 token 时静默返回，正常展示登录表单。
 */
async function tryAutoLogin(): Promise<boolean> {
  return userStore.tryAutoLogin()
}

onMounted(async () => {
  const ok = await tryAutoLogin()
  if (ok) {
    const redirect = (route.query.redirect as string) || '/'
    if (userStore.firstLogin) {
      router.replace({ path: '/change-password', query: { forced: '1' } })
    } else {
      ElMessage.success('已自动登录')
      router.replace(redirect)
    }
    return
  }
  await refreshCaptcha()
})
</script>

<template>
  <div class="blank-layout">
    <div class="login-card">
      <h2 class="login-title">Python Sandbox 管理端</h2>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" clearable :prefix-icon="'User'" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password :prefix-icon="'Lock'" />
        </el-form-item>
        <el-form-item prop="captchaAnswer">
          <div class="captcha-row">
            <el-input v-model="form.captchaAnswer" placeholder="验证码" maxlength="10" :prefix-icon="'Key'" />
            <div class="captcha-img" @click="refreshCaptcha" title="点击刷新验证码">
              <img v-if="captcha" :src="captcha.img" alt="验证码" />
              <el-icon v-else class="is-loading"><Loading /></el-icon>
            </div>
          </div>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.rememberMe">记住我（14 天内免登录）</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading || captchaLoading" @click="submit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-tip">验证码错误不会锁定账号；连续登录失败达阈值将临时锁定</div>
    </div>
  </div>
</template>

<style scoped>
.login-card {
  width: 380px;
  background: #fff;
  border-radius: var(--app-card-radius);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  padding: 32px;
}
.login-title {
  text-align: center;
  margin: 0 0 24px;
  font-size: 20px;
}
.captcha-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.captcha-img {
  width: 120px;
  height: 40px;
  border: 1px solid #dcdfe6;
  border-radius: var(--app-radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex: none;
}
.captcha-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.login-tip {
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
