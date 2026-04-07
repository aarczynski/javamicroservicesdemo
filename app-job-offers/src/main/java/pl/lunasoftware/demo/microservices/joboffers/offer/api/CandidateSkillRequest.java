package pl.lunasoftware.demo.microservices.joboffers.offer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.lunasoftware.demo.microservices.joboffers.skill.SeniorityLevel;

public record CandidateSkillRequest(
        @NotBlank String skillName,
        @NotNull SeniorityLevel seniorityLevel
) {}
