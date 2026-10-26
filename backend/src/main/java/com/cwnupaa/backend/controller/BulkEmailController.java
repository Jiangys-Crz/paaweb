package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.BulkEmailSendRequestDTO;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.service.EmailService;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.BulkEmailParseResultVO;
import com.cwnupaa.backend.vo.BulkEmailRecipientVO;
import com.cwnupaa.backend.vo.BulkEmailSendResultVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/admin/bulk-email")
public class BulkEmailController {
    private static final Logger log = LoggerFactory.getLogger(BulkEmailController.class);
    private static final int MAX_RECIPIENTS = 10000;
    /** 失败原因写入失败名单前的最大长度，避免 SMTP 长报文撑爆响应体。 */
    private static final int MAX_FAILURE_REASON_LENGTH = 120;
    private static final long MAX_IMPORT_FILE_SIZE = 5L * 1024 * 1024;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Autowired
    private EmailService emailService;
    @Autowired
    private UserMapper userMapper;

    @GetMapping("/template")
    public void downloadTemplate(HttpServletRequest request, HttpServletResponse response) throws Exception {
        requireSuperAdmin(request);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("批量邮件发送名单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("姓名");
            header.createCell(1).setCellValue("邮件地址");
            header.createCell(2).setCellValue("用户名");
            header.createCell(3).setCellValue("账号");
            header.createCell(4).setCellValue("学号/工号");

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("张三");
            sample.createCell(1).setCellValue("zhangsan@example.com");
            sample.createCell(2).setCellValue("张三");
            sample.createCell(3).setCellValue("zhangsan");
            sample.createCell(4).setCellValue("20240001");

            sheet.setColumnWidth(0, 20 * 256);
            sheet.setColumnWidth(1, 32 * 256);

            String fileName = URLEncoder.encode("批量邮件发送模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
            try (ServletOutputStream out = response.getOutputStream()) {
                workbook.write(out);
            }
        }
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<BulkEmailParseResultVO> parseRecipients(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        try {
            requireSuperAdmin(request);
            if (file == null || file.isEmpty()) return Result.error("请上传 Excel 文件");
            if (file.getSize() > MAX_IMPORT_FILE_SIZE) return Result.error("Excel 文件大小不能超过 5MB");
            String originalFilename = file.getOriginalFilename();
            String lowerFilename = originalFilename == null ? "" : originalFilename.toLowerCase();
            if (!(lowerFilename.endsWith(".xlsx") || lowerFilename.endsWith(".xls"))) {
                return Result.error("仅支持 .xls 或 .xlsx 文件");
            }

            BulkEmailParseResultVO result = new BulkEmailParseResultVO();
            List<BulkEmailRecipientVO> recipients = new ArrayList<>();
            List<BulkEmailRecipientVO> invalidRecipients = new ArrayList<>();

            try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
                if (sheet == null || sheet.getPhysicalNumberOfRows() <= 1) return Result.error("Excel 中没有可发送的名单");

                DataFormatter formatter = new DataFormatter();
                Row header = sheet.getRow(sheet.getFirstRowNum());
                int nameColumn = findHeaderColumn(header, formatter, "姓名", "用户名", "name", "user_name");
                int emailColumn = findHeaderColumn(header, formatter, "邮件地址", "邮箱", "邮件", "email", "mail");
                if (nameColumn < 0) nameColumn = 0;
                if (emailColumn < 0) emailColumn = 1;
                Map<Integer, String> headers = readHeaders(header, formatter);
                headers.putIfAbsent(nameColumn, "姓名");
                headers.putIfAbsent(emailColumn, "邮件地址");
                Set<String> keywords = new LinkedHashSet<>(headers.values());

                for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    String name = trimCell(row.getCell(nameColumn), formatter);
                    String email = trimCell(row.getCell(emailColumn), formatter);
                    if (!StringUtils.hasText(name) && !StringUtils.hasText(email)) continue;

                    Map<String, String> fields = new LinkedHashMap<>();
                    for (Map.Entry<Integer, String> entry : headers.entrySet()) {
                        fields.put(entry.getValue(), trimCell(row.getCell(entry.getKey()), formatter));
                    }
                    fields.putIfAbsent("姓名", name);
                    fields.putIfAbsent("邮件地址", email);
                    BulkEmailRecipientVO recipient = newRecipient(i + 1, name, email, null, fields);
                    String error = validateRecipient(recipient);
                    if (error == null) {
                        recipients.add(recipient);
                    } else {
                        recipient.setError(error);
                        invalidRecipients.add(recipient);
                    }

                    if (recipients.size() + invalidRecipients.size() > MAX_RECIPIENTS) {
                        return Result.error("单次最多支持 " + MAX_RECIPIENTS + " 位收件人");
                    }
                }
                result.setKeywords(new ArrayList<>(keywords));
            }

            result.setRecipients(recipients);
            result.setInvalidRecipients(invalidRecipients);
            return Result.success(result);
        } catch (SecurityException e) {
            return Result.error(403, e.getMessage());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BulkEmailController.class).warn("操作失败，请检查请求参数和服务配置");
            return Result.error("Excel 解析失败，请检查文件内容");
        }
    }

    @GetMapping("/users/search")
    public Result<List<BulkEmailRecipientVO>> searchSiteUsers(@RequestParam(required = false) String keyword, HttpServletRequest request) {
        try {
            requireSuperAdmin(request);
            QueryWrapper<Users> wrapper = baseUserEmailWrapper();
            if (StringUtils.hasText(keyword)) {
                String key = keyword.trim();
                wrapper.and(w -> w.like("real_name", key)
                        .or().like("user_name", key)
                        .or().like("number_id", key)
                        .or().like("user_account", key)
                        .or().like("email", key));
            }
            wrapper.orderByDesc("register_time").last("LIMIT 20");
            return Result.success(userMapper.selectList(wrapper).stream()
                    .map(this::toRecipient)
                    .filter(item -> validateRecipient(item) == null)
                    .toList());
        } catch (SecurityException e) {
            return Result.error(403, e.getMessage());
        }
    }

    @GetMapping("/users/all")
    public Result<List<BulkEmailRecipientVO>> allSiteUsers(HttpServletRequest request) {
        try {
            requireSuperAdmin(request);
            QueryWrapper<Users> wrapper = baseUserEmailWrapper();
            wrapper.orderByAsc("user_id").last("LIMIT " + MAX_RECIPIENTS);
            return Result.success(userMapper.selectList(wrapper).stream()
                    .map(this::toRecipient)
                    .filter(item -> validateRecipient(item) == null)
                    .toList());
        } catch (SecurityException e) {
            return Result.error(403, e.getMessage());
        }
    }

    @PostMapping("/users/batch")
    public Result<List<BulkEmailRecipientVO>> batchSiteUsers(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        try {
            requireSuperAdmin(request);
            String raw = payload == null ? null : payload.get("usernames");
            if (!StringUtils.hasText(raw)) return Result.error("请输入用户名，每行一个");
            List<String> accounts = raw.lines()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(MAX_RECIPIENTS)
                    .toList();
            if (accounts.isEmpty()) return Result.error("没有可识别的用户名");
            QueryWrapper<Users> wrapper = baseUserEmailWrapper();
            wrapper.select("user_id", "user_account", "real_name", "user_name", "number_id", "email")
                    .and(w -> {
                        for (int i = 0; i < accounts.size(); i++) {
                            String account = accounts.get(i).toLowerCase();
                            if (i == 0) w.apply("LOWER(user_account) = {0}", account);
                            else w.or().apply("LOWER(user_account) = {0}", account);
                        }
                    });
            return Result.success(userMapper.selectList(wrapper).stream()
                    .map(this::toRecipient)
                    .filter(item -> validateRecipient(item) == null)
                    .toList());
        } catch (SecurityException e) {
            return Result.error(403, e.getMessage());
        }
    }

    @PostMapping("/send")
    public Result<BulkEmailSendResultVO> send(@RequestBody BulkEmailSendRequestDTO dto, HttpServletRequest request) {
        int successCount = 0;
        List<BulkEmailRecipientVO> failedRecipients = new ArrayList<>();
        long startedAt = System.currentTimeMillis();
        try {
            requireSuperAdmin(request);
            if (dto == null) return Result.error("请求参数不能为空");
            if (!StringUtils.hasText(dto.getSubject())) return Result.error("邮件主题不能为空");
            if (!StringUtils.hasText(dto.getHtmlContent())) return Result.error("邮件模板不能为空");
            if (dto.getRecipients() == null || dto.getRecipients().isEmpty()) return Result.error("发送名单不能为空");
            if (dto.getRecipients().size() > MAX_RECIPIENTS) return Result.error("单次最多支持 " + MAX_RECIPIENTS + " 位收件人");

            String subject = dto.getSubject().trim();
            String safeHtml = sanitizeEmailHtml(renderMarkdown(dto.getHtmlContent()));
            int total = dto.getRecipients().size();
            log.info("批量邮件开始发送：收件人 {} 人", total);

            for (BulkEmailRecipientVO recipient : dto.getRecipients()) {
                String error = validateRecipient(recipient);
                if (error != null) {
                    failedRecipients.add(newRecipient(recipient.getRowNumber(), recipient.getName(), recipient.getEmail(), error));
                    continue;
                }
                try {
                    String content = replacePlaceholders(safeHtml, recipient);
                    String personalizedSubject = replaceTextPlaceholders(subject, recipient);
                    emailService.sendBulkHtmlEmail(recipient.getEmail().trim(), personalizedSubject, content);
                    successCount++;
                    // 每 100 封打一次进度，方便在几百上千封的长任务里判断"是不是卡住了"。
                    if (successCount % 100 == 0) {
                        log.info("批量邮件发送进度：已成功 {} / {} 封，耗时 {} ms", successCount, total, System.currentTimeMillis() - startedAt);
                    }
                } catch (Exception e) {
                    // 单封失败不影响后续：记录真实原因并继续循环。
                    String reason = failureReason(e);
                    log.warn("批量邮件发送失败：{}", e.getClass().getSimpleName());
                    failedRecipients.add(newRecipient(recipient.getRowNumber(), recipient.getName(), recipient.getEmail(), reason));
                }
            }

            BulkEmailSendResultVO result = new BulkEmailSendResultVO();
            result.setSuccessCount(successCount);
            result.setFailedCount(failedRecipients.size());
            result.setFailedRecipients(failedRecipients);
            log.info("批量邮件发送完成：成功 {} 封，失败 {} 封，共 {} 人，耗时 {} ms",
                    successCount, failedRecipients.size(), total, System.currentTimeMillis() - startedAt);
            return Result.success(result);
        } catch (SecurityException e) {
            return Result.error(403, e.getMessage());
        } catch (Exception e) {
            log.error("批量邮件发送异常终止：已成功 {} 封，已失败 {} 封 ({})", successCount, failedRecipients.size(), e.getClass().getSimpleName());
            return Result.error("批量发送失败");
        }
    }

    /** 把 SMTP 抛出的异常整理成可读、可落 Excel 的失败原因。 */
    private String failureReason(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) message = e.getClass().getSimpleName();
        message = message.replaceAll("\\s+", " ").trim();
        if (message.length() > MAX_FAILURE_REASON_LENGTH) {
            message = message.substring(0, MAX_FAILURE_REASON_LENGTH) + "…";
        }
        return "邮件发送失败：" + message;
    }

    private void requireSuperAdmin(HttpServletRequest request) {
        UserProfileVO user = SecurityUtils.currentUser(request);
        if (user == null) throw new SecurityException("未登录或会话已失效");
        if (!"supper_admin".equals(user.getRole()) && !"super_admin".equals(user.getRole())) {
            throw new SecurityException("仅超级管理员可使用批量邮件发送功能");
        }
    }

    private int findHeaderColumn(Row header, DataFormatter formatter, String... names) {
        if (header == null) return -1;
        for (Cell cell : header) {
            String value = trimCell(cell, formatter).toLowerCase();
            for (String name : names) {
                if (value.contains(name.toLowerCase())) {
                    return cell.getColumnIndex();
                }
            }
        }
        return -1;
    }

    private String trimCell(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        return formatter.formatCellValue(cell).trim();
    }

    private Map<Integer, String> readHeaders(Row header, DataFormatter formatter) {
        Map<Integer, String> headers = new LinkedHashMap<>();
        if (header == null) return headers;
        Set<String> used = new LinkedHashSet<>();
        for (Cell cell : header) {
            String value = trimCell(cell, formatter);
            if (!StringUtils.hasText(value)) continue;
            String key = value;
            int suffix = 2;
            while (!used.add(key)) key = value + "_" + suffix++;
            headers.put(cell.getColumnIndex(), key);
        }
        return headers;
    }

    private String validateRecipient(BulkEmailRecipientVO recipient) {
        if (recipient == null) return "收件人为空";
        if (!StringUtils.hasText(recipient.getName())) return "姓名不能为空";
        if (!StringUtils.hasText(recipient.getEmail())) return "邮件地址不能为空";
        if (!EMAIL_PATTERN.matcher(recipient.getEmail().trim()).matches()) return "邮件地址格式不正确";
        return null;
    }

    private BulkEmailRecipientVO newRecipient(Integer rowNumber, String name, String email, String error) {
        return newRecipient(rowNumber, name, email, error, new LinkedHashMap<>());
    }

    private BulkEmailRecipientVO newRecipient(Integer rowNumber, String name, String email, String error, Map<String, String> fields) {
        BulkEmailRecipientVO recipient = new BulkEmailRecipientVO();
        recipient.setRowNumber(rowNumber);
        recipient.setName(name == null ? "" : name.trim());
        recipient.setEmail(email == null ? "" : email.trim());
        recipient.setError(error);
        recipient.setFields(fields);
        return recipient;
    }

    private QueryWrapper<Users> baseUserEmailWrapper() {
        QueryWrapper<Users> wrapper = new QueryWrapper<>();
        wrapper.select("user_id", "user_account", "real_name", "user_name", "number_id", "email")
                .isNotNull("email")
                .ne("email", "");
        return wrapper;
    }

    private BulkEmailRecipientVO toRecipient(Users user) {
        BulkEmailRecipientVO recipient = new BulkEmailRecipientVO();
        recipient.setUserId(user.getUserId());
        recipient.setName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUserName());
        recipient.setEmail(user.getEmail());
        recipient.setNumberId(user.getNumberId());
        Map<String, String> fields = new LinkedHashMap<>();
        putField(fields, "姓名", recipient.getName());
        putField(fields, "name", recipient.getName());
        putField(fields, "邮件地址", recipient.getEmail());
        putField(fields, "邮箱", recipient.getEmail());
        putField(fields, "email", recipient.getEmail());
        putField(fields, "用户名", user.getUserName());
        putField(fields, "userName", user.getUserName());
        putField(fields, "账号", user.getUserAccount());
        putField(fields, "userAccount", user.getUserAccount());
        putField(fields, "学号/工号", user.getNumberId());
        putField(fields, "numberId", user.getNumberId());
        recipient.setFields(fields);
        return recipient;
    }

    private void putField(Map<String, String> fields, String key, String value) {
        if (StringUtils.hasText(value)) fields.put(key, value);
    }

    private String sanitizeEmailHtml(String html) {
        Safelist safelist = Safelist.relaxed()
                .addTags("div", "span", "table", "thead", "tbody", "tfoot", "tr", "th", "td")
                .addAttributes(":all", "style", "class")
                .addAttributes("table", "border", "cellpadding", "cellspacing", "width")
                .addAttributes("td", "colspan", "rowspan", "width")
                .addAttributes("th", "colspan", "rowspan", "width");
        String cleaned = Jsoup.clean(html, safelist);
        Document document = Jsoup.parseBodyFragment(cleaned);
        for (Element table : document.select("table")) {
            appendStyle(table, "width: 100%; border-collapse: collapse; margin: 12px 0;");
        }
        for (Element cell : document.select("th, td")) {
            appendStyle(cell, "border: 1px solid #d9d9d9; padding: 8px 10px; vertical-align: top;");
        }
        for (Element headerCell : document.select("th")) {
            appendStyle(headerCell, "background-color: #fafafa; font-weight: 600;");
        }
        return document.body().html();
    }

    private void appendStyle(Element element, String style) {
        String currentStyle = element.attr("style");
        if (StringUtils.hasText(currentStyle) && !currentStyle.trim().endsWith(";")) {
            currentStyle += ";";
        }
        element.attr("style", (currentStyle == null ? "" : currentStyle) + style);
    }

    private String renderMarkdown(String markdown) {
        List<Extension> extensions = List.of(TablesExtension.create());
        Parser parser = Parser.builder().extensions(extensions).build();
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(false).build();
        Node document = parser.parse(markdown == null ? "" : markdown);
        return renderer.render(document);
    }

    private String replacePlaceholders(String html, BulkEmailRecipientVO recipient) {
        Map<String, String> fields = recipientFields(recipient);
        return fields.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey(Comparator.comparingInt(String::length).reversed()))
                .reduce(html, (current, entry) -> {
                    String value = HtmlUtils.htmlEscape(entry.getValue() == null ? "" : entry.getValue().trim(), StandardCharsets.UTF_8.name());
                    return current.replace("{{" + entry.getKey() + "}}", value)
                            .replace("{" + entry.getKey() + "}", value);
                }, (left, right) -> right);
    }

    private String replaceTextPlaceholders(String text, BulkEmailRecipientVO recipient) {
        Map<String, String> fields = recipientFields(recipient);
        return fields.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey(Comparator.comparingInt(String::length).reversed()))
                .reduce(text, (current, entry) -> current
                        .replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue().trim())
                        .replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue().trim()),
                        (left, right) -> right);
    }

    private Map<String, String> recipientFields(BulkEmailRecipientVO recipient) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (recipient.getFields() != null) fields.putAll(recipient.getFields());
        putField(fields, "姓名", recipient.getName());
        putField(fields, "name", recipient.getName());
        putField(fields, "邮件地址", recipient.getEmail());
        putField(fields, "email", recipient.getEmail());
        return fields;
    }
}
