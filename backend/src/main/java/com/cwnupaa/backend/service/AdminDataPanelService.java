package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cwnupaa.backend.dto.UserPracticeStatsDTO;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

public interface AdminDataPanelService {
    Map<String, Object> getOverallStats(String keyword, Long majorId, Long schoolId, Long universityId, String className, String gradeYear, Integer isEnrolledStu, String eduBg);
    IPage<UserPracticeStatsDTO> getUserStats(Integer page, Integer size, String keyword, Long majorId, Long schoolId, Long universityId, String className, String gradeYear, Integer isEnrolledStu, String eduBg);
    void exportUserStats(HttpServletResponse response, String keyword, Long majorId, Long schoolId, Long universityId, String className, String gradeYear, Integer isEnrolledStu, String eduBg);
}
