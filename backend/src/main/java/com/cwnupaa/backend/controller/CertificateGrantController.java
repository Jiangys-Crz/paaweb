package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.CertificateType;
import com.cwnupaa.backend.dto.CertificateTemplatePreviewRequestDTO;
import com.cwnupaa.backend.mapper.CertificateTypeMapper;
import com.cwnupaa.backend.service.CertificateAuthenticityService;
import com.cwnupaa.backend.service.CertificateAccessTokenService;
import com.cwnupaa.backend.service.CertificateExcelImportService;
import com.cwnupaa.backend.service.CertificateGrantService;
import com.cwnupaa.backend.service.CertificateTemplateRenderService;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.CertificateAuthenticityVO;
import com.cwnupaa.backend.vo.CertificateImportPreviewVO;
import com.cwnupaa.backend.vo.CertificateImportResultVO;
import com.cwnupaa.backend.vo.CertificateGrantRecordVO;
import com.cwnupaa.backend.vo.CertificateGrantVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/certificate/grant")
public class CertificateGrantController {

    @Autowired
    private CertificateGrantService grantService;
    @Autowired
    private CertificateTypeMapper typeMapper;
    @Autowired
    private CertificateExcelImportService importService;
    @Autowired
    private CertificateTemplateRenderService templateRenderService;
    @Autowired
    private CertificateAuthenticityService authenticityService;
    @Autowired
    private CertificateAccessTokenService accessTokenService;
    @Autowired private com.cwnupaa.backend.service.FileAccessPolicy fileAccessPolicy;
    @Autowired private com.cwnupaa.backend.mapper.FileSystemMapper fileSystemMapper;

    private boolean canReadTemplate(Long fileId, UserProfileVO actor) {
        var file = fileId == null ? null : fileSystemMapper.selectById(fileId);
        if (file == null || !fileAccessPolicy.hasPermission(actor, "manage_cert_grant")) return false;
        // Existing template references may be shared by grant managers. Unbound uploads belong to their uploader.
        boolean registeredTemplate = typeMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CertificateType>().eq("url_id", fileId)) > 0;
        return fileAccessPolicy.canAccess(file, actor) && (registeredTemplate
                || (actor != null && actor.getUserId().equals(file.getUploaderId())));
    }

    @PostMapping("/template/add")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<String> createTemplate(@RequestBody CertificateType certificateType, HttpServletRequest request) {
        String error = fileAccessPolicy.validateCertificateFile(certificateType.getUrlId(), SecurityUtils.currentUser(request), true);
        if (error != null) return Result.error(error);
        return grantService.createTemplate(certificateType);
    }

    @PostMapping("/template/update")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<String> updateTemplate(@RequestBody CertificateType certificateType, HttpServletRequest request) {
        if (certificateType.getUrlId() != null) {
            CertificateType previous = typeMapper.selectById(certificateType.getCertificateTypeId());
            boolean unchanged = previous != null && certificateType.getUrlId().equals(previous.getUrlId());
            if (!unchanged) {
                String error = fileAccessPolicy.validateCertificateFile(certificateType.getUrlId(), SecurityUtils.currentUser(request), true);
                if (error != null) return Result.error(error);
            } else if (!canReadTemplate(certificateType.getUrlId(), SecurityUtils.currentUser(request))) return Result.error("无权访问证书模板");
        }
        return grantService.updateTemplate(certificateType);
    }

    @GetMapping("/template/list")
    public Result<Page<CertificateType>> getTemplateList(@RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size, @RequestParam(required = false) Long termId,    // 🟢 新增：学期筛选
                                                         @RequestParam(required = false) String keyword  // 🟢 新增：名称搜索
    ) {
        return grantService.getTemplateList(current, size, termId, keyword);
    }

    @PostMapping("/distribute")
    public Result<Object> distribute(@RequestBody Map<String, Object> params) {
        Long typeId = Long.valueOf(params.get("typeId").toString());
        String studentsStr = (String) params.get("studentIds");

        if (studentsStr == null || studentsStr.isEmpty()) return Result.error("名单为空");

        // Split by newline only to support team entries (multiple IDs in one line)
        String[] ids = studentsStr.split("\\r?\\n");
        List<String> studentIdList = Arrays.stream(ids).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());

        boolean preview = Boolean.parseBoolean(String.valueOf(params.getOrDefault("preview", false)));
        return grantService.grantCertificates(typeId, studentIdList, preview);
    }

    @GetMapping("/import/template/{kind}")
    public ResponseEntity<Resource> downloadImportTemplate(@PathVariable String kind) {
        boolean team = "team".equalsIgnoreCase(kind);
        boolean personal = "personal".equalsIgnoreCase(kind);
        if (!team && !personal) return ResponseEntity.badRequest().build();
        String resourceName = team ? "templates/团队证书发放模板.xlsx" : "templates/个人证书发放模板.xlsx";
        String downloadName = team ? "团队证书发放模板.xlsx" : "个人证书发放模板.xlsx";
        ClassPathResource resource = new ClassPathResource(resourceName);
        if (!resource.exists()) return ResponseEntity.notFound().build();
        String encoded = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<CertificateImportPreviewVO> previewImport(@RequestParam Long typeId, @RequestPart("file") MultipartFile file) {
        try {
            return Result.success(importService.preview(typeId, file));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping(value = "/import/distribute", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<CertificateImportResultVO> distributeImport(@RequestParam Long typeId,
                                                               @RequestPart("file") MultipartFile file,
                                                               HttpServletRequest request) {
        try {
            return Result.success(importService.issue(typeId, file, SecurityUtils.currentUserId(request)));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping(value = "/template/background/{fileId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<?> templateBackground(@PathVariable Long fileId, HttpServletRequest request) {
        if (!canReadTemplate(fileId, SecurityUtils.currentUser(request))) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(templateRenderService.renderBackground(fileId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(e.getMessage());
        }
    }

    @PostMapping(value = "/template/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> previewTemplate(@RequestBody CertificateTemplatePreviewRequestDTO request, HttpServletRequest servletRequest) {
        if (!canReadTemplate(request.getFileId(), SecurityUtils.currentUser(servletRequest))) return ResponseEntity.status(403).build();
        try {
            byte[] pdf = templateRenderService.renderPreview(request.getFileId(), request.getTemplateSchema(),
                    Integer.valueOf(1).equals(request.getIsTeam()), request.getIssueDate());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=certificate_template_preview.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(e.getMessage());
        }
    }

    @GetMapping("/public/verify")
    public Result<CertificateAuthenticityVO> verifyCertificate(@RequestParam String number) {
        return Result.success(authenticityService.verify(number));
    }

    /** 未登录证书模块：以“姓名 + 学校 + 学号”核验身份并签发短期访问凭证。 */
    @PostMapping("/guest/list")
    public Result<List<CertificateGrantVO>> guestList(@RequestBody Map<String, String> params,
                                                      HttpServletRequest request,
                                                      HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        String school = params.get("school");
        String numberId = params.get("numberId");
        String realName = params.get("realName");
        Result<List<CertificateGrantVO>> result = grantService.guestList(school, numberId, realName);
        if (result.getCode() != null && result.getCode() == 200 && result.getData() != null) {
            HttpSession session = request.getSession(true);
            for (CertificateGrantVO certificate : result.getData()) {
                certificate.setAccessToken(accessTokenService.issueForGuest(session,
                        certificate.getCertificateDownloadId(), school.trim(), numberId.trim(), realName.trim()));
            }
        }
        return result;
    }

    /** 未登录下载仅接受查询接口签发的短期凭证，不再信任 URL 中的身份参数或证书 ID。 */
    @GetMapping({"/guest/download", "/guest/download/{grantId}"})
    public ResponseEntity<?> guestDownloadPdf(@PathVariable(required = false) Long grantId,
                                              @RequestParam(required = false) String accessToken,
                                              HttpServletRequest request) {
        CertificateAccessTokenService.AccessTicket ticket = resolveTicket(
                request, accessToken, CertificateAccessTokenService.AccessScope.GUEST, grantId);
        if (ticket == null) return forbiddenCertificate();
        try {
            byte[] pdfBytes = grantService.generatePdfForGuest(ticket.grantId(), ticket.school(),
                    ticket.numberId(), ticket.realName());
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificate.pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
        } catch (CertificateGrantService.CertificateAccessDeniedException e) {
            return forbiddenCertificate();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CertificateGrantController.class).warn("操作失败，请检查请求参数和服务配置");
            return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN).body("证书生成失败");
        }
    }

    /** 未登录预览与下载使用相同的短期凭证校验。 */
    @GetMapping({"/guest/preview", "/guest/preview/{grantId}"})
    public ResponseEntity<?> guestPreviewPdf(@PathVariable(required = false) Long grantId,
                                             @RequestParam(required = false) String accessToken,
                                             HttpServletRequest request) {
        CertificateAccessTokenService.AccessTicket ticket = resolveTicket(
                request, accessToken, CertificateAccessTokenService.AccessScope.GUEST, grantId);
        if (ticket == null) return forbiddenCertificate();
        try {
            byte[] pdfBytes = grantService.generatePdfForGuest(ticket.grantId(), ticket.school(),
                    ticket.numberId(), ticket.realName());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"certificate_preview.pdf\"")
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (CertificateGrantService.CertificateAccessDeniedException e) {
            return forbiddenCertificate();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CertificateGrantController.class).warn("操作失败，请检查请求参数和服务配置");
            return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN).body("证书预览生成失败");
        }
    }

    @GetMapping("/identity-review/list")
    public Result<List<CertificateGrantService.IdentityReviewEntry>> identityReviewList(@RequestParam(required = false) Long typeId) {
        return Result.success(grantService.identityReviewList(typeId));
    }

    public record IdentityConfirmation(String recordKey, Long userId, String school, String numberId, String realName) {}

    @PostMapping("/identity-review/confirm")
    public Result<String> confirmIdentity(@RequestBody IdentityConfirmation confirmation, HttpServletRequest request) {
        if (confirmation.recordKey() == null || confirmation.userId() == null) return Result.error("请选择证书和已审核通过的目标账号");
        String[] key = confirmation.recordKey().split(":", -1);
        if (key.length != 2 || !key[1].matches("[1-9][0-9]*")) return Result.error("记录编号不合法");
        long id;
        try { id = Long.parseLong(key[1]); } catch (NumberFormatException e) { return Result.error("记录编号不合法"); }
        if ("member".equals(key[0])) return grantService.confirmMemberIdentity(id, confirmation.userId(), confirmation.school(),
                confirmation.numberId(), confirmation.realName(), SecurityUtils.currentUserId(request));
        if (!"grant".equals(key[0])) return Result.error("记录类型不合法");
        return grantService.confirmIdentity(id, confirmation.userId(), confirmation.school(),
                confirmation.numberId(), confirmation.realName(), SecurityUtils.currentUserId(request));
    }

    @GetMapping("/record/list")
    public Result<List<CertificateGrantRecordVO>> getGrantRecordList(@RequestParam Long typeId) {
        return grantService.getGrantRecordList(typeId);
    }

    @PostMapping("/revoke/{grantId}")
    public Result<String> revokeGrant(@PathVariable Long grantId) {
        return grantService.revokeGrant(grantId);
    }

    @PostMapping("/revoke/batch")
    public Result<String> batchRevokeGrant(@RequestBody List<Long> grantIds) {
        return grantService.batchRevokeGrant(grantIds);
    }

    @GetMapping("/my")
    public Result<List<CertificateGrantVO>> getMyCertificates(@RequestAttribute("userId") Long userId,
                                                              HttpServletRequest request,
                                                              HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        Result<List<CertificateGrantVO>> result = grantService.getMyCertificates(userId);
        if (result.getCode() != null && result.getCode() == 200 && result.getData() != null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                for (CertificateGrantVO certificate : result.getData()) {
                    certificate.setAccessToken(accessTokenService.issueForUser(
                            session, certificate.getCertificateDownloadId(), userId));
                }
            }
        }
        return result;
    }

    @GetMapping({"/download", "/download/{grantId}"})
    public ResponseEntity<?> downloadPdf(@PathVariable(required = false) Long grantId,
                                         @RequestParam(required = false) String accessToken,
                                         @RequestAttribute("user") UserProfileVO user,
                                         HttpServletRequest request) {
        CertificateAccessTokenService.AccessTicket ticket = resolveTicket(
                request, accessToken, CertificateAccessTokenService.AccessScope.USER, grantId);
        if (ticket == null || !Objects.equals(ticket.userId(), user.getUserId())) return forbiddenCertificate();
        try {
            byte[] pdfBytes = grantService.generatePdf(ticket.grantId(), user);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificate.pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
        } catch (CertificateGrantService.CertificateAccessDeniedException e) {
            return forbiddenCertificate();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CertificateGrantController.class).warn("操作失败，请检查请求参数和服务配置");
            return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN).body("证书生成失败");
        }
    }

    @GetMapping({"/preview", "/preview/{grantId}"})
    public ResponseEntity<?> previewPdf(@PathVariable(required = false) Long grantId,
                                        @RequestParam(required = false) String accessToken,
                                        @RequestAttribute("user") UserProfileVO user,
                                        HttpServletRequest request) {
        CertificateAccessTokenService.AccessTicket ticket = resolveTicket(
                request, accessToken, CertificateAccessTokenService.AccessScope.USER, grantId);
        if (ticket == null || !Objects.equals(ticket.userId(), user.getUserId())) return forbiddenCertificate();
        try {
            byte[] pdfBytes = grantService.generatePdf(ticket.grantId(), user);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"certificate_preview.pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
        } catch (CertificateGrantService.CertificateAccessDeniedException e) {
            return forbiddenCertificate();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CertificateGrantController.class).warn("操作失败，请检查请求参数和服务配置");
            return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN).body("证书预览生成失败");
        }
    }

    private CertificateAccessTokenService.AccessTicket resolveTicket(HttpServletRequest request, String token,
                                                                      CertificateAccessTokenService.AccessScope scope,
                                                                      Long legacyGrantId) {
        CertificateAccessTokenService.AccessTicket ticket = accessTokenService.resolve(
                request.getSession(false), token, scope);
        if (ticket == null) return null;
        // 兼容旧路径，但路径中的 ID 只能与短期凭证绑定的证书一致。
        if (legacyGrantId != null && !legacyGrantId.equals(ticket.grantId())) return null;
        return ticket;
    }

    private ResponseEntity<String> forbiddenCertificate() {
        return ResponseEntity.status(403)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.TEXT_PLAIN)
                .body("无权访问此证书或访问凭证已失效");
    }
}
