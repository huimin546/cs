package cs209a.finalproject_demo.service.dto;

import java.util.List;

public record TopicTrendSeries(
        String tag,
        TopicTrendMetric metric,
        List<TopicTrendPoint> points) {
}
