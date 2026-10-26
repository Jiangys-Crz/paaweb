package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.GlobalSearchVO;
import com.cwnupaa.backend.vo.UserSearchVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/common")
public class CommonController {

    @Autowired
    private UniversityMapper universityMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private TermMapper termMapper;
    @Autowired
    private NoticeMapper noticeMapper;
    @Autowired
    private EventMapper eventMapper;
    @Autowired
    private ContestCategoryMapper contestCategoryMapper;
    @Autowired
    private TrainingMapper trainingMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private com.cwnupaa.backend.service.ExternalBoardService externalBoardService;

    @GetMapping("/universities")
    public Result<List<University>> getUniversities() {
        return Result.success(universityMapper.selectList(null));
    }

    @GetMapping("/schools/{uniId}")
    public Result<List<School>> getSchools(@PathVariable Long uniId) {
        return Result.success(schoolMapper.selectList(new QueryWrapper<School>().eq("university_id", uniId)));
    }

    @GetMapping("/majors/{schoolId}")
    public Result<List<Major>> getMajors(@PathVariable Long schoolId) {
        return Result.success(majorMapper.selectList(new QueryWrapper<Major>().eq("school_id", schoolId)));
    }

    @GetMapping("/terms")
    public Result<List<Term>> getTerms() {
        return Result.success(termMapper.selectList(new QueryWrapper<Term>().orderByDesc("term_id")));
    }

    @GetMapping("/search")
    public Result<GlobalSearchVO> searchGlobal(@RequestParam(required = false) String keyword) {
        GlobalSearchVO resultVO = new GlobalSearchVO();

        if (!StringUtils.hasText(keyword)) {
            resultVO.setNotices(new ArrayList<>());
            resultVO.setContestIntros(new ArrayList<>());
            resultVO.setContestSignups(new ArrayList<>());
            resultVO.setContestBoards(new ArrayList<>());
            resultVO.setTrainings(new ArrayList<>());
            resultVO.setUsers(new ArrayList<>());
            return Result.success(resultVO);
        }

        String key = keyword.trim();

        QueryWrapper<Notice> noticeQw = new QueryWrapper<>();
        noticeQw.like("title", key).orderByDesc("create_time").last("LIMIT 10");
        resultVO.setNotices(noticeMapper.selectList(noticeQw));

        QueryWrapper<ContestCategory> categoryQw = new QueryWrapper<>();
        categoryQw.like("name", key).last("LIMIT 10");
        resultVO.setContestIntros(contestCategoryMapper.selectList(categoryQw));

        QueryWrapper<Event> eventQw = new QueryWrapper<>();
        eventQw.like("name", key).eq("status", 1).orderByDesc("event_start").last("LIMIT 10");
        List<Event> events = eventMapper.selectList(eventQw);

        resultVO.setContestSignups(events);
        resultVO.setContestBoards(externalBoardService.listBoards(null, true, key).stream().limit(10).toList());

        QueryWrapper<Training> trainingQw = new QueryWrapper<>();
        trainingQw.like("name", key).orderByDesc("train_start").last("LIMIT 10");
        resultVO.setTrainings(trainingMapper.selectList(trainingQw));

        QueryWrapper<Users> userQw = new QueryWrapper<>();
        userQw.like("user_name", key).eq("account_status", "normal").last("LIMIT 10");
        List<UserSearchVO> userVOList = userMapper.selectList(userQw).stream()
                .map(SecurityUtils::publicSearchUser).toList();

        resultVO.setUsers(userVOList);

        return Result.success(resultVO);
    }
}