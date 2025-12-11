package cs209a.finalproject_demo.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import cs209a.finalproject_demo.dto.AnswerDto;
import cs209a.finalproject_demo.dto.CommentDto;
import cs209a.finalproject_demo.dto.OwnerDto;
import cs209a.finalproject_demo.dto.QuestionDto;
import cs209a.finalproject_demo.dto.StackOverflowThreadDto;
import cs209a.finalproject_demo.model.Answer;
import cs209a.finalproject_demo.model.AnswerComment;
import cs209a.finalproject_demo.model.Question;
import cs209a.finalproject_demo.model.QuestionComment;
import cs209a.finalproject_demo.model.Tag;
import cs209a.finalproject_demo.repository.QuestionRepository;
import cs209a.finalproject_demo.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@RequiredArgsConstructor
public class StackOverflowDataImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StackOverflowDataImportRunner.class);

    private final ObjectMapper objectMapper;
    private final QuestionRepository questionRepository;
    private final TagRepository tagRepository;

    @Value("${app.data.zip-path:Sample_SO_data.zip}")
    private String zipPath;

    @Value("${app.data.import-threshold:1000}")
    private int importThreshold;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path archive = Paths.get(zipPath);
        if (!Files.exists(archive)) {
            log.warn("找不到資料檔案：{}，略過自動匯入。", archive.toAbsolutePath());
            return;
        }

        long existing = questionRepository.count();
        if (existing >= importThreshold) {
            log.info("資料庫已有 {} 筆 Question，達到門檻 {}，不再重複匯入。", existing, importThreshold);
            return;
        }

        long target = importThreshold - existing;
        log.info("開始載入 Stack Overflow 線程資料，目標再匯入 {} 筆 (目前 {} 筆)。", target, existing);

        int imported = importThreads(archive, target);
        log.info("匯入完成，共新增 {} 筆 Question，當前總數 {}。", imported, existing + imported);
    }

    private int importThreads(Path archive, long target) {
        Map<String, Tag> tagCache = new HashMap<>();
        int imported = 0;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".json")) {
                    continue;
                }
                if (target > 0 && imported >= target) {
                    break;
                }

                StackOverflowThreadDto threadDto = readEntry(zis, entry.getName());
                zis.closeEntry();
                if (threadDto == null || threadDto.question() == null) {
                    continue;
                }

                QuestionDto questionDto = threadDto.question();
                Long questionId = questionDto.questionId();
                if (questionId == null) {
                    continue;
                }
                if (questionRepository.existsById(questionId)) {
                    continue;
                }

                Question question = mapQuestion(questionDto, tagCache);
                if (question == null) {
                    continue;
                }

                Map<Long, List<CommentDto>> answerCommentMap = normalizeAnswerComments(threadDto.answerComments());
                if (threadDto.answers() != null) {
                    for (AnswerDto answerDto : threadDto.answers()) {
                        Answer answer = mapAnswer(answerDto, question);
                        if (answer == null) {
                            continue;
                        }
                        List<CommentDto> relatedComments = answerCommentMap.getOrDefault(answer.getId(), List.of());
                        relatedComments.stream()
                                .map(commentDto -> mapAnswerComment(commentDto, answer))
                                .filter(Objects::nonNull)
                                .forEach(answer.getComments()::add);
                        question.getAnswers().add(answer);
                    }
                }

                if (threadDto.questionComments() != null) {
                    threadDto.questionComments().stream()
                            .map(commentDto -> mapQuestionComment(commentDto, question))
                            .filter(Objects::nonNull)
                            .forEach(question.getQuestionComments()::add);
                }

                questionRepository.save(question);
                imported++;
            }
        } catch (IOException e) {
            throw new RuntimeException("匯入 Stack Overflow 資料失敗", e);
        }
        return imported;
    }

    private StackOverflowThreadDto readEntry(ZipInputStream zis, String entryName) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = zis.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return objectMapper.readValue(buffer.toByteArray(), StackOverflowThreadDto.class);
        } catch (IOException e) {
            log.error("解析 {} 失敗：{}", entryName, e.getMessage());
            return null;
        }
    }

    private Question mapQuestion(QuestionDto dto, Map<String, Tag> tagCache) {
        Question question = new Question();
        question.setId(dto.questionId());
        question.setTitle(dto.title());
        question.setBody(dto.body());
        question.setAnswered(dto.isAnswered());
        question.setViewCount(dto.viewCount());
        question.setAnswerCount(dto.answerCount());
        question.setScore(dto.score());
        question.setQuestionLink(dto.link());
        question.setCreationDate(toInstant(dto.creationDate()));
        question.setLastActivityDate(toInstant(dto.lastActivityDate()));
        question.setClosedDate(toInstant(dto.closedDate()));
        question.setClosedReason(dto.closedReason());
        question.setAcceptedAnswerId(dto.acceptedAnswerId());

        OwnerDto owner = dto.owner();
        if (owner != null) {
            question.setOwnerUserId(owner.userId());
            question.setOwnerReputation(owner.reputation());
            question.setOwnerDisplayName(owner.displayName());
            question.setOwnerProfileImage(owner.profileImage());
            question.setOwnerLink(owner.link());
        }

        if (dto.tags() != null) {
            Set<Tag> tags = dto.tags().stream()
                    .filter(tag -> tag != null && !tag.isBlank())
                    .map(tag -> tag.toLowerCase(Locale.ENGLISH))
                    .map(tag -> resolveTag(tag, tagCache))
                    .collect(Collectors.toSet());
            question.getTags().addAll(tags);
        }
        return question;
    }

    private Tag resolveTag(String tagName, Map<String, Tag> tagCache) {
        if (tagCache.containsKey(tagName)) {
            return tagCache.get(tagName);
        }
        Optional<Tag> existing = tagRepository.findByName(tagName);
        Tag tag = existing.orElseGet(() -> tagRepository.save(new Tag(tagName)));
        tagCache.put(tagName, tag);
        return tag;
    }

    private Answer mapAnswer(AnswerDto dto, Question question) {
        if (dto == null || dto.answerId() == null) {
            return null;
        }
        Answer answer = new Answer();
        answer.setId(dto.answerId());
        answer.setQuestion(question);
        answer.setBody(dto.body());
        answer.setAccepted(dto.accepted());
        answer.setScore(dto.score());
        answer.setCreationDate(toInstant(dto.creationDate()));
        answer.setLastActivityDate(toInstant(dto.lastActivityDate()));

        OwnerDto owner = dto.owner();
        if (owner != null) {
            answer.setOwnerUserId(owner.userId());
            answer.setOwnerReputation(owner.reputation());
            answer.setOwnerDisplayName(owner.displayName());
            answer.setOwnerProfileImage(owner.profileImage());
            answer.setOwnerLink(owner.link());
        }
        return answer;
    }

    private QuestionComment mapQuestionComment(CommentDto dto, Question question) {
        if (dto == null || dto.commentId() == null) {
            return null;
        }
        QuestionComment comment = new QuestionComment();
        comment.setId(dto.commentId());
        comment.setQuestion(question);
        comment.setBody(dto.body());
        comment.setScore(dto.score());
        comment.setCreationDate(toInstant(dto.creationDate()));
        OwnerDto owner = dto.owner();
        if (owner != null) {
            comment.setOwnerUserId(owner.userId());
            comment.setOwnerReputation(owner.reputation());
            comment.setOwnerDisplayName(owner.displayName());
            comment.setOwnerProfileImage(owner.profileImage());
            comment.setOwnerLink(owner.link());
        }
        return comment;
    }

    private AnswerComment mapAnswerComment(CommentDto dto, Answer answer) {
        if (dto == null || dto.commentId() == null) {
            return null;
        }
        AnswerComment comment = new AnswerComment();
        comment.setId(dto.commentId());
        comment.setAnswer(answer);
        comment.setBody(dto.body());
        comment.setScore(dto.score());
        comment.setCreationDate(toInstant(dto.creationDate()));
        OwnerDto owner = dto.owner();
        if (owner != null) {
            comment.setOwnerUserId(owner.userId());
            comment.setOwnerReputation(owner.reputation());
            comment.setOwnerDisplayName(owner.displayName());
            comment.setOwnerProfileImage(owner.profileImage());
            comment.setOwnerLink(owner.link());
        }
        return comment;
    }

    private Map<Long, List<CommentDto>> normalizeAnswerComments(Map<String, List<CommentDto>> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<CommentDto>> normalized = new HashMap<>();
        raw.forEach((key, value) -> {
            try {
                Long answerId = Long.valueOf(key);
                normalized.put(answerId, value != null ? new ArrayList<>(value) : List.of());
            } catch (NumberFormatException ignored) {
                log.debug("無法解析 answer_comments 的 key：{}", key);
            }
        });
        return normalized;
    }

    private Instant toInstant(Long epochSeconds) {
        return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
    }
}
