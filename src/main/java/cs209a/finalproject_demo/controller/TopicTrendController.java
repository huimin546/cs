package cs209a.finalproject_demo.controller;

import cs209a.finalproject_demo.service.TopicTrendService;
import cs209a.finalproject_demo.service.dto.TopicTrendMetric;
import cs209a.finalproject_demo.service.dto.TopicTrendResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/topics")
public class TopicTrendController {

    private final TopicTrendService topicTrendService;

    public TopicTrendController(TopicTrendService topicTrendService) {
        this.topicTrendService = topicTrendService;
    }

    @GetMapping("/trends")
    public TopicTrendResponse getTopicTrends(
            @RequestParam(required = false) List<String> tags,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "bucket", defaultValue = "month") String bucket,
            @RequestParam(name = "metric", defaultValue = "questions") String metric) {
        TopicTrendMetric resolvedMetric = TopicTrendMetric.from(metric);
        return topicTrendService.getTrends(tags, from, to, bucket, resolvedMetric);
    }
}
