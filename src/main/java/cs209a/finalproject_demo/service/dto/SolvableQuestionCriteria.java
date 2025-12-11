package cs209a.finalproject_demo.service.dto;

public record SolvableQuestionCriteria(
        boolean requiresAcceptedAnswer,
        int minAcceptedAnswerScore,
        int maxFirstAnswerHours) {
}
