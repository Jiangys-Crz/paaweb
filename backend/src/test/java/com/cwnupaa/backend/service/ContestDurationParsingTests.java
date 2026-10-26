package com.cwnupaa.backend.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import static org.assertj.core.api.Assertions.*;

class ContestDurationParsingTests {
    @Test void durationsComeFromRealClockOrExplicitUnits() {
        assertThat(CrawlerLocalRunner.parseContestDurationSeconds("02:30")).isEqualTo(9000L);
        assertThat(CrawlerLocalRunner.parseContestDurationSeconds("2小时30分钟")).isEqualTo(9000L);
        assertThat(CrawlerLocalRunner.parseContestDurationSeconds("150分钟")).isEqualTo(9000L);
        assertThat(CrawlerLocalRunner.parseContestDurationSeconds("待定")).isNull();
        assertThat(CrawlerLocalRunner.parseContestDurationSeconds("02:90")).isNull();
    }
    @Test void nowcoderUsesExplicitEndOrDurationAndNeverInventsUnknownEnd() {
        long start = LocalDateTime.of(2026, 10, 26, 12, 0).atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond();
        assertThat(CrawlerLocalRunner.parseNowcoderContestEnd("2026-10-26 12:00 至 2026-10-26 15:00", start)).isEqualTo(start + 10800);
        assertThat(CrawlerLocalRunner.parseNowcoderContestEnd("2026-10-26 12:00 时长：2小时30分钟", start)).isEqualTo(start + 9000);
        assertThat(CrawlerLocalRunner.parseNowcoderContestEnd("2026-10-26 12:00", start)).isNull();
    }
    @Test void nowcoderRegistrationRangeIsReadFromActualDateTextIncludingLineBreaks() {
        long expected = LocalDateTime.of(2026, 10, 30, 21, 0).atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond();
        assertThat(CrawlerLocalRunner.parseContestDates("报名时间：2026-10-10 10:00\n 至 2026-10-30 21:00"))
                .hasSize(2).last().isEqualTo(expected);
        assertThat(CrawlerLocalRunner.parseContestDates("报名时间：待定")).isEmpty();
    }
}
