package io.github.sandbox.admin.rbac.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户 CSV 批量导入结果（T-0043，FR-USER-07）。
 *
 * <p>逐行反馈：成功创建的用户名与失败行（行号+原因）明确表达，
 * 重复用户名/非法字段/缺失必填项不静默覆盖既有数据（验收）。</p>
 */
@Data
public class UserImportResultVO {

    /** 成功导入的用户名列表 */
    private List<String> successUsernames = new ArrayList<>();

    /** 失败明细（1 起行号，含表头偏移由服务端统一按数据行计） */
    private List<RowError> failures = new ArrayList<>();

    /** 处理总行数 */
    private int total;

    public void addFailure(int row, String reason) {
        RowError e = new RowError();
        e.setRow(row);
        e.setReason(reason);
        failures.add(e);
    }

    @Data
    public static class RowError {
        /** 数据行号（不含表头，1 起） */
        private int row;
        /** 失败原因 */
        private String reason;
    }
}
