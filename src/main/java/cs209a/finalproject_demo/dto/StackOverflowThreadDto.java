package cs209a.finalproject_demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StackOverflowThreadDto(
        QuestionDto question,
        List<AnswerDto> answers,
        @JsonProperty("question_comments") List<CommentDto> questionComments,
        @JsonProperty("answer_comments") Map<String, List<CommentDto>> answerComments) {
}
