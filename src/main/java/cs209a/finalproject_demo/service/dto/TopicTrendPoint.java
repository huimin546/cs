package cs209a.finalproject_demo.service.dto;

import java.time.Instant;

public record TopicTrendPoint(
        Instant bucket,
        long questionCount,
        long scoreSum,
        long value) {
}
