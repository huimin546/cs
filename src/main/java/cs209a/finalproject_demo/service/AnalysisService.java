package cs209a.finalproject_demo.service;

import cs209a.finalproject_demo.model.Question;
import cs209a.finalproject_demo.repository.QuestionRepository;
import cs209a.finalproject_demo.service.dto.CategoryCount;
import cs209a.finalproject_demo.service.dto.MultithreadingPitfallResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    /**
     * 关键字与陷阱分类的映射，可复用在其它分析逻辑中。
     */
    public static final Map<String, List<String>> MULTITHREADING_PITFALL_KEYWORDS;

    private static final Map<String, List<Pattern>> MULTITHREADING_PITFALL_PATTERNS;

    private static final List<String> MULTITHREADING_TAGS = List.of(
            "java",
            "multithreading",
            "concurrency",
            "java-threads",
            "thread-safety",
            "synchronization");

    static {
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        keywords.put("Race Conditions", List.of(
                "\\brace condition(s)?\\b",
                "\\bdata race(s)?\\b",
                "\\blost update(s)?\\b",
                "\\bcheck-then-act\\b",
                "\\bread-?modify-?write\\b"));
        keywords.put("Deadlocks", List.of(
                "\\bdeadlock(s)?\\b",
                "\\bcircular wait\\b",
                "\\block ordering\\b",
                "\\blocked forever\\b",
                "\\bwait\\s+forever\\b"));
        keywords.put("Memory Consistency / Visibility", List.of(
                "\\bvolatile\\b",
                "\\bhappens[-\\s]?before\\b",
                "\\bvisibility issue(s)?\\b",
                "\\bmemory barrier\\b",
                "\\bMemoryConsistencyError\\b"));
        keywords.put("Thread Safety (General)", List.of(
                "\\bthread[-\\s]?safe\\b",
                "\\bsynchronized\\b",
                "\\bReentrantLock\\b",
                "\\bConcurrentHashMap\\b",
                "\\bAtomic(?:Integer|Long|Boolean|Reference)\\b"));
        keywords.put("Concurrent Modification", List.of(
                "\\bConcurrentModificationException\\b",
                "\\bmodify while iterat(ing|ion)\\b",
                "\\bfail[-\\s]?fast iterator\\b",
                "\\bIterator\\.remove\\b",
                "\\bCopyOnWriteArrayList\\b"));
        keywords.put("Thread Lifecycle (Start/Join issues)", List.of(
                "\\bIllegalThreadStateException\\b",
                "\\bThread\\.start\\b",
                "\\bthread already started\\b",
                "\\bThread\\.join\\b",
                "\\bInterruptedException\\b"));
        MULTITHREADING_PITFALL_KEYWORDS = Collections.unmodifiableMap(keywords);

        MULTITHREADING_PITFALL_PATTERNS = Collections.unmodifiableMap(
                MULTITHREADING_PITFALL_KEYWORDS.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().stream()
                                        .map(pattern -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE))
                                        .toList(),
                                (left, right) -> left,
                                LinkedHashMap::new)));
    }

    private final QuestionRepository questionRepository;

    public AnalysisService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public MultithreadingPitfallResponse analyzeMultithreadingPitfalls(int requestedTop) {
        List<String> tagFilters = toLowerCaseTags(MULTITHREADING_TAGS);
        List<Long> questionIds = questionRepository.findDistinctIdsByTagNames(tagFilters);
        List<Question> questions = questionIds.isEmpty() ? List.of() : questionRepository.findAllById(questionIds);

        Map<String, Integer> frequency = initFrequencyMap();
        for (Question question : questions) {
            String searchable = combineTitleAndBody(question);
            if (searchable.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, List<Pattern>> entry : MULTITHREADING_PITFALL_PATTERNS.entrySet()) {
                if (matchesAny(searchable, entry.getValue())) {
                    frequency.merge(entry.getKey(), 1, (current, increment) -> current + increment);
                }
            }
        }

        List<CategoryCount> sorted = frequency.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new CategoryCount(entry.getKey(), entry.getValue()))
                .toList();

        int sanitizedTop = Math.max(1, requestedTop);
        int actualTop = Math.min(sanitizedTop, sorted.size());
        List<CategoryCount> topCategories = sorted.subList(0, actualTop);

        return new MultithreadingPitfallResponse(actualTop, topCategories);
    }

    private Map<String, Integer> initFrequencyMap() {
        Map<String, Integer> frequency = new LinkedHashMap<>();
        MULTITHREADING_PITFALL_KEYWORDS.keySet().forEach(category -> frequency.put(category, 0));
        return frequency;
    }

    private List<String> toLowerCaseTags(List<String> tags) {
        return tags.stream()
                .filter(Objects::nonNull)
                .map(tag -> tag.toLowerCase(Locale.ENGLISH))
                .distinct()
                .toList();
    }

    private String combineTitleAndBody(Question question) {
        String title = question.getTitle() == null ? "" : question.getTitle();
        String body = question.getBody() == null ? "" : question.getBody();
        return (title + " " + body).trim();
    }

    private boolean matchesAny(String text, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
