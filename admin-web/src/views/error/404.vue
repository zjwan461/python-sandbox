<script setup lang="ts">
/** 404 页 */
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="blank-layout">
    <el-result icon="info" title="404" sub-title="页面不存在">
      <template #extra>
        <el-button type="primary" @click="router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>
