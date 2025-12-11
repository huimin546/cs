package cs209a.finalproject_demo.service.dto;

import java.util.List;

public record TopicCooccurrenceResponse(
        int top,
        List<TopicCooccurrencePair> pairs) {
}
