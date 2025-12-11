package cs209a.finalproject_demo.service;

import cs209a.finalproject_demo.repository.QuestionRepository;
import cs209a.finalproject_demo.repository.projection.TagPairRow;
import cs209a.finalproject_demo.service.dto.TopicCooccurrencePair;
import cs209a.finalproject_demo.service.dto.TopicCooccurrenceResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TopicCooccurrenceService {

    private static final int DEFAULT_TOP = 10;
    private static final int MIN_TOP = 1;
    private static final int MAX_TOP = 50;

    private final QuestionRepository questionRepository;

    public TopicCooccurrenceService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public TopicCooccurrenceResponse getTopPairs(Integer requestedTop) {
        int top = sanitizeTop(requestedTop);
        List<TopicCooccurrencePair> pairs = questionRepository.findTopTagPairs(top).stream()
                .map(this::toDto)
                .toList();
        return new TopicCooccurrenceResponse(top, pairs);
    }

    private TopicCooccurrencePair toDto(TagPairRow row) {
        return new TopicCooccurrencePair(row.getTagA(), row.getTagB(), row.getPairCount());
    }

    private int sanitizeTop(Integer requestedTop) {
        if (requestedTop == null) {
            return DEFAULT_TOP;
        }
        if (requestedTop < MIN_TOP || requestedTop > MAX_TOP) {
            throw new IllegalArgumentException(String.format(
                    "Parameter 'top' must be between %d and %d.", MIN_TOP, MAX_TOP));
        }
        return requestedTop;
    }
}
