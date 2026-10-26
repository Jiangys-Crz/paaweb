package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.ContestCategoryDTO;
import com.cwnupaa.backend.entity.ContestCategory;
import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.mapper.AdminPermissionsMapper;
import com.cwnupaa.backend.mapper.ContestCategoryMapper;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.ContestCategoryVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.cwnupaa.backend.service.FileAccessPolicy;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/contest/category")
public class ContestCategoryController {

    @Autowired
    private FileAccessPolicy fileAccessPolicy;

    @Autowired
    private ContestCategoryMapper categoryMapper;

    @Autowired
    private FileSystemMapper fileSystemMapper;

    @Autowired
    private AdminPermissionsMapper adminPermissionsMapper;

    @GetMapping("/list")
    public Result<Page<ContestCategoryVO>> list(@RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size, @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "false") Boolean admin) {
        Page<ContestCategory> page = new Page<>(SecurityUtils.normalizePage(current), SecurityUtils.clampPageSize(size));
        QueryWrapper<ContestCategory> wrapper = new QueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.like("name", keyword);
        }

        if (!admin) {
            wrapper.eq("status", 1);
        }

        String sortSql = "ORDER BY CASE level " +
                "WHEN 'national' THEN 1 " +
                "WHEN 'provincial' THEN 2 " +
                "WHEN 'school' THEN 3 " +
                "WHEN 'city' THEN 3 " +
                "WHEN 'department' THEN 4 " +
                "ELSE 5 END ASC, category_id DESC";

        wrapper.last(sortSql);

        categoryMapper.selectPage(page, wrapper);

        List<ContestCategoryVO> voList = page.getRecords().stream().map(this::convertToVO).collect(Collectors.toList());
        Page<ContestCategoryVO> resultPage = new Page<>();
        BeanUtils.copyProperties(page, resultPage);
        resultPage.setRecords(voList);

        return Result.success(resultPage);
    }

    @GetMapping("/detail/{id}")
    public Result<ContestCategoryVO> detail(@PathVariable Long id) {
        ContestCategory category = categoryMapper.selectById(id);
        if (category == null) return Result.error("赛事不存在");
        return Result.success(convertToVO(category));
    }

    @PostMapping("/save")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> save(@RequestBody ContestCategoryDTO dto, HttpServletRequest request) {
        if (!requireManageContest(request)) return Result.error("权限不足，无法管理赛事分类");
        if (dto == null) return Result.error("参数不能为空");
        if (!StringUtils.hasText(dto.getName()) || dto.getName().trim().length() > 100) return Result.error("分类名称不能为空且不能超过100字");
        if (!StringUtils.hasText(dto.getLevel())) return Result.error("赛事级别不能为空");
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) return Result.error("状态不合法");
        String fileError = fileAccessPolicy.validatePublicFile(dto.getLogoFileId(), SecurityUtils.currentUser(request), true);
        if (fileError == null) fileError = fileAccessPolicy.validatePublicContent(dto.getIntro(), SecurityUtils.currentUser(request));
        if (fileError != null) return Result.error(fileError);
        ContestCategory category = new ContestCategory();
        BeanUtils.copyProperties(dto, category);
        category.setName(dto.getName().trim());

        if (category.getCategoryId() != null) {
            categoryMapper.updateById(category);
        } else {
            categoryMapper.insert(category);
        }
        return Result.success("保存成功");
    }

    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id, HttpServletRequest request) {
        if (!requireManageContest(request)) return Result.error("权限不足，无法管理赛事分类");
        categoryMapper.deleteById(id);
        return Result.success("删除成功");
    }

    /**
     * 赛事分类写操作权限：实时校验 admin_permissions 中 manage_contest 权限（根管理员直接放行）。
     */
    private boolean requireManageContest(HttpServletRequest request) {
        return fileAccessPolicy.hasPermission(SecurityUtils.currentUser(request), "manage_contest");
    }

    private ContestCategoryVO convertToVO(ContestCategory entity) {
        ContestCategoryVO vo = new ContestCategoryVO();
        BeanUtils.copyProperties(entity, vo);
        if (entity.getLogoFileId() != null) {
            FileSystem file = fileSystemMapper.selectById(entity.getLogoFileId());
            if (file != null) {
                vo.setLogoUrl(file.getUrl());
            }
        }
        return vo;
    }
}
