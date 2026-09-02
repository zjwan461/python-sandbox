<script setup lang="ts">
/** 工作台：当前账号概览 + 常用入口（轻页面，非批次5硬性要求，作为 Default Layout 视觉样板落地页） */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()
const nickname = computed(() => userStore.userInfo?.nickname || userStore.userInfo?.username || '')
const isAdmin = computed(() => userStore.dataIsAll)
</script>

<template>
  <div class="app-page">
    <div class="app-card">
      <h3 class="app-title">欢迎回来，{{ nickname }}</h3>
      <p style="color: #606266">
        当前数据范围：
        <el-tag :type="isAdmin ? 'success' : 'info'" size="small">{{ isAdmin ? '全部数据' : '仅本人归属数据' }}</el-tag>
      </p>
    </div>
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="hover" class="quick-card" @click="router.push('/business/client')">
          <el-icon size="28" color="#409EFF"><OfficeBuilding /></el-icon>
          <div>客户端管理</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="quick-card" @click="router.push('/business/apikey')">
          <el-icon size="28" color="#E6A23C"><Key /></el-icon>
          <div>ApiKey 管理</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="quick-card" @click="router.push('/business/session')">
          <el-icon size="28" color="#67C23A"><Monitor /></el-icon>
          <div>运行中会话</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="quick-card" @click="router.push('/business/apilog')">
          <el-icon size="28" color="#909399"><Tickets /></el-icon>
          <div>调用记录</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.quick-card {
  cursor: pointer;
  text-align: center;
  border-radius: var(--app-card-radius);
  padding: 8px 0;
}
.quick-card div {
  margin-top: 8px;
  font-size: 14px;
}
</style>
