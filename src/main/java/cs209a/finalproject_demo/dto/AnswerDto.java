package cs209a.finalproject_demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnswerDto(
        OwnerDto owner,
        @JsonProperty("is_accepted") Boolean accepted,
        Integer score,
        @JsonProperty("last_activity_date") Long lastActivityDate,
        @JsonProperty("creation_date") Long creationDate,
        @JsonProperty("answer_id") Long answerId,
        String body) {
}
