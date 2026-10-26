package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.NoticeDTO;
import com.cwnupaa.backend.entity.FileRelation;
import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.entity.Notice;
import com.cwnupaa.backend.mapper.FileRelationMapper;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.cwnupaa.backend.mapper.NoticeMapper;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.NoticeVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notice")
public class NoticeController {

    @Autowired
    private NoticeMapper noticeMapper;
    @Autowired
    private FileRelationMapper fileRelationMapper;
    @Autowired
    private FileSystemMapper fileMapper;
    @Autowired
    private FileAccessPolicy fileAccessPolicy;

    @GetMapping("/list")
    public Result<Page<NoticeVO>> list(@RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size, @RequestParam(required = false) String type, @RequestParam(required = false) String keyword) {
        Page<Notice> page = new Page<>(SecurityUtils.normalizePage(current), SecurityUtils.clampPageSize(size));
        QueryWrapper<Notice> wrapper = new QueryWrapper<>();

        if (StringUtils.hasText(type) && !"all".equals(type)) {
            wrapper.eq("type", type);
        }

        if (StringUtils.hasText(keyword)) {
            wrapper.like("title", keyword);
        }

        wrapper.orderByDesc("create_time");

        noticeMapper.selectPage(page, wrapper);

        List<NoticeVO> voList = page.getRecords().stream().map(this::convertToVO).collect(Collectors.toList());
        Page<NoticeVO> resultPage = new Page<>();
        BeanUtils.copyProperties(page, resultPage);
        resultPage.setRecords(voList);

        return Result.success(resultPage);
    }

    @GetMapping("/detail/{id}")
    public Result<NoticeVO> detail(@PathVariable Long id, @RequestParam(defaultValue = "true") Boolean incrementView) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) return Result.error("公告不存在");

        if (incrementView) {
            notice.setViewCount(notice.getViewCount() + 1);
            noticeMapper.updateById(notice);
        }

        return Result.success(convertToVO(notice));
    }

    @PostMapping("/publish")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> publish(@RequestBody NoticeDTO dto, HttpServletRequest request) {
        if (SecurityUtils.currentUserId(request) == null) return Result.error("未登录或会话已失效");
        if (!hasManageNoticePermission(request)) return Result.error("权限不足，无法操作公告管理");
        if (dto == null) return Result.error("参数不能为空");
        if (!StringUtils.hasText(dto.getTitle()) || dto.getTitle().trim().length() > 200) return Result.error("标题不能为空且不能超过200字");
        if (!StringUtils.hasText(dto.getType())) return Result.error("公告类型不能为空");
        if (!StringUtils.hasText(dto.getPublisherName()) || dto.getPublisherName().trim().length() > 100) return Result.error("发布人不能为空且不能超过100字");
        String fileError = fileAccessPolicy.validatePublicFiles(dto.getAttachmentIds(), SecurityUtils.currentUser(request));
        if (fileError == null) fileError = fileAccessPolicy.validatePublicFile(dto.getCoverFileId(), SecurityUtils.currentUser(request), true);
        if (fileError == null) fileError = fileAccessPolicy.validatePublicContent(dto.getContent(), SecurityUtils.currentUser(request));
        if (fileError != null) return Result.error(fileError);
        Notice notice = new Notice();
        BeanUtils.copyProperties(dto, notice);
        notice.setTitle(dto.getTitle().trim());
        notice.setPublisherName(dto.getPublisherName().trim());
        notice.setCreateTime(dto.getCreateTime() == null ? LocalDateTime.now() : dto.getCreateTime());

        if (dto.getInfoId() != null) {
            noticeMapper.updateById(notice);
            fileRelationMapper.delete(new QueryWrapper<FileRelation>().eq("biz_type", "notice").eq("business_id", dto.getInfoId()));
        } else {
            noticeMapper.insert(notice);
        }

        if (dto.getAttachmentIds() != null && !dto.getAttachmentIds().isEmpty()) {
            for (Long fileId : dto.getAttachmentIds()) {
                if (fileId == null) continue;
                FileRelation fr = new FileRelation();
                fr.setFileId(fileId);
                fr.setBizType("notice");
                fr.setBusinessId(notice.getInfoId());
                fileRelationMapper.insert(fr);
            }
        }
        return Result.success("发布成功");
    }

    @DeleteMapping("/delete/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = SecurityUtils.currentUserId(request);
        if (userId == null) return Result.error("未登录或会话已失效");
        if (!hasManageNoticePermission(request)) return Result.error("权限不足，无法操作公告管理");
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) return Result.error("公告不存在");

        fileRelationMapper.delete(new QueryWrapper<FileRelation>().eq("biz_type", "notice").eq("business_id", id));
        noticeMapper.deleteById(id);

        return Result.success("删除成功");
    }

    private NoticeVO convertToVO(Notice notice) {
        NoticeVO vo = new NoticeVO();
        BeanUtils.copyProperties(notice, vo);
        List<FileRelation> relations = fileRelationMapper.selectList(new QueryWrapper<FileRelation>().eq("biz_type", "notice").eq("business_id", notice.getInfoId()));
        if (!relations.isEmpty()) {
            List<Long> fileIds = relations.stream().map(FileRelation::getFileId).collect(Collectors.toList());
            List<FileSystem> files = fileMapper.selectBatchIds(fileIds);
            vo.setAttachments(files.stream().filter(f -> fileAccessPolicy.canAccess(f, null)).toList());
        }
        return vo;
    }

    private boolean hasManageNoticePermission(HttpServletRequest request) {
        return fileAccessPolicy.hasPermission(SecurityUtils.currentUser(request), "manage_notice");
    }
}
