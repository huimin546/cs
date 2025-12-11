package cs209a.finalproject_demo.service.dto;

public record TopicCooccurrencePair(
        String tagA,
        String tagB,
        long questionCount) {
}
