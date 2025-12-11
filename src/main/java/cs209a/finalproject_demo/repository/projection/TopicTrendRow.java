package cs209a.finalproject_demo.repository.projection;

import java.time.Instant;

public interface TopicTrendRow {
    String getTag();

    Instant getBucket();

    Long getQuestionCount();

    Long getScoreSum();
}
