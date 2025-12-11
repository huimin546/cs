package cs209a.finalproject_demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OwnerDto(
        @JsonProperty("account_id") Long accountId,
        Integer reputation,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("user_type") String userType,
        @JsonProperty("profile_image") String profileImage,
        @JsonProperty("display_name") String displayName,
        String link) {
}
