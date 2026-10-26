package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.RatingTermRecord;
import com.cwnupaa.backend.entity.Term;
import com.cwnupaa.backend.mapper.RatingTermRecordMapper;
import com.cwnupaa.backend.mapper.TermMapper;
import com.cwnupaa.backend.service.RatingService;
import com.cwnupaa.backend.vo.LeaderboardVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rating")
public class RatingController {

    @Autowired
    private RatingService ratingService;

    @Autowired
    private TermMapper termMapper;
    @Autowired
    private RatingTermRecordMapper ratingTermRecordMapper;

    @GetMapping("/leaderboard")
    public Result<List<LeaderboardVO>> getLeaderboard(@RequestParam(required = false) Long termId) {
        return Result.success(ratingService.getLeaderboard(termId));
    }

    @GetMapping("/terms")
    public Result<List<Term>> getTerms() {
        return Result.success(termMapper.selectList(new QueryWrapper<Term>().orderByDesc("start_time")));
    }

    @PostMapping("/admin/settle-contest/{eventId}")
    public Result<String> settleContest(@PathVariable Long eventId) {
        ratingService.settleContestRating(eventId);
        return Result.success("结算成功");
    }

    @PostMapping("/admin/settle-daily-practice")
    public Result<String> settlePractice() {
        ratingService.refreshAllRatings();
        return Result.success("全量刷新触发成功");
    }

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getUserRatingHistory(@RequestParam Long userId) {
        List<RatingTermRecord> records = ratingTermRecordMapper.selectList(new QueryWrapper<RatingTermRecord>().eq("user_id", userId));

        if (records.isEmpty()) return Result.success(new ArrayList<>());

        List<Long> termIds = records.stream().map(RatingTermRecord::getTermId).collect(Collectors.toList());
        List<Term> terms = termMapper.selectBatchIds(termIds);
        Map<Long, Term> termMap = terms.stream().collect(Collectors.toMap(Term::getTermId, t -> t));

        List<Map<String, Object>> result = records.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            Term t = termMap.get(r.getTermId());
            map.put("termName", t != null ? t.getTermName() : "未知学期");
            map.put("startTime", t != null ? t.getStartTime() : null);
            map.put("ratingAll", r.getRatingAll());
            map.put("ratingContest", r.getRatingContest());
            map.put("ratingCertificate", r.getRatingCertificate());
            map.put("ratingPractice", r.getRatingPractice());
            return map;
        }).sorted((a, b) -> {
            Object t1 = a.get("startTime");
            Object t2 = b.get("startTime");
            if (t1 == null) return -1;
            if (t2 == null) return 1;
            return ((Comparable) t1).compareTo(t2);
        }).collect(Collectors.toList());

        return Result.success(result);
    }
}