package cs209a.finalproject_demo.service.dto;

public record SolvabilityFactor(
        String name,
        double solvableValue,
        double hardValue,
        String unit) {
}
