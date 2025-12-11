package cs209a.finalproject_demo.service.dto;

import java.util.List;

public record SolvabilityComparisonResponse(
        SolvabilityCriteria criteria,
        SolvabilityTotals totals,
        List<SolvabilityFactor> factors,
        List<SolvabilityTagStat> solvableTopTags,
        List<SolvabilityTagStat> hardTopTags) {
}
