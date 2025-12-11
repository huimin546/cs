package cs209a.finalproject_demo.controller;

import cs209a.finalproject_demo.service.SolvabilityAnalysisService;
import cs209a.finalproject_demo.service.dto.SolvabilityComparisonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
public class SolvabilityAnalysisController {

    private final SolvabilityAnalysisService solvabilityAnalysisService;

    public SolvabilityAnalysisController(SolvabilityAnalysisService solvabilityAnalysisService) {
        this.solvabilityAnalysisService = solvabilityAnalysisService;
    }

    @GetMapping("/solvability/compare")
    public SolvabilityComparisonResponse compareSolvability(
            @RequestParam(value = "minAcceptedAnswerScore", required = false) Integer minAcceptedAnswerScore,
            @RequestParam(value = "maxFirstAnswerHours", required = false) Integer maxFirstAnswerHours,
            @RequestParam(value = "hardMinAnswerLatencyHours", required = false) Integer hardMinAnswerLatencyHours) {
        return solvabilityAnalysisService.compareSolvability(
                minAcceptedAnswerScore,
                maxFirstAnswerHours,
                hardMinAnswerLatencyHours);
    }
}
