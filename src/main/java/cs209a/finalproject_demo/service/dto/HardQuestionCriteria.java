package cs209a.finalproject_demo.service.dto;

public record HardQuestionCriteria(
        boolean markIfNoAnswers,
        boolean markIfMissingAcceptedAnswer,
        int minAnswerLatencyHours) {
}
