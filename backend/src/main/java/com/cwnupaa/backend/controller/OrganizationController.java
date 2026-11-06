package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.Major;
import com.cwnupaa.backend.entity.School;
import com.cwnupaa.backend.entity.Term;
import com.cwnupaa.backend.entity.University;
import com.cwnupaa.backend.mapper.MajorMapper;
import com.cwnupaa.backend.mapper.SchoolMapper;
import com.cwnupaa.backend.mapper.TermMapper;
import com.cwnupaa.backend.mapper.UniversityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/admin/org")
public class OrganizationController {

    @Autowired
    private UniversityMapper universityMapper;
    @Autowired private FileAccessPolicy fileAccessPolicy;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private TermMapper termMapper;

    private boolean canManageLogos(HttpServletRequest request) {
        UserProfileVO user = SecurityUtils.currentUser(request);
        return user != null && "normal".equals(user.getAccountStatus())
                && ("super_admin".equals(user.getRole()) || "supper_admin".equals(user.getRole()));
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> organizationInfo(HttpServletRequest request) {
        if (!canManageLogos(request)) return Result.error(403, "仅超级管理员可管理组织标识");
        Map<String, Object> data = new HashMap<>();
        data.put("universities", universityMapper.selectList(new QueryWrapper<University>().orderByAsc("university_name")));
        data.put("schools", schoolMapper.selectList(new QueryWrapper<School>().orderByAsc("school_name")));
        return Result.success(data);
    }

    @PostMapping("/info/{kind}/{id}/logo")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateOrganizationLogo(@PathVariable String kind, @PathVariable Long id,
                                                 @RequestBody Map<String, Long> payload, HttpServletRequest request) {
        if (!canManageLogos(request)) return Result.error(403, "仅超级管理员可管理组织标识");
        if (!payload.containsKey("logoFileId")) return Result.error("缺少 LOGO 参数");
        Long logoId = payload.get("logoFileId");
        if (!"university".equals(kind) && !"school".equals(kind)) return Result.error("组织类型不正确");
        University university = "university".equals(kind) ? universityMapper.selectById(id) : null;
        School school = "school".equals(kind) ? schoolMapper.selectById(id) : null;
        if (university == null && school == null) return Result.error("组织不存在");
        String error = fileAccessPolicy.validatePublicFile(logoId, SecurityUtils.currentUser(request), true);
        if (error != null) return Result.error(error);
        Long previous = university != null ? university.getLogoFileId() : school.getLogoFileId();
        if (university != null) universityMapper.update(null, new UpdateWrapper<University>().eq("university_id", id).set("logo_file_id", logoId));
        else schoolMapper.update(null, new UpdateWrapper<School>().eq("school_id", id).set("logo_file_id", logoId));
        if (!Objects.equals(previous, logoId)) fileAccessPolicy.deleteIfUnreferenced(previous, SecurityUtils.currentUser(request));
        return Result.success("保存成功");
    }

    @PostMapping("/university/save")
    public Result<String> saveUniversity(@RequestBody University university) {
        if (university == null || !StringUtils.hasText(university.getUniversityName())) return Result.error("学校名称不能为空");
        university.setLogoFileId(null); // Logos are changed only through the validated logo endpoint.
        university.setUniversityName(university.getUniversityName().trim());
        if (university.getUniversityName().length() > 100) return Result.error("学校名称不能超过100字");
        if (university.getUniversityId() == null) {
            if (universityMapper.selectCount(new QueryWrapper<University>().eq("university_name", university.getUniversityName())) > 0) {
                return Result.error("学校名称已存在");
            }
            universityMapper.insert(university);
        } else {
            universityMapper.updateById(university);
        }
        return Result.success("操作成功");
    }

    @DeleteMapping("/university/delete/{id}")
    public Result<String> deleteUniversity(@PathVariable Long id) {
        Long count = schoolMapper.selectCount(new QueryWrapper<School>().eq("university_id", id));
        if (count > 0) return Result.error("该学校下存在学院，禁止删除");
        universityMapper.deleteById(id);
        return Result.success("删除成功");
    }

    @PostMapping("/school/save")
    public Result<String> saveSchool(@RequestBody School school) {
        if (school == null || school.getUniversityId() == null) return Result.error("所属学校不能为空");
        if (!StringUtils.hasText(school.getSchoolName())) return Result.error("学院名称不能为空");
        school.setLogoFileId(null); // Logos are changed only through the validated logo endpoint.
        school.setSchoolName(school.getSchoolName().trim());
        if (school.getSchoolName().length() > 100) return Result.error("学院名称不能超过100字");
        if (school.getSchoolId() == null) {
            if (schoolMapper.selectCount(new QueryWrapper<School>().eq("university_id", school.getUniversityId()).eq("school_name", school.getSchoolName())) > 0) {
                return Result.error("该学校下已存在同名学院");
            }
            schoolMapper.insert(school);
        } else {
            schoolMapper.updateById(school);
        }
        return Result.success("操作成功");
    }

    @DeleteMapping("/school/delete/{id}")
    public Result<String> deleteSchool(@PathVariable Long id) {
        Long count = majorMapper.selectCount(new QueryWrapper<Major>().eq("school_id", id));
        if (count > 0) return Result.error("该学院下存在专业，禁止删除");
        schoolMapper.deleteById(id);
        return Result.success("删除成功");
    }

    @PostMapping("/major/save")
    public Result<String> saveMajor(@RequestBody Major major) {
        if (major == null || major.getSchoolId() == null) return Result.error("所属学院不能为空");
        if (!StringUtils.hasText(major.getMajorName())) return Result.error("专业名称不能为空");
        major.setMajorName(major.getMajorName().trim());
        if (major.getMajorName().length() > 100) return Result.error("专业名称不能超过100字");
        if (major.getSchoolingYears() != null && (major.getSchoolingYears() < 0 || major.getSchoolingYears() > 10)) return Result.error("学制不合法");
        if (major.getMajorId() == null) {
            if (majorMapper.selectCount(new QueryWrapper<Major>().eq("school_id", major.getSchoolId()).eq("major_name", major.getMajorName())) > 0) {
                return Result.error("该学院下已存在同名专业");
            }
            if (major.getSchoolingYears() == null) major.setSchoolingYears(4);
            majorMapper.insert(major);
        } else {
            majorMapper.updateById(major);
        }
        return Result.success("操作成功");
    }

    @DeleteMapping("/major/delete/{id}")
    public Result<String> deleteMajor(@PathVariable Long id) {
        majorMapper.deleteById(id);
        return Result.success("删除成功");
    }

    @PostMapping("/import")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> batchImport(@RequestBody Map<String, Object> params) {
        String content = params.get("content") == null ? null : params.get("content").toString();
        boolean preview = Boolean.parseBoolean(String.valueOf(params.getOrDefault("preview", false)));
        if (!StringUtils.hasText(content)) return Result.error("内容为空");
        if (content.length() > 200000) return Result.error("导入内容过大");

        String[] lines = content.split("\n");
        int success = 0;
        int fail = 0;
        List<Map<String, String>> failedList = new ArrayList<>();
        List<String> successList = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\t");
            String uniName = parts.length > 0 ? parts[0].trim() : "";

            if (uniName.isEmpty()) {
                fail++;
                failedList.add(Map.of("content", line, "reason", "学校名称不能为空"));
                continue;
            }

            try {
                StringBuilder importLog = new StringBuilder(uniName);
                University uni = universityMapper.selectOne(new QueryWrapper<University>().eq("university_name", uniName));
                if (uni == null) {
                    if (!preview) {
                        uni = new University();
                        uni.setUniversityName(uniName);
                        universityMapper.insert(uni);
                    }
                }
                String schoolName = parts.length > 1 ? parts[1].trim() : "";
                if (StringUtils.hasText(schoolName)) {
                    importLog.append(" > ").append(schoolName);
                    School school = uni == null ? null : schoolMapper.selectOne(new QueryWrapper<School>().eq("university_id", uni.getUniversityId()).eq("school_name", schoolName));
                    if (school == null && !preview) {
                        school = new School();
                        school.setUniversityId(uni.getUniversityId());
                        school.setSchoolName(schoolName);
                        schoolMapper.insert(school);
                    }

                    String majorName = parts.length > 2 ? parts[2].trim() : "";
                    if (StringUtils.hasText(majorName)) {
                        importLog.append(" > ").append(majorName);

                        Major major = school == null ? null : majorMapper.selectOne(new QueryWrapper<Major>().eq("school_id", school.getSchoolId()).eq("major_name", majorName));
                        if (major == null && !preview) {
                            major = new Major();
                            major.setSchoolId(school.getSchoolId());
                            major.setMajorName(majorName);
                            major.setSchoolingYears(4);
                            majorMapper.insert(major);
                        }
                    }
                }

                success++;
                successList.add(importLog.toString());
            } catch (Exception e) {
                fail++;
                failedList.add(Map.of("content", line, "reason", "系统错误: " + e.getMessage()));
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", success);
        res.put("failure", fail);
        res.put("failedList", failedList);
        res.put("successList", successList);
        return Result.success(res);
    }

    @PostMapping("/term/save")
    public Result<String> saveTerm(@RequestBody Term term) {
        if (term == null || !StringUtils.hasText(term.getTermName())) return Result.error("学期名称不能为空");
        term.setTermName(term.getTermName().trim());
        if (term.getTermId() == null) {
            if (!isValidTermName(term.getTermName())) {
                return Result.error("学期名称格式错误，必须为 YYYY-YYYY-N (如 2025-2026-1)");
            }

            if (term.getStartTime() == null) {
                return Result.error("开始时间必填");
            }

            if (termMapper.selectCount(new QueryWrapper<Term>().eq("term_name", term.getTermName())) > 0) {
                return Result.error("学期已存在");
            }

            Term lastTerm = termMapper.selectOne(new QueryWrapper<Term>().orderByDesc("start_time").last("LIMIT 1"));
            if (lastTerm != null && lastTerm.getEndTime() == null) {
                return Result.error("上学期未设置结束时间，禁止添加学期");
            }

            termMapper.insert(term);
        } else {
            Term old = termMapper.selectById(term.getTermId());
            if (old == null) return Result.error("学期不存在");

            if (term.getStartTime() != null) old.setStartTime(term.getStartTime());
            if (term.getEndTime() != null) old.setEndTime(term.getEndTime());

            termMapper.updateById(old);
        }
        return Result.success("操作成功");
    }

    private boolean isValidTermName(String name) {
        if (!StringUtils.hasText(name)) return false;
        String regex = "^\\d{4}-\\d{4}-[12]$";
        return Pattern.matches(regex, name);
    }
}
