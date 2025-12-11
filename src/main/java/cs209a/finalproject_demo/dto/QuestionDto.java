package cs209a.finalproject_demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionDto(
        List<String> tags,
        OwnerDto owner,
        @JsonProperty("is_answered") Boolean isAnswered,
        @JsonProperty("view_count") Integer viewCount,
        @JsonProperty("closed_date") Long closedDate,
        @JsonProperty("answer_count") Integer answerCount,
        Integer score,
        @JsonProperty("last_activity_date") Long lastActivityDate,
        @JsonProperty("creation_date") Long creationDate,
        @JsonProperty("question_id") Long questionId,
        String link,
        @JsonProperty("closed_reason") String closedReason,
        String title,
        String body,
        @JsonProperty("accepted_answer_id") Long acceptedAnswerId) {
}
