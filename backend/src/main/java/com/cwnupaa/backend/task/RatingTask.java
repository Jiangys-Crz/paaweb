package com.cwnupaa.backend.task;

import com.cwnupaa.backend.service.RatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RatingTask {

    private static final Logger log = LoggerFactory.getLogger(RatingTask.class);

    @Autowired
    private RatingService ratingService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("========== 服务启动：检测到系统重启，开始执行 Rating 全量重算 ==========");
        long start = System.currentTimeMillis();
        try {
            ratingService.refreshAllRatings();
            log.info("========== 服务启动：Rating 全量重算完成，耗时 {} ms ==========", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("服务启动：Rating 全量重算失败 ({})", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void dailyRatingRecalculation() {
        log.info("========== 开始执行每日 Rating 全量重算 ==========");
        long start = System.currentTimeMillis();
        try {
            ratingService.refreshAllRatings();
            log.info("========== Rating 全量重算完成，耗时 {} ms ==========", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Rating 全量重算失败 ({})", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "0 30 0 * * ?")
    public void termTransitionCheck() {
        try {
            ratingService.handleTermTransition();
        } catch (Exception e) {
            log.error("学期切换检查失败 ({})", e.getClass().getSimpleName());
        }
    }
}