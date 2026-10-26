package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.OjPlatform;
import com.cwnupaa.backend.entity.OjRecentContest;
import com.cwnupaa.backend.mapper.OjPlatformMapper;
import com.cwnupaa.backend.mapper.OjRecentContestMapper;
import com.cwnupaa.backend.vo.OjRecentContestVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OjRecentContestService {

    private static final Logger log = LoggerFactory.getLogger(OjRecentContestService.class);

    private final OjRecentContestMapper ojRecentContestMapper;
    private final OjPlatformMapper ojPlatformMapper;
    private final RestTemplate restTemplate;

    @Value("${crawler.service.url}")
    private String crawlerServiceUrl;

    @Autowired
    private CrawlerLocalRunner crawlerLocalRunner;

    private Map<String, Long> platformIdMap;

    @Autowired
    public OjRecentContestService(OjRecentContestMapper ojRecentContestMapper, OjPlatformMapper ojPlatformMapper, RestTemplate restTemplate) {
        this.ojRecentContestMapper = ojRecentContestMapper;
        this.ojPlatformMapper = ojPlatformMapper;
        this.restTemplate = restTemplate;
    }

    private void initPlatformMap() {
        platformIdMap = new HashMap<>();
        List<OjPlatform> platforms = ojPlatformMapper.selectList(null);
        for (OjPlatform p : platforms) {
            platformIdMap.put(p.getOjName(), p.getOjId());
        }
    }

    public List<OjRecentContest> getUpcomingContests() {
        QueryWrapper<OjRecentContest> query = new QueryWrapper<>();
        // 只展示未开始的比赛，按时间排序
        query.gt("contests_starttime", LocalDateTime.now()).orderByAsc("contests_starttime");
        return ojRecentContestMapper.selectList(query);
    }

    public List<OjRecentContestVO> getUpcomingContestsVO() {
        List<OjRecentContest> list = getUpcomingContests();
        List<OjPlatform> platforms = ojPlatformMapper.selectList(null);
        Map<Long, String> idToNameMap = platforms.stream().collect(Collectors.toMap(OjPlatform::getOjId, OjPlatform::getOjName));

        return list.stream().map(e -> {
            OjRecentContestVO vo = new OjRecentContestVO();
            vo.setId(e.getUpcomingContestsId());
            vo.setContestName(e.getContestsName());
            vo.setStartTime(e.getContestsStarttime());
            vo.setLink(e.getUrl());
            vo.setOjName(idToNameMap.getOrDefault(e.getSourceId(), "Unknown"));
            return vo;
        }).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncContests() {
        initPlatformMap();

        try {
            log.info("Requesting upcoming contests locally from CrawlerLocalRunner");

            List<Map<String, Object>> rawList = crawlerLocalRunner.crawlUpcoming();

            if (rawList == null || rawList.isEmpty()) {
                log.warn("爬虫返回数据为空");
                return;
            }
            log.info("爬虫返回 {} 场近期比赛", rawList.size());

            int addCount = 0;
            int updateCount = 0;

            for (Map<String, Object> item : rawList) {
                String platformName = (String) item.get("platform");
                String uniqueId = (String) item.get("unique_id");
                String name = (String) item.get("name");
                String contestLink = (String) item.get("link");
                Number startTimeObj = (Number) item.get("start_time");

                Long sourceId = platformIdMap.get(platformName);
                if (sourceId == null) {
                    continue;
                }

                LocalDateTime startTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(startTimeObj.longValue()), ZoneId.of("Asia/Shanghai"));
                Number endTimeObj = (Number) item.get("end_time");
                LocalDateTime endTime = endTimeObj == null ? null : LocalDateTime.ofInstant(Instant.ofEpochSecond(endTimeObj.longValue()), ZoneId.of("Asia/Shanghai"));
                LocalDateTime registrationStart = contestDate(item.get("registration_start"));
                LocalDateTime registrationEnd = contestDate(item.get("registration_end"));

                QueryWrapper<OjRecentContest> check = new QueryWrapper<>();
                check.eq("contests_id", uniqueId);
                OjRecentContest existing = ojRecentContestMapper.selectOne(check);

                if (existing != null) {
                    existing.setContestsName(name);
                    existing.setContestsStarttime(startTime);
                    if (endTime != null) existing.setContestsEndtime(endTime);
                    if (registrationStart != null) existing.setRegistrationStart(registrationStart);
                    if (registrationEnd != null) existing.setRegistrationEnd(registrationEnd);
                    existing.setUrl(contestLink);
                    existing.setSourceId(sourceId);

                    ojRecentContestMapper.updateById(existing);
                    updateCount++;
                } else {
                    OjRecentContest contest = new OjRecentContest();
                    contest.setSourceId(sourceId);
                    contest.setContestsId(uniqueId);
                    contest.setContestsName(name);
                    contest.setUrl(contestLink);
                    contest.setContestsStarttime(startTime);
                    contest.setContestsEndtime(endTime);
                    contest.setRegistrationStart(registrationStart);
                    contest.setRegistrationEnd(registrationEnd);

                    ojRecentContestMapper.insert(contest);
                    addCount++;
                }
            }

            log.info("同步完成，新增 {} 场，更新 {} 场", addCount, updateCount);

        } catch (Exception e) {
            log.error("同步近期比赛失败 ({})", e.getClass().getSimpleName());
        }
    }

    private static LocalDateTime contestDate(Object value) {
        return value instanceof Number epoch ? LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch.longValue()), ZoneId.of("Asia/Shanghai")) : null;
    }
}
