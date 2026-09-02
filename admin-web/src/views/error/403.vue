<script setup lang="ts">
/** 403 页（静态路由，Blank Layout 风格） */
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="blank-layout">
    <el-result icon="warning" title="403" sub-title="抱歉，您没有权限访问该页面">
      <template #extra>
        <el-button type="primary" @click="router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>
