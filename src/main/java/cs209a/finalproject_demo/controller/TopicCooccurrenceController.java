package cs209a.finalproject_demo.controller;

import cs209a.finalproject_demo.service.TopicCooccurrenceService;
import cs209a.finalproject_demo.service.dto.TopicCooccurrenceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
public class TopicCooccurrenceController {

    private final TopicCooccurrenceService topicCooccurrenceService;

    public TopicCooccurrenceController(TopicCooccurrenceService topicCooccurrenceService) {
        this.topicCooccurrenceService = topicCooccurrenceService;
    }

    @GetMapping("/cooccurrence")
    public TopicCooccurrenceResponse getTopPairs(@RequestParam(name = "top", required = false) Integer top) {
        return topicCooccurrenceService.getTopPairs(top);
    }
}
