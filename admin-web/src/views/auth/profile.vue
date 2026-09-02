<script setup lang="ts">
/** 个人中心（T-0018 提及）：展示当前账号信息 + 修改密码入口。不展示密码/摘要。 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()
const info = computed(() => userStore.userInfo)
</script>

<template>
  <div class="app-page">
    <div class="app-card" style="max-width: 640px">
      <h3 class="app-title">个人中心</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户名">{{ info?.username }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ info?.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag v-for="r in info?.roles || []" :key="r" size="small" style="margin-right: 4px">{{ r }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="数据范围">{{ info?.dataScope === 'ALL' ? '全部' : '本人' }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top: 16px">
        <el-button type="primary" @click="router.push('/change-password')">修改密码</el-button>
      </div>
    </div>
  </div>
</template>
