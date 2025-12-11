package cs209a.finalproject_demo.service.dto;

public enum TopicTrendMetric {
    QUESTIONS,
    SCORE;

    public static TopicTrendMetric from(String raw) {
        if (raw == null || raw.isBlank()) {
            return QUESTIONS;
        }
        try {
            return TopicTrendMetric.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return QUESTIONS;
        }
    }
}
