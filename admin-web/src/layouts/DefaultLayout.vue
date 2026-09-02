<script setup lang="ts">
/**
 * Default Layout（T-0011/T-0042，design.md §9.3）：左侧菜单 + 顶部导航 + 主内容区。
 * 菜单数据来自 usePermissionStore.menuTree（后端 /menus/routes），
 * isVisible=0 节点不显示；图标经全局注册的 Element Plus 图标动态取用。
 * T-0042（FR-SYS-03）：顶栏公告铃铛 + 未读徽标，点击展开未读公告通栏，
 * 打开即标记已读；未到生效时间/超过失效时间的公告后端不投递。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { markNoticeRead, noticeInbox, noticeUnreadCount } from '@/api/sys'
import type { SysNoticeVO } from '@/utils/types'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { resetDynamicRoutes } from '@/router'
import SideMenu from './components/SideMenu.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permStore = usePermissionStore()

const activeMenu = computed(() => route.path)
const nickname = computed(() => userStore.userInfo?.nickname || userStore.userInfo?.username || '')

// ===== 公告通栏（T-0042）=====
const unread = ref(0)
const notices = ref<SysNoticeVO[]>([])
const noticeVisible = ref(false)
const bannerDismissed = ref(false)
let pollTimer: number | undefined

async function refreshNotices() {
  try {
    unread.value = await noticeUnreadCount()
    if (unread.value > 0 && !bannerDismissed.value) {
      notices.value = (await noticeInbox()).filter((n) => !n.read)
    }
  } catch {
    /* 公告不可用不影响主布局 */
  }
}

async function openNoticePopover() {
  notices.value = await noticeInbox()
  noticeVisible.value = true
}

async function readNotice(n: SysNoticeVO) {
  await markNoticeRead(n.id)
  n.read = true
  unread.value = Math.max(0, unread.value - 1)
}

const bannerNotices = computed(() => notices.value.filter((n) => !n.read).slice(0, 3))

onMounted(() => {
  refreshNotices()
  pollTimer = window.setInterval(refreshNotices, 60_000)
})
onBeforeUnmount(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})

async function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await userStore.logout()
    permStore.resetRoutes()
    // 同步移除已注册动态路由并重置初始化缓存，保证重新登录后路由重建
    resetDynamicRoutes()
    router.replace('/login')
  } else if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'password') {
    router.push('/change-password')
  }
}
</script>

<template>
  <el-container style="height: 100vh">
    <el-aside :width="'var(--app-sidebar-width)'" style="background: #304156">
      <div class="logo">
        <span>Sandbox 管理端</span>
      </div>
      <SideMenu :menu-tree="permStore.menuTree" :active="activeMenu" />
    </el-aside>
    <el-container>
      <el-header
        height="var(--app-header-height)"
        style="background: #fff; box-shadow: var(--app-shadow); display: flex; align-items: center; justify-content: space-between; padding: 0 16px"
      >
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="route.meta?.title">{{ route.meta.title }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div style="display: flex; align-items: center; gap: 16px">
          <!-- 公告铃铛+站内信（T-0042，FR-SYS-03） -->
          <el-popover v-model:visible="noticeVisible" placement="bottom-end" :width="360" trigger="click" popper-class="notice-popover">
            <template #reference>
              <el-badge :value="unread" :hidden="unread === 0">
                <el-icon :size="18" style="cursor: pointer" @click="openNoticePopover"><Bell /></el-icon>
              </el-badge>
            </template>
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
              <b>公告（{{ notices.length }}）</b>
              <el-button link size="small" @click="noticeVisible = false">收起</el-button>
            </div>
            <el-empty v-if="!notices.length" description="暂无有效公告" :image-size="60" />
            <div v-for="n in notices" :key="n.id" class="notice-item" @click="readNotice(n)">
              <div>
                <el-tag v-if="n.top" type="danger" size="small" style="margin-right: 4px">置顶</el-tag>
                <el-tag v-if="!n.read" type="warning" size="small" style="margin-right: 4px">未读</el-tag>
                <span :style="{ fontWeight: n.read ? 400 : 600 }">{{ n.title }}</span>
              </div>
              <div class="notice-content">{{ n.content }}</div>
              <div class="notice-meta">{{ n.publisherName || '-' }} · {{ n.publishTime || '' }}</div>
            </div>
          </el-popover>
          <el-dropdown @command="handleCommand">
            <span style="cursor: pointer; display: inline-flex; align-items: center; gap: 6px">
              <el-avatar :size="26">{{ nickname.charAt(0) }}</el-avatar>
              {{ nickname }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <!-- 登录后未读公告通栏（T-0042，FR-SYS-03） -->
      <div v-if="bannerNotices.length && !bannerDismissed" class="notice-banner">
        <el-icon><Bell /></el-icon>
        <span v-for="n in bannerNotices" :key="n.id" class="banner-item" @click="openNoticePopover">
          {{ n.top ? '【置顶】' : '' }}{{ n.title }}
        </span>
        <el-icon class="banner-close" @click="bannerDismissed = true"><Close /></el-icon>
      </div>
      <el-main style="padding: 0; background: var(--app-bg)">
        <!-- keep-alive 与普通渲染必须整棵子树互斥（v-if/v-else），
             同一 Component vnode 不能同时出现在 keep-alive 内外两个分支，
             否则切换时 keep-alive 对已被接管的实例 deactivate → parentComponent.ctx.deactivate 报错 -->
        <router-view v-slot="{ Component }">
          <keep-alive v-if="route.meta?.keepAlive !== false">
            <component :is="Component" :key="route.path" />
          </keep-alive>
          <component :is="Component" v-else :key="route.path" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.logo {
  height: var(--app-header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
}
.notice-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 16px;
  background: #fdf6ec;
  border-bottom: 1px solid #faecd8;
  color: #b88230;
  font-size: 13px;
  overflow: hidden;
}
.banner-item {
  cursor: pointer;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  max-width: 420px;
}
.banner-item:hover {
  text-decoration: underline;
}
.banner-close {
  margin-left: auto;
  cursor: pointer;
}
.notice-item {
  padding: 8px 4px;
  border-bottom: 1px dashed #ebeef5;
  cursor: pointer;
}
.notice-item:hover {
  background: #f5f7fa;
}
.notice-content {
  font-size: 12px;
  color: #606266;
  margin-top: 2px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.notice-meta {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
</style>
