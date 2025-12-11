package cs209a.finalproject_demo.service.dto;

import java.time.Instant;
import java.util.List;

public record TopicTrendResponse(
        List<String> tags,
        Instant from,
        Instant to,
        String bucket,
        TopicTrendMetric metric,
        List<TopicTrendSeries> series) {
}
