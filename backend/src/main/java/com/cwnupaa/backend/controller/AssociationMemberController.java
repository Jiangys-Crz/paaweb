package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.AssociationMember;
import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.AssociationMemberMapper;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.cwnupaa.backend.mapper.UniversityMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.service.AuthStateChangeService;
import com.cwnupaa.backend.utils.StudentNumberValidator;
import com.cwnupaa.backend.vo.AssociationMemberVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.utils.SecurityUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/association-member")
public class AssociationMemberController {

    @Autowired
    private FileAccessPolicy fileAccessPolicy;

    @Autowired
    private AssociationMemberMapper associationMemberMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UniversityMapper universityMapper;

    @Autowired
    private FileSystemMapper fileSystemMapper;

    @Autowired
    private AuthStateChangeService authStateChangeService;

    @GetMapping("/list")
    public Result<List<AssociationMemberVO>> list(@RequestParam(required = false) String term) {
        QueryWrapper<AssociationMember> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasText(term)) {
            queryWrapper.eq("term", term);
        }
        queryWrapper.orderByAsc("sort_order").orderByAsc("member_id");
        List<AssociationMember> list = associationMemberMapper.selectList(queryWrapper);

        List<AssociationMemberVO> voList = new ArrayList<>();
        for (AssociationMember member : list) {
            AssociationMemberVO vo = new AssociationMemberVO();
            vo.setMemberId(member.getMemberId());
            vo.setTerm(member.getTerm());
            vo.setDepartment(member.getDepartment());
            vo.setUserId(member.getUserId());
            vo.setSchoolName(member.getSchoolName());
            vo.setNumberId(member.getNumberId());
            vo.setPosition(member.getPosition());
            vo.setSortOrder(member.getSortOrder());

            String realName = member.getRealName();
            Long avatarFileId = member.getAvatarFileId();
            String introduction = member.getIntroduction();

            // 如果关联了站内用户，且对应字段未覆盖（为空），则从站内用户表中获取默认值
            if (member.getUserId() != null) {
                Users user = userMapper.selectById(member.getUserId());
                if (user != null) {
                    if (!StringUtils.hasText(realName)) {
                        realName = user.getRealName();
                    }
                    if (avatarFileId == null) {
                        avatarFileId = user.getAvatarFileId();
                    }
                    if (!StringUtils.hasText(introduction)) {
                        introduction = user.getIntroduce();
                    }
                }
            }

            vo.setRealName(realName);
            vo.setAvatarFileId(avatarFileId);
            vo.setIntroduction(introduction);

            if (avatarFileId != null) {
                FileSystem fs = fileSystemMapper.selectById(avatarFileId);
                if (fs != null) {
                    vo.setAvatarUrl("/api" + fs.getUrl());
                }
            }
            voList.add(vo);
        }
        return Result.success(voList);
    }

    @GetMapping("/terms")
    public Result<List<String>> getTerms() {
        QueryWrapper<AssociationMember> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("DISTINCT term");
        List<AssociationMember> list = associationMemberMapper.selectList(queryWrapper);
        List<String> terms = list.stream()
                .map(AssociationMember::getTerm)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        return Result.success(terms);
    }

    @PostMapping("/add")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> add(@RequestBody AssociationMember member, HttpServletRequest request) {
        com.cwnupaa.backend.vo.UserProfileVO user = SecurityUtils.currentUser(request);
        if (!fileAccessPolicy.hasPermission(user, "manage_sys")) {
            return Result.error("无权限操作系统管理");
        }
        if (member == null) {
            return Result.error("请求参数不能为空");
        }
        if (!StringUtils.hasText(member.getTerm())) {
            return Result.error("学年不能为空");
        }
        if (!StringUtils.hasText(member.getDepartment())) {
            return Result.error("部门不能为空");
        }
        if (!StringUtils.hasText(member.getPosition())) {
            return Result.error("职位不能为空");
        }
        String fileError = fileAccessPolicy.validatePublicFile(member.getAvatarFileId(), user, true);
        if (fileError == null) fileError = fileAccessPolicy.validatePublicContent(member.getIntroduction(), user);
        if (fileError != null) return Result.error(fileError);
        if (member.getSortOrder() == null) {
            member.setSortOrder(0);
        }
        member.setCreateTime(LocalDateTime.now());
        associationMemberMapper.insert(member);
        return Result.success("添加成功");
    }

    @PostMapping("/batch-import")
    public Result<Map<String, Object>> batchImport(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        com.cwnupaa.backend.vo.UserProfileVO user = SecurityUtils.currentUser(request);
        if (!fileAccessPolicy.hasPermission(user, "manage_sys")) {
            return Result.error("无权限操作系统管理");
        }

        String term = payload == null || payload.get("term") == null ? null : payload.get("term").toString();
        String content = payload == null || payload.get("content") == null ? null : payload.get("content").toString();
        boolean preview = payload != null && Boolean.parseBoolean(String.valueOf(payload.getOrDefault("preview", false)));
        if (!StringUtils.hasText(term)) return Result.error("请选择导入学年");
        if (!StringUtils.hasText(content)) return Result.error("导入内容不能为空");

        String[] lines = content.split("\\r?\\n");
        int imported = 0;
        int updated = 0;
        int noAccount = 0;
        List<String> errors = new ArrayList<>();
        List<Map<String, String>> failedList = new ArrayList<>();
        List<Map<String, String>> successList = new ArrayList<>();

        Map<String, Integer> nextSortByDept = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            String rawLine = lines[i] == null ? "" : lines[i].trim();
            if (!StringUtils.hasText(rawLine)) continue;

            String[] parts = rawLine.split("\\t+");
            if (parts.length < 5) {
                parts = rawLine.split("\\s+");
            }
            if (parts.length < 5) {
                String reason = "第 " + (i + 1) + " 行格式错误，应为：部门、职位、姓名、学校、学号";
                errors.add(reason);
                failedList.add(Map.of("content", rawLine, "reason", reason));
                continue;
            }

            String department = parts[0].trim();
            String position = parts[1].trim();
            String realName = parts[2].trim();
            String schoolName = parts[3].trim();
            String numberId = parts[4].trim();
            if (!StringUtils.hasText(department) || !StringUtils.hasText(position)
                    || !StringUtils.hasText(realName) || !StringUtils.hasText(schoolName)
                    || !StringUtils.hasText(numberId)) {
                String reason = "第 " + (i + 1) + " 行存在空字段";
                errors.add(reason);
                failedList.add(Map.of("content", rawLine, "reason", reason));
                continue;
            }

            Users matchedUser = StudentNumberValidator.findByUniversityNameAndNumber(
                    userMapper, universityMapper, schoolName, numberId);
            if (matchedUser != null && !realName.equals(matchedUser.getRealName())) {
                String reason = "第 " + (i + 1) + " 行姓名与学校、学号对应的站内用户不一致";
                errors.add(reason);
                failedList.add(Map.of("content", rawLine, "reason", reason));
                continue;
            }
            if (matchedUser == null) {
                noAccount++;
            } else if (!preview && !"supper_admin".equals(matchedUser.getRole()) && !"super_admin".equals(matchedUser.getRole())) {
                matchedUser.setRole("stu_admin");
                userMapper.updateById(matchedUser);
                // 协会干部名单导入会授予 stu_admin，必须立刻同步该用户的在线会话，
                // 否则当事人要重新登录才会拿到管理后台入口。
                authStateChangeService.notifyChanged(matchedUser.getUserId());
            }

            QueryWrapper<AssociationMember> existingQuery = new QueryWrapper<>();
            existingQuery.eq("term", term);
            if (matchedUser != null) {
                existingQuery.eq("user_id", matchedUser.getUserId());
            } else {
                existingQuery.eq("school_name", schoolName)
                        .eq("number_id", numberId);
            }
            AssociationMember member = associationMemberMapper.selectOne(existingQuery.last("limit 1"));

            boolean isNew = member == null;
            if (isNew) {
                member = new AssociationMember();
                member.setCreateTime(LocalDateTime.now());
                member.setSortOrder(nextSortByDept.computeIfAbsent(department, dept -> {
                    QueryWrapper<AssociationMember> maxQuery = new QueryWrapper<>();
                    maxQuery.eq("term", term).eq("department", dept).orderByDesc("sort_order").last("limit 1");
                    AssociationMember maxMember = associationMemberMapper.selectOne(maxQuery);
                    return maxMember == null || maxMember.getSortOrder() == null ? 0 : maxMember.getSortOrder() + 1;
                }));
                nextSortByDept.put(department, member.getSortOrder() + 1);
            }

            member.setTerm(term);
            member.setDepartment(department);
            member.setPosition(position);
            member.setUserId(matchedUser == null ? null : matchedUser.getUserId());
            member.setRealName(realName);
            member.setSchoolName(schoolName);
            member.setNumberId(numberId);

            if (isNew) {
                if (!preview) associationMemberMapper.insert(member);
                imported++;
            } else {
                if (!preview) associationMemberMapper.updateById(member);
                updated++;
            }
            successList.add(Map.of(
                    "content", department + " / " + position + " / " + realName + " / " + schoolName + " / " + numberId,
                    "reason", isNew ? "将新增干部" : "将更新干部"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("imported", imported);
        result.put("updated", updated);
        result.put("noAccount", noAccount);
        result.put("errors", errors);
        result.put("success", imported + updated);
        result.put("failure", failedList.size());
        result.put("successList", successList);
        result.put("failedList", failedList);
        return Result.success(result);
    }

    @PostMapping("/update")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> update(@RequestBody AssociationMember member, HttpServletRequest request) {
        com.cwnupaa.backend.vo.UserProfileVO user = SecurityUtils.currentUser(request);
        if (!fileAccessPolicy.hasPermission(user, "manage_sys")) {
            return Result.error("无权限操作系统管理");
        }
        if (member == null || member.getMemberId() == null) {
            return Result.error("成员ID不能为空");
        }
        if (!StringUtils.hasText(member.getTerm())) {
            return Result.error("学年不能为空");
        }
        if (!StringUtils.hasText(member.getDepartment())) {
            return Result.error("部门不能为空");
        }
        if (!StringUtils.hasText(member.getPosition())) {
            return Result.error("职位不能为空");
        }
        String fileError = fileAccessPolicy.validatePublicFile(member.getAvatarFileId(), user, true);
        if (fileError == null) fileError = fileAccessPolicy.validatePublicContent(member.getIntroduction(), user);
        if (fileError != null) return Result.error(fileError);
        if (member.getSortOrder() == null) {
            member.setSortOrder(0);
        }
        associationMemberMapper.updateById(member);
        return Result.success("修改成功");
    }

    @PostMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id, HttpServletRequest request) {
        com.cwnupaa.backend.vo.UserProfileVO user = SecurityUtils.currentUser(request);
        if (!fileAccessPolicy.hasPermission(user, "manage_sys")) {
            return Result.error("无权限操作系统管理");
        }
        associationMemberMapper.deleteById(id);
        return Result.success("删除成功");
    }

    @PostMapping("/swap/{id1}/{id2}")
    public Result<String> swap(@PathVariable Long id1, @PathVariable Long id2, HttpServletRequest request) {
        com.cwnupaa.backend.vo.UserProfileVO user = SecurityUtils.currentUser(request);
        if (!fileAccessPolicy.hasPermission(user, "manage_sys")) {
            return Result.error("无权限操作系统管理");
        }
        AssociationMember m1 = associationMemberMapper.selectById(id1);
        AssociationMember m2 = associationMemberMapper.selectById(id2);
        if (m1 == null || m2 == null) {
            return Result.error("成员不存在");
        }
        if (!m1.getTerm().equals(m2.getTerm()) || !m1.getDepartment().equals(m2.getDepartment())) {
            return Result.error("不能跨学年或跨部门进行排序交换");
        }

        // 查询该学年该部门的所有成员，并按当前物理顺序排列
        QueryWrapper<AssociationMember> qw = new QueryWrapper<>();
        qw.eq("term", m1.getTerm())
          .eq("department", m1.getDepartment())
          .orderByAsc("sort_order")
          .orderByAsc("member_id");
        List<AssociationMember> allMembers = associationMemberMapper.selectList(qw);

        int index1 = -1;
        int index2 = -1;
        for (int i = 0; i < allMembers.size(); i++) {
            if (allMembers.get(i).getMemberId().equals(id1)) {
                index1 = i;
            }
            if (allMembers.get(i).getMemberId().equals(id2)) {
                index2 = i;
            }
        }

        if (index1 == -1 || index2 == -1) {
            return Result.error("成员在当前部门下未找到");
        }

        // 交换位置
        AssociationMember temp = allMembers.get(index1);
        allMembers.set(index1, allMembers.get(index2));
        allMembers.set(index2, temp);

        // 重新赋予递增的 sort_order 并更新数据库
        for (int i = 0; i < allMembers.size(); i++) {
            AssociationMember m = allMembers.get(i);
            m.setSortOrder(i);
            associationMemberMapper.updateById(m);
        }

        return Result.success("排序更新成功");
    }
}
