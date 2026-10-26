package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.SysIntro;
import com.cwnupaa.backend.mapper.SysIntroMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.utils.SecurityUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/intro")
public class SysIntroController {

    @Autowired
    private FileAccessPolicy fileAccessPolicy;

    @Autowired
    private SysIntroMapper sysIntroMapper;

    @GetMapping("/{category}")
    public Result getIntro(@PathVariable String category) {
        if ("smtp_config".equals(category) || "object_storage_config".equals(category)) {
            return Result.error("敏感配置不可通过公开接口读取");
        }
        QueryWrapper<SysIntro> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category", category);
        SysIntro intro = sysIntroMapper.selectOne(queryWrapper);
        return Result.success(intro);
    }

    @PostMapping("/update")
    @Transactional(rollbackFor = Exception.class)
    public Result updateIntro(@RequestBody SysIntro intro, jakarta.servlet.http.HttpServletRequest request) {
        com.cwnupaa.backend.vo.UserProfileVO user = SecurityUtils.currentUser(request);
        if (!fileAccessPolicy.hasPermission(user, "manage_sys")) {
            return Result.error("无权限操作系统管理");
        }
        
        if (intro == null || intro.getCategory() == null) {
            return Result.error("分类不能为空");
        }
        if ("smtp_config".equals(intro.getCategory()) || "object_storage_config".equals(intro.getCategory())) {
            return Result.error("请通过系统配置专用接口保存敏感配置");
        }

        String fileError = fileAccessPolicy.validatePublicContent(intro.getContent(), user);
        if (fileError != null) return Result.error(fileError);
        intro.setUpdateTime(LocalDateTime.now());

        QueryWrapper<SysIntro> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category", intro.getCategory());

        int count = sysIntroMapper.update(intro, queryWrapper);
        if (count == 0) {
            sysIntroMapper.insert(intro);
        }
        return Result.success("保存成功");
    }
}
