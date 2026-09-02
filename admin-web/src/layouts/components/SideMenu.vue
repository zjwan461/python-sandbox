<script setup lang="ts">
/**
 * 侧栏菜单递归组件：M=目录（el-sub-menu），C=菜单（el-menu-item）。
 * isVisible=0 不渲染；按钮（F）后端 routes 已过滤，不在此处出现。
 */
import type { MenuRouteVO } from '@/utils/types'

defineProps<{
  menuTree: MenuRouteVO[]
  active: string
}>()

function visible(nodes: MenuRouteVO[]): MenuRouteVO[] {
  return (nodes || []).filter((n) => n.isVisible !== 0)
}

function hasVisibleChild(n: MenuRouteVO): boolean {
  return visible(n.children || []).length > 0
}
</script>

<template>
  <el-menu
    :default-active="active"
    router
    background-color="#304156"
    text-color="#bfcbd9"
    active-text-color="#409EFF"
    :collapse-transition="false"
  >
    <el-menu-item index="/dashboard">
      <el-icon><HomeFilled /></el-icon>
      <span>工作台</span>
    </el-menu-item>
    <template v-for="node in visible(menuTree)" :key="node.id">
      <el-sub-menu v-if="node.menuType === 'M' && hasVisibleChild(node)" :index="node.routePath || String(node.id)">
        <template #title>
          <el-icon v-if="node.icon"><component :is="node.icon" /></el-icon>
          <span>{{ node.menuName }}</span>
        </template>
        <el-menu-item
          v-for="child in visible(node.children || [])"
          :key="child.id"
          :index="child.routePath || String(child.id)"
        >
          <el-icon v-if="child.icon"><component :is="child.icon" /></el-icon>
          <span>{{ child.menuName }}</span>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item v-else-if="node.menuType === 'C'" :index="node.routePath || String(node.id)">
        <el-icon v-if="node.icon"><component :is="node.icon" /></el-icon>
        <span>{{ node.menuName }}</span>
      </el-menu-item>
    </template>
  </el-menu>
</template>
