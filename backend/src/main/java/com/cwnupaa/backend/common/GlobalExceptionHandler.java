package com.cwnupaa.backend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Maps validation and database constraints to useful responses without logging submitted values. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 兜底文案：只在无法识别具体约束时使用。 */
    private static final String GENERIC_MESSAGE = "数据保存失败，请检查填写内容后重试；如仍失败请联系管理员";

    private static final Pattern CONSTRAINT_NAME = Pattern.compile("constraint \"([^\"]+)\"");
    private static final Pattern COLUMN_NAME = Pattern.compile("column \"([^\"]+)\"");
    private static final Pattern VARCHAR_LIMIT = Pattern.compile("character varying\\((\\d+)\\)");

    /** 约束名 → 用户可见提示。 */
    private static final Map<String, String> CONSTRAINT_MESSAGES = new LinkedHashMap<>();

    /** 非空约束的列名 → 字段中文名，用于“XX 不能为空”一类提示。 */
    private static final Map<String, String> COLUMN_LABELS = new LinkedHashMap<>();

    static {
        // users：注册 / 激活 / 资料修改
        CONSTRAINT_MESSAGES.put("uk_users_useraccount", "用户名已存在，请更换后重试");
        CONSTRAINT_MESSAGES.put("uk_users_useraccount_lower", "用户名已存在，请更换后重试");
        CONSTRAINT_MESSAGES.put("uk_users_email_lower", "邮箱已被注册，请更换后重试");
        // Compatibility mapping for databases that enforce unique phone numbers.
        CONSTRAINT_MESSAGES.put("uk_users_phone", "手机号已被注册，请更换后重试");
        CONSTRAINT_MESSAGES.put("ck_users_user_account_format", "用户名需为3-20个英文字母或数字，且至少包含1个英文字母");
        CONSTRAINT_MESSAGES.put("ck_users_gender", "性别取值不合法，请重新选择");
        CONSTRAINT_MESSAGES.put("ck_users_enrolled", "在校状态取值不合法，请刷新页面后重试");
        CONSTRAINT_MESSAGES.put("fk_users_major", "所选专业不存在或已被调整，请返回上一步重新选择学校与专业");
        CONSTRAINT_MESSAGES.put("fk_users_avatar", "头像文件无效，请重新上传");
        CONSTRAINT_MESSAGES.put("fk_users_background", "背景图文件无效，请重新上传");
        CONSTRAINT_MESSAGES.put("fk_users_id_photo", "证件照文件无效，请重新上传");
        CONSTRAINT_MESSAGES.put("fk_users_student_card", "学生证文件无效，请重新上传");
        CONSTRAINT_MESSAGES.put("pk_users", "系统数据序列异常（用户主键冲突），请联系管理员修复主键序列");

        // 文件
        CONSTRAINT_MESSAGES.put("uk_file_url", "该文件已上传过，请勿重复提交");
        CONSTRAINT_MESSAGES.put("fk_file_uploader", "上传者信息无效，请重新登录后再上传");

        // 学校 / 学院 / 专业（注册时后端会自动补建同名院校与专业）
        CONSTRAINT_MESSAGES.put("uk_university_name", "该学校/单位已存在，请刷新页面后重试");
        CONSTRAINT_MESSAGES.put("uk_school_name_uni", "该学校下已存在同名学院，请刷新页面后重试");
        CONSTRAINT_MESSAGES.put("uk_major_school_name", "该学院下已存在同名专业，请刷新页面后重试");

        // 报名 / 队伍 / 申请
        CONSTRAINT_MESSAGES.put("uk_saa_one_pending_per_user", "你已有一条待审核的申请，请勿重复提交");
        CONSTRAINT_MESSAGES.put("uk_team_member_team_user", "该成员已在队伍中，请勿重复添加");
        CONSTRAINT_MESSAGES.put("uk_signup_team_detail_signup_user", "该队员已报名该项目，请勿重复添加");
        CONSTRAINT_MESSAGES.put("uk_teamname", "队伍名称已存在，请更换后重试");
        CONSTRAINT_MESSAGES.put("uk_sp_event_user", "你已报名该赛事，请勿重复提交");
        CONSTRAINT_MESSAGES.put("uk_st_event_team", "该队伍已报名该赛事，请勿重复提交");
        CONSTRAINT_MESSAGES.put("uk_st_event_team_name", "该赛事下已存在同名队伍，请更换队伍名称");
        CONSTRAINT_MESSAGES.put("uk_ts_train_user", "你已报名该培训，请勿重复提交");
        CONSTRAINT_MESSAGES.put("uk_training_teacher", "该培训已关联该教师，请勿重复添加");

        // 证书 / 其他
        CONSTRAINT_MESSAGES.put("uk_certificate_issue_no", "证书编号已存在，请更换后重试");
        CONSTRAINT_MESSAGES.put("uk_certificate_issue_member_student", "该成员已在本批次证书中，请勿重复添加");
        CONSTRAINT_MESSAGES.put("uk_cert_level_name", "证书等级名称已存在，请更换后重试");
        CONSTRAINT_MESSAGES.put("uk_term_name", "期次名称已存在，请更换后重试");
        CONSTRAINT_MESSAGES.put("uk_user_id", "该用户已存在关联记录，请勿重复提交");
        CONSTRAINT_MESSAGES.put("uk_opr_unique", "该平台记录已存在，请勿重复提交");

        // 非空约束列名 → 字段中文名
        COLUMN_LABELS.put("user_account", "用户名");
        COLUMN_LABELS.put("user_name", "昵称");
        COLUMN_LABELS.put("real_name", "真实姓名");
        COLUMN_LABELS.put("number_id", "学号/工号");
        COLUMN_LABELS.put("email", "邮箱");
        COLUMN_LABELS.put("phone", "手机号");
        COLUMN_LABELS.put("password", "密码");
        COLUMN_LABELS.put("role", "身份类型");
        COLUMN_LABELS.put("account_status", "账号状态");
        COLUMN_LABELS.put("edu_bg", "身份类型");
        COLUMN_LABELS.put("major_id", "专业");
        COLUMN_LABELS.put("grade_year", "年级/年份");
        COLUMN_LABELS.put("class_name", "班级");
        COLUMN_LABELS.put("gender", "性别");
        COLUMN_LABELS.put("is_enrolled_stu", "在校状态");
        COLUMN_LABELS.put("uploader_id", "上传者");
        COLUMN_LABELS.put("university_id", "学校/单位");
        COLUMN_LABELS.put("school_id", "学院");
        COLUMN_LABELS.put("school_name", "学院名称");
        COLUMN_LABELS.put("major_name", "专业名称");
        COLUMN_LABELS.put("url", "文件地址");
        COLUMN_LABELS.put("file_name", "文件名");
        COLUMN_LABELS.put("title", "标题");
        COLUMN_LABELS.put("name", "名称");
        COLUMN_LABELS.put("user_id", "用户");
        COLUMN_LABELS.put("event_id", "赛事");
        COLUMN_LABELS.put("team_id", "队伍");
        COLUMN_LABELS.put("training_id", "培训");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationExceptions(MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        String message = bindingResult.getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.error(message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<String> handleIllegalArgumentException(IllegalArgumentException ex) {
        return Result.error(ex.getMessage());
    }

    // Multipart resolution happens before the controller, including Excel import endpoints.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<String>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(413).body(Result.error(413,
                "上传文件过大：图片最多5MB，Excel最多10MB，其他文件最多20MB（批量邮件Excel最多5MB）"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<String>> handleMultipartError(MultipartException ex) {
        log.warn("上传请求解析失败：{}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(Result.error(400, "上传请求格式无效，请重新选择文件后上传"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Result<String>> handleMissingUploadPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest().body(Result.error(400, "请先选择要上传的文件"));
    }

    /**
     * 数据库约束失败：先按约束名给出准确文案，再按 SQLState 兜底，最后才是通用文案。
     * 日志仅记录 SQLState 和约束名，不记录提交值或完整数据库异常。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<String> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String detail = collectMessages(ex);
        String constraint = extract(CONSTRAINT_NAME, detail);
        String sqlState = resolveSqlState(ex);

        log.warn("数据库写入失败：sqlState={} constraint={}", sqlState, constraint);

        String message = resolveByConstraintName(constraint);
        if (message == null) {
            message = resolveBySqlState(sqlState, detail);
        }
        return Result.error(409, message);
    }

    /** 约束名精确匹配优先，其次按包含关系匹配（兼容 *_lower 之类的变体命名）。 */
    private static String resolveByConstraintName(String constraint) {
        if (constraint == null) {
            return null;
        }
        String exact = CONSTRAINT_MESSAGES.get(constraint);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, String> entry : CONSTRAINT_MESSAGES.entrySet()) {
            if (constraint.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 未登记约束名时，按 PostgreSQL SQLState 给出可操作的提示。 */
    private static String resolveBySqlState(String sqlState, String detail) {
        if (sqlState == null) {
            return GENERIC_MESSAGE;
        }
        switch (sqlState) {
            case "23502": { // not-null violation
                String column = extract(COLUMN_NAME, detail);
                String label = column == null ? null : COLUMN_LABELS.get(column);
                return label != null ? label + "不能为空，请返回检查必填项" : "有必填信息缺失，请返回检查后重试";
            }
            case "22001": { // value too long for type character varying(N)
                String limit = extract(VARCHAR_LIMIT, detail);
                String fields = "昵称、真实姓名、学号/工号、班级、邮箱";
                return limit != null
                        ? "填写内容长度超出限制（最多 " + limit + " 个字符），请检查" + fields + "等字段"
                        : "填写内容长度超出限制，请检查" + fields + "等字段";
            }
            case "23503":
                return "关联的数据不存在或已被删除，请刷新页面后重试";
            case "23514":
                return "填写内容不符合系统规则，请检查后重试";
            case "23505":
                return "该内容已存在，请勿重复提交";
            default:
                if (sqlState.startsWith("22")) {
                    return "填写内容格式不正确，请检查后重试";
                }
                return GENERIC_MESSAGE;
        }
    }

    /** 汇总整条异常链的消息，避免只取最外层或最内层而丢掉约束名。 */
    private static String collectMessages(Throwable ex) {
        StringBuilder builder = new StringBuilder();
        Throwable current = ex;
        int guard = 0;
        while (current != null && guard++ < 20) {
            String message = current.getMessage();
            if (message != null && !message.isEmpty() && builder.indexOf(message) < 0) {
                if (builder.length() > 0) {
                    builder.append(" | ");
                }
                builder.append(message);
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private static String extract(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String resolveSqlState(Throwable ex) {
        Throwable current = ex;
        int guard = 0;
        while (current != null && guard++ < 20) {
            if (current instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }
}
