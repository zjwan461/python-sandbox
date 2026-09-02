/** 通用格式化工具 */

/** Element Plus DatePicker value-format 统一口径：后端 Jackson 为 yyyy-MM-dd HH:mm:ss */
export const DATETIME_FORMAT = 'YYYY-MM-DD HH:mm:ss'

/** ApiKey 掩码展示：前缀 + ****后四位 */
export function maskKey(prefix?: string, suffix?: string): string {
  if (!prefix && !suffix) return '-'
  return `${prefix || ''}****${suffix || ''}`
}

/** 布尔/数字 0-1 标志转文案 */
export function flagText(v?: number, yes = '是', no = '否'): string {
  return v === 1 ? yes : no
}
