package cs209a.finalproject_demo.service;

import cs209a.finalproject_demo.repository.QuestionRepository;
import cs209a.finalproject_demo.repository.TagRepository;
import cs209a.finalproject_demo.repository.projection.TopicTrendRow;
import cs209a.finalproject_demo.service.dto.TopicTrendMetric;
import cs209a.finalproject_demo.service.dto.TopicTrendPoint;
import cs209a.finalproject_demo.service.dto.TopicTrendResponse;
import cs209a.finalproject_demo.service.dto.TopicTrendSeries;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TopicTrendService {

    private static final List<String> DEFAULT_TAGS = List.of(
            "java",
            "spring-boot",
            "hibernate",
            "multithreading",
            "lambda",
            "collections");

    private final QuestionRepository questionRepository;
    private final TagRepository tagRepository;

    public TopicTrendService(QuestionRepository questionRepository, TagRepository tagRepository) {
        this.questionRepository = questionRepository;
        this.tagRepository = tagRepository;
    }

    public TopicTrendResponse getTrends(
            List<String> tags,
            LocalDate fromDate,
            LocalDate toDate,
            String bucket,
            TopicTrendMetric metric) {
        List<String> effectiveTags = normalizeTags(tags);
        String bucketSize = normalizeBucket(bucket);
        Instant fromInstant = resolveFromInstant(fromDate);
        Instant toInstant = resolveToInstant(toDate);
        boolean isYearBucket = "year".equals(bucketSize);

        List<TopicTrendRow> rows = questionRepository.findTopicTrends(
                effectiveTags.toArray(String[]::new),
                fromInstant,
                toInstant,
                isYearBucket);

        Map<String, List<TopicTrendPoint>> seriesMap = initSeriesMap(effectiveTags);
        for (TopicTrendRow row : rows) {
            long value = metric == TopicTrendMetric.SCORE
                    ? safeLong(row.getScoreSum())
                    : safeLong(row.getQuestionCount());
            List<TopicTrendPoint> points = seriesMap.computeIfAbsent(row.getTag(), key -> new ArrayList<>());
            points.add(new TopicTrendPoint(
                    row.getBucket(),
                    safeLong(row.getQuestionCount()),
                    safeLong(row.getScoreSum()),
                    value));
        }

        List<TopicTrendSeries> series = seriesMap.entrySet().stream()
                .map(entry -> new TopicTrendSeries(entry.getKey(), metric, entry.getValue()))
                .toList();

        return new TopicTrendResponse(
                effectiveTags,
                fromInstant,
                toInstant,
                bucketSize,
                metric,
                series);
    }

    private Map<String, List<TopicTrendPoint>> initSeriesMap(List<String> tags) {
        Map<String, List<TopicTrendPoint>> map = new LinkedHashMap<>();
        for (String tag : tags) {
            map.put(tag, new ArrayList<>());
        }
        return map;
    }

    private List<String> normalizeTags(List<String> tags) {
        List<String> source = CollectionUtils.isEmpty(tags) ? DEFAULT_TAGS : tags;
        List<String> invalidTags = new ArrayList<>();
        List<String> normalizedTags = source.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.toLowerCase(Locale.ENGLISH))
                .filter(tag -> {
                    boolean isValid = isValidTag(tag);
                    if (!isValid) {
                        invalidTags.add(tag); // 记录无效标签
                    }
                    return isValid;
                })
                .distinct()
                .toList();

        if (!invalidTags.isEmpty()) {
            throw new IllegalArgumentException("Invalid tags: " + String.join(", ", invalidTags));
        }

        return normalizedTags;
    }

    private boolean isValidTag(String tag) {
        return tagRepository.findByName(tag).isPresent();
    }

    private String normalizeBucket(String bucket) {
        if ("year".equalsIgnoreCase(bucket)) {
            return "year";
        }
        return "month";
    }

    private Instant resolveFromInstant(LocalDate fromDate) {
        LocalDate date = fromDate != null
                ? fromDate
                : LocalDate.now(ZoneOffset.UTC).minusYears(3).withDayOfMonth(1);
        return atStartOfDayUtc(date);
    }

    private Instant resolveToInstant(LocalDate toDate) {
        LocalDate date = toDate != null
                ? toDate.plusDays(1)
                : LocalDate.now(ZoneOffset.UTC).plusDays(1);
        return atStartOfDayUtc(date);
    }

    private Instant atStartOfDayUtc(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
