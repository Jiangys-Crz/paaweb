package com.cwnupaa.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CrawlerLocalRunner {

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private double clipDifficulty(double difficulty) {
        if (difficulty >= 400) {
            return Math.round(difficulty);
        }
        return Math.round(400.0 / Math.exp(1.0 - difficulty / 400.0));
    }

    private double normalizeDifficulty(String platform, Object raw) {
        if (raw == null) return 0.0;
        double val;
        try {
            val = Double.parseDouble(raw.toString());
        } catch (NumberFormatException e) {
            val = 0.0;
        }

        if ("CodeForces".equalsIgnoreCase(platform) || "AtCoder".equalsIgnoreCase(platform) || "NowCoder".equalsIgnoreCase(platform)) {
            return Double.isNaN(val) ? 0.0 : val;
        } else if ("Luogu".equalsIgnoreCase(platform)) {
            Map<Integer, Double> luoguMap = new HashMap<>();
            luoguMap.put(0, 0.0);
            luoguMap.put(1, 800.0);
            luoguMap.put(2, 1100.0);
            luoguMap.put(3, 1500.0);
            luoguMap.put(4, 1800.0);
            luoguMap.put(5, 2200.0);
            luoguMap.put(6, 2600.0);
            luoguMap.put(7, 3200.0);
            return luoguMap.getOrDefault((int) val, 0.0);
        }
        return 0.0;
    }

    // CodeForces Crawler
    public List<Map<String, Object>> crawlCodeforces(String handle) {
        List<Map<String, Object>> results = new ArrayList<>();
        String url = "https://codeforces.com/api/user.status?handle=" + handle;
        try {
            String resp = restTemplate.getForObject(url, String.class);
            Map<String, Object> data = objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
            if (data == null || !"OK".equals(data.get("status"))) {
                return results;
            }
            List<Map<String, Object>> resultList = (List<Map<String, Object>>) data.get("result");
            if (resultList == null) return results;

            List<Map<String, Object>> acSubs = new ArrayList<>();
            for (Map<String, Object> sub : resultList) {
                if ("OK".equals(sub.get("verdict"))) {
                    acSubs.add(sub);
                }
            }
            acSubs.sort(Comparator.comparingLong(s -> ((Number) s.get("creationTimeSeconds")).longValue()));

            Set<String> seenIds = new HashSet<>();
            for (Map<String, Object> sub : acSubs) {
                Map<String, Object> prob = (Map<String, Object>) sub.get("problem");
                if (prob == null) continue;
                Number contestIdObj = (Number) prob.get("contestId");
                int contestId = contestIdObj != null ? contestIdObj.intValue() : 0;
                String index = (String) prob.get("index");
                if (index == null) index = "";
                String pid = "CF" + contestId + index;

                if (seenIds.contains(pid)) continue;
                seenIds.add(pid);

                String contestType = contestId <= 10000 ? "contest" : "gym";
                String link = "https://codeforces.com/" + contestType + "/" + contestId + "/problem/" + index;
                Number ratingObj = (Number) prob.get("rating");
                int rating = ratingObj != null ? ratingObj.intValue() : 0;

                Map<String, Object> record = new HashMap<>();
                record.put("platform", "CodeForces");
                record.put("remote_id", String.valueOf(sub.get("id")));
                record.put("problem_id", pid);
                record.put("title", prob.getOrDefault("name", ""));
                record.put("link", link);
                record.put("solve_time", ((Number) sub.get("creationTimeSeconds")).longValue());
                record.put("difficulty", rating > 0 ? normalizeDifficulty("CodeForces", rating) : 0.0);
                record.put("raw_difficulty", rating > 0 ? String.valueOf(rating) : "N/A");
                record.put("tags", prob.getOrDefault("tags", new ArrayList<>()));
                results.add(record);
            }
        } catch (Exception e) {
            log.error("CodeForces crawl error for {}: {}", handle, e.getClass().getSimpleName());
        }
        return results;
    }

    // AtCoder Crawler
    private final Map<String, String> atcoderProblemsMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> atcoderModelsMap = new ConcurrentHashMap<>();
    private long atcoderLastUpdate = 0;
    private static final long CACHE_DURATION = 1000 * 3600 * 6; // 6 hours

    private synchronized void updateAtcoderMetadata() {
        if (System.currentTimeMillis() - atcoderLastUpdate < CACHE_DURATION && !atcoderProblemsMap.isEmpty()) {
            return;
        }
        try {
            log.info("Updating AtCoder metadata cache...");
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> pRes = restTemplate.exchange("https://kenkoooo.com/atcoder/resources/problems.json", HttpMethod.GET, entity, String.class);
            if (pRes.getStatusCode() == HttpStatus.OK && pRes.getBody() != null) {
                List<Map<String, Object>> problems = objectMapper.readValue(pRes.getBody(), new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> p : problems) {
                    String id = (String) p.get("id");
                    String name = (String) p.get("name");
                    if (id != null && name != null) {
                        atcoderProblemsMap.put(id, name);
                    }
                }
            }

            ResponseEntity<String> mRes = restTemplate.exchange("https://kenkoooo.com/atcoder/resources/problem-models.json", HttpMethod.GET, entity, String.class);
            if (mRes.getStatusCode() == HttpStatus.OK && mRes.getBody() != null) {
                Map<String, Map<String, Object>> models = objectMapper.readValue(mRes.getBody(), new TypeReference<Map<String, Map<String, Object>>>() {});
                atcoderModelsMap.clear();
                atcoderModelsMap.putAll(models);
            }

            atcoderLastUpdate = System.currentTimeMillis();
        } catch (Exception e) {
            log.error("Failed to update AtCoder metadata: {}", e.getClass().getSimpleName());
        }
    }

    public List<Map<String, Object>> crawlAtcoder(String handle) {
        updateAtcoderMetadata();
        List<Map<String, Object>> results = new ArrayList<>();
        String url = "https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=" + handle + "&from_second=0";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return results;
            }

            List<Map<String, Object>> rawList = objectMapper.readValue(response.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> acSubs = new ArrayList<>();
            for (Map<String, Object> sub : rawList) {
                if ("AC".equals(sub.get("result"))) {
                    acSubs.add(sub);
                }
            }
            acSubs.sort(Comparator.comparingLong(s -> ((Number) s.get("epoch_second")).longValue()));

            Set<String> seenPids = new HashSet<>();
            for (Map<String, Object> sub : acSubs) {
                String pid = (String) sub.get("problem_id");
                if (pid == null) continue;
                if (seenPids.contains(pid)) continue;
                seenPids.add(pid);

                String title = atcoderProblemsMap.getOrDefault(pid, pid);
                String contestId = (String) sub.get("contest_id");

                Map<String, Object> modelInfo = atcoderModelsMap.getOrDefault(pid, Collections.emptyMap());
                Number rawDiffObj = (Number) modelInfo.get("difficulty");
                double difficulty = 0.0;
                String rawDiffStr = "N/A";
                if (rawDiffObj != null) {
                    double rawDiff = rawDiffObj.doubleValue();
                    double clipped = clipDifficulty(rawDiff);
                    difficulty = normalizeDifficulty("AtCoder", clipped);
                    rawDiffStr = String.valueOf(rawDiffObj);
                }

                Map<String, Object> record = new HashMap<>();
                record.put("platform", "AtCoder");
                record.put("remote_id", String.valueOf(sub.get("id")));
                record.put("problem_id", pid);
                record.put("title", title);
                record.put("link", "https://atcoder.jp/contests/" + contestId + "/tasks/" + pid);
                record.put("solve_time", ((Number) sub.get("epoch_second")).longValue());
                record.put("difficulty", difficulty);
                record.put("raw_difficulty", rawDiffStr);
                record.put("tags", new ArrayList<>());
                results.add(record);
            }
        } catch (Exception e) {
            log.error("[AtCoder] Error crawling {}: {}", handle, e.getClass().getSimpleName());
        }
        return results;
    }

    // CWNU OJ Crawler
    private final Map<String, Double> cwnuDifficultyCache = new ConcurrentHashMap<>();

    private double getCwnuProblemDifficulty(String pid) {
        if (cwnuDifficultyCache.containsKey(pid)) {
            return cwnuDifficultyCache.get(pid);
        }
        try {
            Thread.sleep(100);
            String url = com.cwnupaa.backend.utils.DeploymentUrls.hydro() + "/p/" + pid;
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(10000)
                    .get();

            org.jsoup.select.Elements tags = doc.getElementsByClass("problem__tag-item");
            Map<Integer, Double> hydroMap = new HashMap<>();
            hydroMap.put(0, 0.0);
            hydroMap.put(1, 300.0);
            hydroMap.put(2, 400.0);
            hydroMap.put(3, 500.0);
            hydroMap.put(4, 700.0);
            hydroMap.put(5, 800.0);
            hydroMap.put(6, 1000.0);
            hydroMap.put(7, 1200.0);
            hydroMap.put(8, 1400.0);
            hydroMap.put(9, 1600.0);
            hydroMap.put(10, 1800.0);

            for (org.jsoup.nodes.Element tag : tags) {
                String text = tag.text().trim();
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?:难度|Difficulty)\\s*[:：]\\s*(\\d+)");
                java.util.regex.Matcher m = p.matcher(text);
                if (m.find()) {
                    int rawDiff = Integer.parseInt(m.group(1));
                    double score = hydroMap.getOrDefault(rawDiff, 0.0);
                    cwnuDifficultyCache.put(pid, score);
                    return score;
                }
            }
            cwnuDifficultyCache.put(pid, 0.0);
            return 0.0;
        } catch (Exception e) {
            log.error("[CWNU] Failed to get difficulty for {}: {}", pid, e.getClass().getSimpleName());
            return 0.0;
        }
    }

    public List<Map<String, Object>> crawlCwnu(String userId) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (com.cwnupaa.backend.utils.DeploymentUrls.hydro().isEmpty()) return results;
        String url = com.cwnupaa.backend.utils.DeploymentUrls.hydro() + "/api/submissions/" + userId;
        try {
            String resp = restTemplate.getForObject(url, String.class);
            Map<String, Object> data = objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
            if (data == null || !data.containsKey("data") || !(data.get("data") instanceof List)) {
                return results;
            }

            List<Map<String, Object>> dataList = (List<Map<String, Object>>) data.get("data");
            List<Map<String, Object>> acSubs = new ArrayList<>();
            for (Map<String, Object> sub : dataList) {
                Number statusObj = (Number) sub.get("status");
                if (statusObj != null && statusObj.intValue() == 1) {
                    acSubs.add(sub);
                }
            }

            List<Object[]> parsedSubs = new ArrayList<>();
            for (Map<String, Object> sub : acSubs) {
                String judgeAtStr = (String) sub.get("judgeAt");
                if (judgeAtStr == null) continue;
                try {
                    java.time.Instant instant = java.time.Instant.parse(judgeAtStr);
                    long ts = instant.getEpochSecond();
                    parsedSubs.add(new Object[]{sub, ts});
                } catch (Exception e) {
                    // ignore
                }
            }
            parsedSubs.sort(Comparator.comparingLong(x -> (long) x[1]));

            Set<String> seenPids = new HashSet<>();
            for (Object[] pair : parsedSubs) {
                Map<String, Object> sub = (Map<String, Object>) pair[0];
                long ts = (long) pair[1];
                String displayPid = (String) sub.get("displayPid");
                if (displayPid == null) continue;
                if (seenPids.contains(displayPid)) continue;
                seenPids.add(displayPid);

                double difficulty = getCwnuProblemDifficulty(displayPid);
                String rawDiffStr = difficulty > 0 ? String.valueOf((int) difficulty) : "N/A";

                Map<String, Object> record = new HashMap<>();
                record.put("platform", "CWNU");
                record.put("remote_id", String.valueOf(sub.get("rid")));
                record.put("problem_id", displayPid);
                record.put("title", sub.getOrDefault("title", ""));
                record.put("link", com.cwnupaa.backend.utils.DeploymentUrls.hydro() + "/p/" + displayPid);
                record.put("solve_time", ts);
                record.put("difficulty", difficulty);
                record.put("raw_difficulty", rawDiffStr);
                record.put("tags", new ArrayList<>());
                results.add(record);
            }
        } catch (Exception e) {
            log.error("[CWNU] Error crawling {}: {}", userId, e.getClass().getSimpleName());
        }
        return results;
    }

    // Luogu OJ Crawler
    private String getLuoguUid(String keyword, Map<String, String> headers) {
        try {
            String url = "https://www.luogu.com.cn/api/user/search?keyword=" + keyword;
            HttpHeaders httpHeaders = new HttpHeaders();
            headers.forEach(httpHeaders::set);
            HttpEntity<Void> entity = new HttpEntity<>(httpHeaders);
            ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            Map<String, Object> data = objectMapper.readValue(res.getBody(), new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> users = (List<Map<String, Object>>) data.get("users");
            if (users != null && !users.isEmpty()) {
                return String.valueOf(users.get(0).get("uid"));
            }
        } catch (Exception e) {
            log.error("[Luogu] Failed to get UID for {}: {}", keyword, e.getClass().getSimpleName());
        }
        return null;
    }

    public List<Map<String, Object>> crawlLuogu(String handle, long lastTime, String clientId) {
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        headers.put("Referer", "https://www.luogu.com.cn/");
        headers.put("x-requested-with", "XMLHttpRequest");
        headers.put("Connection", "keep-alive");

        if (clientId != null && !clientId.isEmpty()) {
            headers.put("Cookie", buildLuoguCookie(clientId, null));
        }

        String uid = getLuoguUid(handle, headers);
        if (uid == null) {
            log.warn("[Luogu] User {} not found", handle);
            return results;
        }

        if (clientId != null && !clientId.isEmpty()) {
            headers.put("Cookie", buildLuoguCookie(clientId, uid));
        }

        List<Map<String, Object>> rawResults = new ArrayList<>();
        int page = 1;
        boolean shouldStop = false;

        try {
            while (!shouldStop) {
                String url = "https://www.luogu.com.cn/record/list?user=" + uid + "&status=12&page=" + page;
                HttpHeaders httpHeaders = new HttpHeaders();
                headers.forEach(httpHeaders::set);
                HttpEntity<Void> entity = new HttpEntity<>(httpHeaders);
                ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                String html = res.getBody();
                if (html == null) break;

                java.util.regex.Pattern p = java.util.regex.Pattern.compile("decodeURIComponent\\(\"([^\"]+)\"\\)");
                java.util.regex.Matcher m = p.matcher(html);
                if (!m.find()) break;

                String jsonStr = java.net.URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
                Map<String, Object> data = objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
                Map<String, Object> currentData = (Map<String, Object>) data.get("currentData");
                if (currentData == null) break;
                Map<String, Object> recordsMap = (Map<String, Object>) currentData.get("records");
                if (recordsMap == null) break;
                List<Map<String, Object>> records = (List<Map<String, Object>>) recordsMap.get("result");
                if (records == null || records.isEmpty()) break;

                for (Map<String, Object> rec : records) {
                    long submitTime = ((Number) rec.get("submitTime")).longValue();
                    if (submitTime <= lastTime) {
                        shouldStop = true;
                        break;
                    }

                    Map<String, Object> problem = (Map<String, Object>) rec.get("problem");
                    if (problem == null) continue;
                    String pid = (String) problem.get("pid");
                    Number rawDiff = (Number) problem.getOrDefault("difficulty", 0);

                    Map<String, Object> subMap = new HashMap<>();
                    subMap.put("platform", "Luogu");
                    subMap.put("remote_id", String.valueOf(rec.get("id")));
                    subMap.put("problem_id", pid);
                    subMap.put("title", problem.get("title"));
                    subMap.put("link", "https://www.luogu.com.cn/problem/" + pid);
                    subMap.put("solve_time", submitTime);
                    subMap.put("diff_val", rawDiff.intValue());
                    rawResults.add(subMap);
                }

                page++;
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            log.error("[Luogu] Error: {}", e.getClass().getSimpleName());
        }

        rawResults.sort(Comparator.comparingLong(x -> (long) x.get("solve_time")));

        Set<String> seen = new HashSet<>();
        for (Map<String, Object> sub : rawResults) {
            String pid = (String) sub.get("problem_id");
            if (!seen.contains(pid)) {
                seen.add(pid);
                int diffVal = (int) sub.remove("diff_val");
                double difficulty = normalizeDifficulty("Luogu", diffVal);

                Map<String, Object> record = new HashMap<>(sub);
                record.put("difficulty", difficulty);
                record.put("raw_difficulty", String.valueOf(diffVal));
                record.put("tags", new ArrayList<>());
                results.add(record);
            }
        }
        return results;
    }

    private String buildLuoguCookie(String rawCookie, String uid) {
        String cookie = rawCookie == null ? "" : rawCookie.trim();
        if (cookie.isEmpty()) return "";

        if (!cookie.contains("=")) {
            cookie = "__client_id=" + cookie;
        }

        if (uid != null && !uid.isEmpty() && !cookie.contains("_uid=")) {
            if (!cookie.endsWith(";")) cookie += ";";
            cookie += "_uid=" + uid;
        }

        if (!cookie.endsWith(";")) cookie += ";";
        return cookie;
    }

    // NowCoder Crawler
    private final Map<String, Double> nowcoderDifficultyCache = new ConcurrentHashMap<>();

    private double getNowcoderProblemDifficulty(String numericId) {
        if (nowcoderDifficultyCache.containsKey(numericId)) {
            return nowcoderDifficultyCache.get(numericId);
        }
        try {
            Thread.sleep(500);
            String apiStr = "https://ac.nowcoder.com/acm/problem/list/json?keyword=" + numericId + "&asc=true";
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "application/json");
            headers.set("Host", "ac.nowcoder.com");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> res = restTemplate.exchange(apiStr, HttpMethod.GET, entity, String.class);
            Map<String, Object> data = objectMapper.readValue(res.getBody(), new TypeReference<Map<String, Object>>() {});
            if (data != null && data.get("code") != null && ((Number) data.get("code")).intValue() == 0) {
                Map<String, Object> innerData = (Map<String, Object>) data.get("data");
                if (innerData != null && innerData.containsKey("problemSets")) {
                    List<Map<String, Object>> problemSets = (List<Map<String, Object>>) innerData.get("problemSets");
                    for (Map<String, Object> p : problemSets) {
                        if (String.valueOf(p.get("problemId")).equals(numericId)) {
                            Number rawDiffObj = (Number) p.get("difficulty");
                            double rawDiff = rawDiffObj != null ? rawDiffObj.doubleValue() : 0.0;
                            double finalDiff = rawDiff;
                            if (rawDiff < 9.0) {
                                finalDiff = normalizeDifficulty("Luogu", rawDiff);
                            }
                            nowcoderDifficultyCache.put(numericId, finalDiff);
                            return finalDiff;
                        }
                    }
                }
            }
            nowcoderDifficultyCache.put(numericId, 0.0);
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public List<Map<String, Object>> crawlNowcoder(String userId, long lastTime) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (userId == null || !userId.matches("\\d+")) {
            log.warn("[NowCoder] Invalid user_id: {}", userId);
            return results;
        }

        int page = 1;
        List<Map<String, Object>> rawResults = new ArrayList<>();
        boolean shouldStop = false;
        String lastPageSignature = "";
        int maxPages = 50;

        log.info("[NowCoder] Starting crawl for user {}, last_time={}", userId, lastTime);

        try {
            while (!shouldStop && page <= maxPages) {
                String url = "https://ac.nowcoder.com/acm/contest/profile/" + userId + "/practice-coding?pageSize=200&statusTypeFilter=5&orderType=DESC&page=" + page;
                
                org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept", "text/html,application/xhtml+xml")
                        .header("Host", "ac.nowcoder.com")
                        .timeout(15000)
                        .get();

                org.jsoup.select.Elements rows = doc.select("table.table-hover tbody tr");
                if (rows.isEmpty()) {
                    break;
                }

                String currentSignature = rows.get(0).outerHtml();
                if (currentSignature.equals(lastPageSignature)) {
                    break;
                }
                lastPageSignature = currentSignature;

                int processedInPage = 0;
                for (org.jsoup.nodes.Element row : rows) {
                    org.jsoup.select.Elements tds = row.select("td");
                    if (tds.size() < 9) continue;

                    org.jsoup.nodes.Element linkTag = tds.get(1).selectFirst("a");
                    if (linkTag == null) continue;

                    String title = linkTag.text().trim();
                    String href = linkTag.attr("href");

                    String numericId = null;
                    String pid;
                    if (href.contains("/problem/")) {
                        String[] parts = href.split("/problem/");
                        if (parts.length > 1) {
                            numericId = parts[1];
                            pid = "NC" + numericId;
                        } else {
                            pid = "NC_" + title;
                        }
                    } else {
                        pid = "NC_" + title;
                    }

                    long ts;
                    try {
                        String timeStr = tds.get(8).text().trim();
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(timeStr, formatter);
                        java.time.ZonedDateTime zdt = ldt.atZone(java.time.ZoneId.of("Asia/Shanghai"));
                        ts = zdt.toEpochSecond();
                    } catch (Exception e) {
                        continue;
                    }

                    if (ts <= lastTime) {
                        shouldStop = true;
                        break;
                    }

                    Map<String, Object> subMap = new HashMap<>();
                    subMap.put("platform", "NowCoder");
                    subMap.put("remote_id", tds.get(0).text().trim());
                    subMap.put("problem_id", pid);
                    subMap.put("numeric_id", numericId);
                    subMap.put("title", title);
                    subMap.put("link", "https://ac.nowcoder.com" + href);
                    subMap.put("solve_time", ts);
                    rawResults.add(subMap);
                    processedInPage++;
                }

                if (processedInPage == 0) {
                    break;
                }
                page++;
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            log.error("[NowCoder] Error on page {}: {}", page, e.getClass().getSimpleName());
        }

        rawResults.sort(Comparator.comparingLong(x -> (long) x.get("solve_time")));

        List<Map<String, Object>> uniqueSubs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> sub : rawResults) {
            String pid = (String) sub.get("problem_id");
            if (!seen.contains(pid)) {
                seen.add(pid);
                uniqueSubs.add(sub);
            }
        }

        List<Map<String, Object>> finalResults = new ArrayList<>();
        for (Map<String, Object> sub : uniqueSubs) {
            double diff = 0.0;
            String rawDiffStr = "N/A";
            String numericId = (String) sub.get("numeric_id");
            if (numericId != null) {
                double d = getNowcoderProblemDifficulty(numericId);
                if (d > 0.0) {
                    diff = normalizeDifficulty("NowCoder", d);
                    rawDiffStr = String.valueOf(d);
                }
            }

            Map<String, Object> record = new HashMap<>(sub);
            record.remove("numeric_id");
            record.put("difficulty", diff);
            record.put("raw_difficulty", rawDiffStr);
            record.put("tags", new ArrayList<>());
            finalResults.add(record);
        }
        return finalResults;
    }

    // Upcoming Contests Crawlers
    private List<Map<String, Object>> fetchCodeforcesUpcoming() {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String url = "https://codeforces.com/api/contest.list?gym=false";
            String resp = restTemplate.getForObject(url, String.class);
            Map<String, Object> data = objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
            if (data != null && "OK".equals(data.get("status"))) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("result");
                for (Map<String, Object> c : list) {
                    if (!(c.get("startTimeSeconds") instanceof Number start) || !(c.get("durationSeconds") instanceof Number duration)) continue;
                    long endTime = start.longValue() + duration.longValue();
                    if ("BEFORE".equals(c.get("phase")) || endTime > System.currentTimeMillis() / 1000) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", c.get("name"));
                        map.put("platform", "CodeForces");
                        map.put("link", "https://codeforces.com/contest/" + c.get("id"));
                        map.put("start_time", ((Number) c.get("startTimeSeconds")).longValue());
                        map.put("end_time", endTime);
                        map.put("unique_id", "CF_" + c.get("id"));
                        results.add(map);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Upcoming] CF error: {}", e.getClass().getSimpleName());
        }
        return results;
    }

    private List<Map<String, Object>> fetchAtcoderUpcoming() {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String url = "https://atcoder.jp/contests";
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(10000)
                    .get();

            for (String tableId : List.of("contest-table-active", "contest-table-upcoming")) {
            org.jsoup.nodes.Element table = doc.getElementById(tableId);
            if (table != null) {
                org.jsoup.select.Elements rows = table.select("tbody tr");
                for (org.jsoup.nodes.Element row : rows) {
                    org.jsoup.select.Elements tds = row.select("td");
                    if (tds.size() < 2) continue;

                    String timeStr = tds.get(0).text().trim();
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*(\\d{2}:\\d{2})");
                    java.util.regex.Matcher m = p.matcher(timeStr);
                    if (!m.find()) continue;

                    String dtStr = m.group(1) + " " + m.group(2);
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dtStr, formatter);
                    java.time.ZonedDateTime zdt = ldt.atZone(java.time.ZoneId.of("Asia/Tokyo"));
                    long ts = zdt.toEpochSecond();

                    org.jsoup.nodes.Element linkTag = tds.get(1).selectFirst("a");
                    if (linkTag == null) continue;

                    String name = linkTag.text().trim();
                    String path = linkTag.attr("href");
                    String[] parts = path.split("/");
                    String contestId = parts[parts.length - 1];

                    Map<String, Object> map = new HashMap<>();
                    map.put("name", name);
                    map.put("platform", "AtCoder");
                    map.put("link", "https://atcoder.jp" + path);
                    map.put("start_time", ts);
                    if (tds.size() >= 3) {
                        Long seconds = parseContestDurationSeconds(tds.get(2).text().trim());
                        if (seconds != null) map.put("end_time", ts + seconds);
                    }
                    map.put("unique_id", "AT_" + contestId);
                    results.add(map);
                }
            }
            }
        } catch (Exception e) {
            log.error("[Upcoming] AtCoder error: {}", e.getClass().getSimpleName());
        }
        return results;
    }

    private List<Map<String, Object>> fetchNowcoderUpcoming() {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String url = "https://ac.nowcoder.com/acm/contest/vip-index";
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://ac.nowcoder.com/")
                    .header("Host", "ac.nowcoder.com")
                    .timeout(30000)
                    .get();

            org.jsoup.select.Elements items = doc.select(".platform-item-cont");
            for (org.jsoup.nodes.Element item : items) {
                org.jsoup.nodes.Element h4_a = item.selectFirst("h4 a");
                if (h4_a == null) continue;

                String name = h4_a.text().trim();
                String link = h4_a.attr("href");

                org.jsoup.nodes.Element timeDiv = item.selectFirst(".match-time-icon");
                if (timeDiv == null) continue;
                String timeText = timeDiv.text();

                java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})");
                java.util.regex.Matcher m = p.matcher(timeText);
                if (!m.find()) continue;

                String dtStr = m.group(1) + " " + m.group(2);
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dtStr, formatter);
                java.time.ZonedDateTime zdt = ldt.atZone(java.time.ZoneId.of("Asia/Shanghai"));
                long ts = zdt.toEpochSecond();

                Long endTime = parseNowcoderContestEnd(timeText, ts);
                if (ts > System.currentTimeMillis() / 1000 || (endTime != null && endTime > System.currentTimeMillis() / 1000)) {
                    String contestId = link.substring(link.lastIndexOf("/") + 1);
                    java.util.regex.Pattern cp = java.util.regex.Pattern.compile("/contest/(\\d+)");
                    java.util.regex.Matcher cm = cp.matcher(link);
                    if (cm.find()) {
                        contestId = cm.group(1);
                    }

                    Map<String, Object> map = new HashMap<>();
                    map.put("name", name);
                    map.put("platform", "NowCoder");
                    map.put("link", link.startsWith("/") ? "https://ac.nowcoder.com" + link : link);
                    map.put("start_time", ts);
                    if (endTime != null) map.put("end_time", endTime);
                    org.jsoup.nodes.Element registration = item.selectFirst(".time-icon");
                    if (registration != null) {
                        List<Long> dates = parseContestDates(registration.text());
                        if (!dates.isEmpty()) map.put("registration_start", dates.get(0));
                        if (dates.size() > 1) map.put("registration_end", dates.get(1));
                    }
                    map.put("unique_id", "NC_" + contestId);
                    results.add(map);
                }
            }
        } catch (Exception e) {
            log.error("[Upcoming] NowCoder error: {}", e.getClass().getSimpleName());
        }
        return results;
    }

    /** Parse only durations actually present in source HTML. Never guess a contest's duration. */
    static Long parseContestDurationSeconds(String value) {
        java.util.regex.Matcher clock = java.util.regex.Pattern.compile("^(\\d{1,4}):([0-5]\\d)$").matcher(value == null ? "" : value.trim());
        if (clock.matches()) return Long.parseLong(clock.group(1)) * 3600 + Long.parseLong(clock.group(2)) * 60;
        java.util.regex.Matcher units = java.util.regex.Pattern.compile("(?:(\\d+)\\s*(?:小时|h))?\\s*(?:(\\d+)\\s*(?:分钟|min))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value == null ? "" : value.trim());
        if (units.matches() && (units.group(1) != null || units.group(2) != null)) {
            return (units.group(1) == null ? 0L : Long.parseLong(units.group(1)) * 3600)
                    + (units.group(2) == null ? 0L : Long.parseLong(units.group(2)) * 60);
        }
        return null;
    }

    static Long parseNowcoderContestEnd(String value, long startEpoch) {
        List<Long> dates = parseContestDates(value);
        if (dates.size() > 1) return dates.get(1);
        java.util.regex.Matcher duration = java.util.regex.Pattern.compile("(?:时长|比赛时长)\\s*[:：]?\\s*([0-9]+\\s*(?:小时|h)(?:\\s*[0-9]+\\s*(?:分钟|min))?|[0-9]+\\s*(?:分钟|min)|[0-9]{1,4}:[0-5][0-9])", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
        if (duration.find()) {
            Long seconds = parseContestDurationSeconds(duration.group(1));
            if (seconds != null && seconds > 0) return startEpoch + seconds;
        }
        return null;
    }

    static List<Long> parseContestDates(String value) {
        List<Long> result = new ArrayList<>();
        java.util.regex.Matcher dates = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})").matcher(value == null ? "" : value);
        while (dates.find() && result.size() < 2) {
            try {
                result.add(java.time.LocalDateTime.parse(dates.group(1) + " " + dates.group(2), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        .atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond());
            } catch (RuntimeException ignored) { }
        }
        return result;
    }

    public List<Map<String, Object>> crawlUpcoming() {
        List<Map<String, Object>> allList = new ArrayList<>();
        allList.addAll(fetchCodeforcesUpcoming());
        allList.addAll(fetchAtcoderUpcoming());
        allList.addAll(fetchNowcoderUpcoming());
        allList.sort(Comparator.comparingLong(x -> ((Number) x.get("start_time")).longValue()));
        return allList;
    }

    // Unified call dispatcher
    public Map<String, Object> crawlSubmissions(String platform, String handle, long lastTime, String clientId) {
        List<Map<String, Object>> list;
        if ("codeforces".equalsIgnoreCase(platform)) {
            list = crawlCodeforces(handle);
        } else if ("atcoder".equalsIgnoreCase(platform)) {
            list = crawlAtcoder(handle);
        } else if ("cwnu".equalsIgnoreCase(platform)) {
            list = crawlCwnu(handle);
        } else if ("luogu".equalsIgnoreCase(platform)) {
            list = crawlLuogu(handle, lastTime, clientId);
        } else if ("nowcoder".equalsIgnoreCase(platform)) {
            list = crawlNowcoder(handle, lastTime);
        } else {
            log.error("Unknown crawler platform: {}", platform);
            list = Collections.emptyList();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("submissions", list);
        result.put("count", list.size());
        return result;
    }
}
