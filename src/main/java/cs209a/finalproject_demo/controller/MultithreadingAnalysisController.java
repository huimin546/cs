package cs209a.finalproject_demo.controller;

import cs209a.finalproject_demo.service.AnalysisService;
import cs209a.finalproject_demo.service.dto.MultithreadingPitfallResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
public class MultithreadingAnalysisController {

    private static final int DEFAULT_TOP = 5;
    private static final int MIN_TOP = 1;
    private static final int MAX_TOP = 6;

    private final AnalysisService analysisService;

    public MultithreadingAnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/multithreading/pitfalls")
    public MultithreadingPitfallResponse getMultithreadingPitfalls(
            @RequestParam(value = "top", required = false) Integer top) {
        int sanitizedTop = sanitizeTop(top);
        return analysisService.analyzeMultithreadingPitfalls(sanitizedTop);
    }

    private int sanitizeTop(Integer requestedTop) {
        if (requestedTop == null) {
            return DEFAULT_TOP;
        }
        if (requestedTop < MIN_TOP || requestedTop > MAX_TOP) {
            throw new IllegalArgumentException(String.format(
                    "Parameter 'top' must be between %d and %d.", MIN_TOP, MAX_TOP));
        }
        return requestedTop;
    }
}
