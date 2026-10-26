package com.cwnupaa.backend.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.Major;
import com.cwnupaa.backend.entity.School;
import com.cwnupaa.backend.entity.University;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.MajorMapper;
import com.cwnupaa.backend.mapper.SchoolMapper;
import com.cwnupaa.backend.mapper.UniversityMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import org.springframework.util.StringUtils;

/**
 * 学号（工号）唯一性校验工具。
 *
 * 规则：同一个学校（大学，university）内学号不能重复，不同学校（大学）之间的学号可以重复。
 * 用户所属学校（大学）通过 major -> school -> university 推导。
 */
public final class StudentNumberValidator {

    private StudentNumberValidator() {
    }

    /**
     * 通过专业ID解析用户所属的学校（大学）ID。
     */
    public static Long resolveUniversityId(MajorMapper majorMapper, SchoolMapper schoolMapper, Long majorId) {
        if (majorId == null || majorId == 0) return null;
        Major major = majorMapper.selectById(majorId);
        if (major == null) return null;
        School school = schoolMapper.selectById(major.getSchoolId());
        return school == null ? null : school.getUniversityId();
    }

    /**
     * 通过专业ID解析用户所属的学校（大学）名称。
     */
    public static String resolveUniversityName(MajorMapper majorMapper, SchoolMapper schoolMapper,
                                               UniversityMapper universityMapper, Long majorId) {
        Long universityId = resolveUniversityId(majorMapper, schoolMapper, majorId);
        if (universityId == null) return null;
        University university = universityMapper.selectById(universityId);
        return university == null ? null : university.getUniversityName();
    }

    /**
     * 判断学号是否已在同一学校（大学）内被其他用户占用。
     *
     * @param numberId      待校验的学号/工号
     * @param majorId       用户（或即将生效）的专业ID，用于推导所属学校（大学）
     * @param excludeUserId 排除的用户ID（更新场景传当前用户ID，新增场景传 null）
     */
    public static boolean existsInSameUniversity(UserMapper userMapper, MajorMapper majorMapper, SchoolMapper schoolMapper,
                                                 String numberId, Long majorId, Long excludeUserId) {
        return findSameUniversityUser(userMapper, majorMapper, schoolMapper, numberId, majorId, excludeUserId) != null;
    }

    /**
     * 查找同一学校（大学）内使用相同学号/工号的已有用户。
     *
     * @param numberId      待查找的学号/工号
     * @param majorId       用户（或即将生效）的专业ID，用于推导所属学校（大学）
     * @param excludeUserId 排除的用户ID（更新场景传当前用户ID，新增场景传 null）
     * @return 已存在的用户，未找到返回 null
     */
    public static Users findSameUniversityUser(UserMapper userMapper, MajorMapper majorMapper, SchoolMapper schoolMapper,
                                               String numberId, Long majorId, Long excludeUserId) {
        if (!StringUtils.hasText(numberId)) return null;
        Long universityId = resolveUniversityId(majorMapper, schoolMapper, majorId);
        if (universityId == null) return null;
        QueryWrapper<Users> qw = new QueryWrapper<Users>()
                .eq("number_id", numberId)
                .inSql("major_id",
                        "SELECT m.major_id FROM major m JOIN school s ON m.school_id = s.school_id WHERE s.university_id = " + universityId)
                .last("LIMIT 1");
        if (excludeUserId != null) qw.ne("user_id", excludeUserId);
        return userMapper.selectOne(qw);
    }

    /**
     * 按学校（大学）名称 + 学号精确查找用户。
     *
     * 导入场景必须提供两个字段，避免不同学校使用相同学号时误关联。
     */
    public static Users findByUniversityNameAndNumber(UserMapper userMapper, UniversityMapper universityMapper,
                                                       String universityName, String numberId) {
        if (!StringUtils.hasText(universityName) || !StringUtils.hasText(numberId)) return null;
        University university = universityMapper.selectOne(new QueryWrapper<University>()
                .eq("university_name", universityName.trim())
                .last("LIMIT 1"));
        if (university == null) return null;
        return findByUniversityIdAndNumber(userMapper, university.getUniversityId(), numberId);
    }

    /** 按学校（大学）ID + 学号精确查找用户。 */
    public static Users findByUniversityIdAndNumber(UserMapper userMapper, Long universityId, String numberId) {
        if (universityId == null || !StringUtils.hasText(numberId)) return null;
        return userMapper.selectOne(new QueryWrapper<Users>()
                .eq("number_id", numberId.trim())
                .inSql("major_id",
                        "SELECT m.major_id FROM major m JOIN school s ON m.school_id = s.school_id WHERE s.university_id = " + universityId)
                .last("LIMIT 1"));
    }

    /**
     * 判断输入是否是一个已存在的学号（用于登录时提示学号登录已禁用）。
     */
    public static boolean isStudentNumber(UserMapper userMapper, String input) {
        if (!StringUtils.hasText(input)) return false;
        return userMapper.selectCount(new QueryWrapper<Users>().eq("number_id", input)) > 0;
    }
}
