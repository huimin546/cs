package cs209a.finalproject_demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentDto(
        OwnerDto owner,
        Boolean edited,
        Integer score,
        @JsonProperty("creation_date") Long creationDate,
        @JsonProperty("post_id") Long postId,
        @JsonProperty("comment_id") Long commentId,
        String body) {
}
