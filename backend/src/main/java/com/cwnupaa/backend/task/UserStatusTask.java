package com.cwnupaa.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.Major;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.MajorMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UserStatusTask {

    private static final Logger log = LoggerFactory.getLogger(UserStatusTask.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MajorMapper majorMapper;

    @Scheduled(cron = "0 0 4 15 7 ?")
    public void updateUserEnrollmentStatus() {
        log.info("========== 开始执行年度在校生状态检查 (每年7月15日) ==========");
        long start = System.currentTimeMillis();

        try {
            List<Users> enrolledUsers = userMapper.selectList(new QueryWrapper<Users>().eq("is_enrolled_stu", 1));
            if (enrolledUsers.isEmpty()) {
                log.info("当前无在校生用户，任务结束");
                return;
            }

            List<Major> majors = majorMapper.selectList(null);
            Map<Long, Integer> majorDurationMap = majors.stream().collect(Collectors.toMap(Major::getMajorId, Major::getSchoolingYears));

            int currentYear = LocalDate.now().getYear();
            List<Users> usersToUpdate = new ArrayList<>();

            for (Users user : enrolledUsers) {
                try {
                    String gradeYearStr = user.getGradeYear();
                    if (gradeYearStr == null || gradeYearStr.trim().isEmpty()) {
                        continue;
                    }
                    String yearNumStr = gradeYearStr.replaceAll("[^0-9]", "");
                    if (yearNumStr.isEmpty()) continue;

                    int startYear = Integer.parseInt(yearNumStr);
                    Integer duration = 4;
                    if (user.getMajorId() != null && majorDurationMap.containsKey(user.getMajorId())) {
                        Integer majorDuration = majorDurationMap.get(user.getMajorId());
                        if (majorDuration != null && majorDuration > 0) {
                            duration = majorDuration;
                        }
                    }

                    int graduationYear = startYear + duration;

                    if (currentYear >= graduationYear) {
                        user.setIsEnrolledStu(0);
                        usersToUpdate.add(user);
                        log.debug("用户记录 {} 已达到毕业年限，更新状态", user.getUserId());
                    }
                } catch (NumberFormatException e) {
                    log.warn("用户 {} 的入学年份格式异常，跳过处理: {}", user.getUserId(), user.getGradeYear());
                } catch (Exception e) {
                    log.error("处理用户 {} 时发生异常 ({})", user.getUserId(), e.getClass().getSimpleName());
                }
            }

            if (!usersToUpdate.isEmpty()) {
                for (Users u : usersToUpdate) {
                    Users updateEntity = new Users();
                    updateEntity.setUserId(u.getUserId());
                    updateEntity.setIsEnrolledStu(0);
                    userMapper.updateById(updateEntity);
                }
            }

            log.info("========== 在校生状态检查完成，耗时 {} ms，共扫描 {} 人，更新 {} 人为已毕业 ==========", System.currentTimeMillis() - start, enrolledUsers.size(), usersToUpdate.size());

        } catch (Exception e) {
            log.error("在校生状态检查任务执行失败 ({})", e.getClass().getSimpleName());
        }
    }
}