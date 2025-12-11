package cs209a.finalproject_demo.service;

import cs209a.finalproject_demo.model.Answer;
import cs209a.finalproject_demo.model.Question;
import cs209a.finalproject_demo.model.Tag;
import cs209a.finalproject_demo.repository.QuestionRepository;
import cs209a.finalproject_demo.service.dto.HardQuestionCriteria;
import cs209a.finalproject_demo.service.dto.SolvabilityComparisonResponse;
import cs209a.finalproject_demo.service.dto.SolvabilityCriteria;
import cs209a.finalproject_demo.service.dto.SolvabilityFactor;
import cs209a.finalproject_demo.service.dto.SolvabilityTagStat;
import cs209a.finalproject_demo.service.dto.SolvabilityTotals;
import cs209a.finalproject_demo.service.dto.SolvableQuestionCriteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SolvabilityAnalysisService {

    private static final int DEFAULT_MIN_ACCEPTED_ANSWER_SCORE = 2;
    private static final int DEFAULT_MAX_FIRST_ANSWER_HOURS = 48;
    private static final int DEFAULT_HARD_MIN_ANSWER_LATENCY_HOURS = 72;
    private static final int MIN_ACCEPTED_SCORE = 0;
    private static final int MAX_ACCEPTED_SCORE = 100;
    private static final int MIN_RESPONSE_HOURS = 1;
    private static final int MAX_RESPONSE_HOURS = 720; // 30 days
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("<code\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> JAVA_TAG_FILTER = List.of("java");

    private final QuestionRepository questionRepository;

    public SolvabilityAnalysisService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public SolvabilityComparisonResponse compareSolvability(
            Integer minAcceptedAnswerScore,
            Integer maxFirstAnswerHours,
            Integer hardMinAnswerLatencyHours) {
        SolvabilityThresholds thresholds = resolveThresholds(
                minAcceptedAnswerScore,
                maxFirstAnswerHours,
                hardMinAnswerLatencyHours);

        List<Long> questionIds = questionRepository.findDistinctIdsByTagNames(JAVA_TAG_FILTER);
        if (questionIds.isEmpty()) {
            return emptyResponse(thresholds);
        }

        List<Question> questions = questionRepository.findAllById(questionIds);
        List<QuestionSnapshot> solvable = new ArrayList<>();
        List<QuestionSnapshot> hard = new ArrayList<>();

        for (Question question : questions) {
            QuestionSnapshot snapshot = buildSnapshot(question);
            if (snapshot == null) {
                continue;
            }
            if (isSolvable(snapshot, thresholds)) {
                solvable.add(snapshot);
            } else if (isHard(snapshot, thresholds)) {
                hard.add(snapshot);
            }
        }

        return new SolvabilityComparisonResponse(
                buildCriteria(thresholds),
                new SolvabilityTotals(solvable.size(), hard.size()),
                buildFactors(solvable, hard),
                computeTopTags(solvable),
                computeTopTags(hard));
    }

    private SolvabilityComparisonResponse emptyResponse(SolvabilityThresholds thresholds) {
        return new SolvabilityComparisonResponse(
                buildCriteria(thresholds),
                new SolvabilityTotals(0, 0),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    private SolvabilityCriteria buildCriteria(SolvabilityThresholds thresholds) {
        return new SolvabilityCriteria(
                new SolvableQuestionCriteria(true,
                        thresholds.minAcceptedAnswerScore(),
                        thresholds.maxFirstAnswerHours()),
                new HardQuestionCriteria(true, true, thresholds.hardMinAnswerLatencyHours()));
    }

    private QuestionSnapshot buildSnapshot(Question question) {
        Instant creationDate = question.getCreationDate();
        List<Answer> answers = question.getAnswers();
        if (answers == null) {
            answers = List.of();
        }

        Answer acceptedAnswer = findAcceptedAnswer(question, answers);
        Double hoursToFirstAnswer = computeHoursToFirstAnswer(creationDate, answers);

        int answerCount = question.getAnswerCount() != null ? question.getAnswerCount() : answers.size();
        List<String> tagNames = question.getTags() == null ? List.of()
                : question.getTags().stream()
                        .map(Tag::getName)
                        .filter(Objects::nonNull)
                        .map(name -> name.toLowerCase(Locale.ENGLISH))
                        .toList();

        return new QuestionSnapshot(
                safeLength(question.getTitle()),
                countCodeBlocks(question.getBody()),
                question.getOwnerReputation() == null ? 0 : question.getOwnerReputation(),
                question.getScore() == null ? 0 : question.getScore(),
                hoursToFirstAnswer,
                acceptedAnswer != null ? acceptedAnswer.getScore() : null,
                acceptedAnswer != null,
                answerCount,
                tagNames);
    }

    private Answer findAcceptedAnswer(Question question, List<Answer> answers) {
        if (answers.isEmpty()) {
            return null;
        }
        if (question.getAcceptedAnswerId() != null) {
            Long acceptedId = question.getAcceptedAnswerId();
            for (Answer answer : answers) {
                if (Objects.equals(answer.getId(), acceptedId)) {
                    return answer;
                }
            }
        }
        return answers.stream()
                .filter(answer -> Boolean.TRUE.equals(answer.getAccepted()))
                .findFirst()
                .orElse(null);
    }

    private Double computeHoursToFirstAnswer(Instant questionCreation, List<Answer> answers) {
        if (questionCreation == null || answers.isEmpty()) {
            return null;
        }
        Instant firstAnswer = answers.stream()
                .map(Answer::getCreationDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (firstAnswer == null) {
            return null;
        }
        return Duration.between(questionCreation, firstAnswer).toMinutes() / 60.0;
    }

    private boolean isSolvable(QuestionSnapshot snapshot, SolvabilityThresholds thresholds) {
        return snapshot.hasAcceptedAnswer()
                && snapshot.acceptedAnswerScore() != null
                && snapshot.acceptedAnswerScore() >= thresholds.minAcceptedAnswerScore()
                && snapshot.hoursToFirstAnswer() != null
                && snapshot.hoursToFirstAnswer() <= thresholds.maxFirstAnswerHours();
    }

    private boolean isHard(QuestionSnapshot snapshot, SolvabilityThresholds thresholds) {
        boolean noAnswers = snapshot.answerCount() == 0;
        boolean missingAccepted = !snapshot.hasAcceptedAnswer();
        boolean slowResponse = snapshot.hoursToFirstAnswer() == null
                || snapshot.hoursToFirstAnswer() > thresholds.hardMinAnswerLatencyHours();
        return noAnswers || missingAccepted || slowResponse;
    }

    private List<SolvabilityFactor> buildFactors(List<QuestionSnapshot> solvable, List<QuestionSnapshot> hard) {
        List<SolvabilityFactor> factors = new ArrayList<>();
        factors.add(new SolvabilityFactor(
                "平均代码块数量",
                averagePrimitive(solvable, QuestionSnapshot::codeBlockCount),
                averagePrimitive(hard, QuestionSnapshot::codeBlockCount),
                "blocks"));
        factors.add(new SolvabilityFactor(
                "含代码示例占比",
                percentageWithCode(solvable),
                percentageWithCode(hard),
                "percent"));
        factors.add(new SolvabilityFactor(
                "平均首答所需小时",
                averageNullable(solvable, QuestionSnapshot::hoursToFirstAnswer),
                averageNullable(hard, QuestionSnapshot::hoursToFirstAnswer),
                "hours"));
        factors.add(new SolvabilityFactor(
                "平均问题得分",
                averagePrimitive(solvable, QuestionSnapshot::questionScore),
                averagePrimitive(hard, QuestionSnapshot::questionScore),
                "score"));
        factors.add(new SolvabilityFactor(
                "平均回答数量",
                averagePrimitive(solvable, QuestionSnapshot::answerCount),
                averagePrimitive(hard, QuestionSnapshot::answerCount),
                "count"));
        return factors;
    }

    private List<SolvabilityTagStat> computeTopTags(List<QuestionSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Long> counts = snapshots.stream()
                .flatMap(snapshot -> snapshot.tags().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        int total = counts.values().stream().mapToInt(Long::intValue).sum();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(5)
                .map(entry -> new SolvabilityTagStat(entry.getKey(), safePercentage(entry.getValue(), total)))
                .toList();
    }

    private double percentageWithCode(List<QuestionSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return 0;
        }
        long withCode = snapshots.stream()
                .filter(snapshot -> snapshot.codeBlockCount() > 0)
                .count();
        return safePercentage(withCode, snapshots.size());
    }

    private double averagePrimitive(List<QuestionSnapshot> snapshots, ToDoubleFunction<QuestionSnapshot> extractor) {
        if (snapshots.isEmpty()) {
            return 0;
        }
        return snapshots.stream().mapToDouble(extractor).average().orElse(0);
    }

    private double averageNullable(List<QuestionSnapshot> snapshots, Function<QuestionSnapshot, Double> extractor) {
        double total = 0;
        int count = 0;
        for (QuestionSnapshot snapshot : snapshots) {
            Double value = extractor.apply(snapshot);
            if (value != null) {
                total += value;
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private int countCodeBlocks(String body) {
        if (body == null || body.isEmpty()) {
            return 0;
        }
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(body);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private double safePercentage(long part, long total) {
        if (total == 0) {
            return 0;
        }
        return (part * 100.0) / total;
    }

    private SolvabilityThresholds resolveThresholds(
            Integer minAcceptedAnswerScore,
            Integer maxFirstAnswerHours,
            Integer hardMinAnswerLatencyHours) {
        int resolvedScore = minAcceptedAnswerScore == null
                ? DEFAULT_MIN_ACCEPTED_ANSWER_SCORE
                : validateRange("minAcceptedAnswerScore", minAcceptedAnswerScore,
                        MIN_ACCEPTED_SCORE, MAX_ACCEPTED_SCORE);
        int resolvedMaxFirst = maxFirstAnswerHours == null
                ? DEFAULT_MAX_FIRST_ANSWER_HOURS
                : validateRange("maxFirstAnswerHours", maxFirstAnswerHours,
                        MIN_RESPONSE_HOURS, MAX_RESPONSE_HOURS);
        int resolvedHardLatency = hardMinAnswerLatencyHours == null
                ? DEFAULT_HARD_MIN_ANSWER_LATENCY_HOURS
                : validateRange("hardMinAnswerLatencyHours", hardMinAnswerLatencyHours,
                        MIN_RESPONSE_HOURS, MAX_RESPONSE_HOURS);
        return new SolvabilityThresholds(resolvedScore, resolvedMaxFirst, resolvedHardLatency);
    }

    private int validateRange(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    String.format("%s 必须在 %d 到 %d 之间", field, min, max));
        }
        return value;
    }

    private record SolvabilityThresholds(
            int minAcceptedAnswerScore,
            int maxFirstAnswerHours,
            int hardMinAnswerLatencyHours) {
    }

    private record QuestionSnapshot(
            int titleLength,
            int codeBlockCount,
            int ownerReputation,
            int questionScore,
            Double hoursToFirstAnswer,
            Integer acceptedAnswerScore,
            boolean hasAcceptedAnswer,
            int answerCount,
            List<String> tags) {
    }

}
