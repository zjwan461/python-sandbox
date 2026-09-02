/** 系统设置与通知公告接口（T-0041 /sys/configs、T-0042 /sys/notices + /notices/**） */
import { request } from '@/utils/request'
import type { NoticeUpsertRequest, PageResult, SysConfigItem, SysNoticeVO } from '@/utils/types'

// ===== 系统设置（sysconfig:view / sysconfig:edit）=====

export function listConfigs() {
  return request<SysConfigItem[]>({ url: '/sys/configs', method: 'get' })
}

/** 批量更新：configKey -> value（未登记键或类型非法整体拒绝 11008/11009） */
export function batchUpdateConfigs(updates: Record<string, string>) {
  return request<void>({ url: '/sys/configs/batch', method: 'put', data: updates })
}

// ===== 公告管理（notice:view/add/edit/delete）=====

export function pageNotices(params: { title?: string; status?: number; pageNum?: number; pageSize?: number }) {
  return request<PageResult<SysNoticeVO>>({ url: '/sys/notices', method: 'get', params })
}

export function createNotice(data: NoticeUpsertRequest) {
  return request<number>({ url: '/sys/notices', method: 'post', data })
}

export function updateNotice(id: number, data: NoticeUpsertRequest) {
  return request<void>({ url: `/sys/notices/${id}`, method: 'put', data })
}

export function deleteNotice(id: number) {
  return request<void>({ url: `/sys/notices/${id}`, method: 'delete' })
}

export function publishNotice(id: number) {
  return request<void>({ url: `/sys/notices/${id}/publish`, method: 'put' })
}

export function unpublishNotice(id: number) {
  return request<void>({ url: `/sys/notices/${id}/unpublish`, method: 'put' })
}

// ===== 公告投递（登录即可）=====

export function noticeInbox() {
  return request<SysNoticeVO[]>({ url: '/notices/inbox', method: 'get' })
}

export function noticeUnreadCount() {
  return request<number>({ url: '/notices/unread-count', method: 'get' })
}

export function markNoticeRead(id: number) {
  return request<void>({ url: `/notices/${id}/read`, method: 'put' })
}
