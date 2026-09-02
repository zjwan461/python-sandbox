/**
 * v-permission 按钮权限指令（T-0033，design.md §9.4）。
 *
 * 用法：
 *   v-permission="['apikey:edit']"          —— 任意匹配（默认）
 *   v-permission:any="['a:b','c:d']"        —— 任意匹配
 *   v-permission:all="['a:b','c:d']"        —— 全部匹配
 * 不匹配时移除 DOM 节点；后端 @SaCheckPermission 仍独立校验，隐藏不能绕过授权。
 */
import type { App, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

function check(binding: DirectiveBinding<string[]>): boolean {
  const required = binding.value || []
  if (required.length === 0) return true
  const userStore = useUserStore()
  // 超级管理员放行（拥有 * 或由后端权限集全含，前端按 permissions 判定即可；超管权限集为全量）
  const owned = userStore.permissions
  if (owned.has('*:*:*')) return true
  const mode = binding.arg === 'all' ? 'all' : 'any'
  if (mode === 'all') {
    return required.every((p) => owned.has(p))
  }
  return required.some((p) => owned.has(p))
}

function apply(el: HTMLElement, binding: DirectiveBinding<string[]>): void {
  if (!check(binding)) {
    el.parentNode?.removeChild(el)
  }
}

export function setupPermissionDirective(app: App): void {
  app.directive('permission', {
    mounted: apply
  })
}
