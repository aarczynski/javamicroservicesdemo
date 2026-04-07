package pl.lunasoftware.demo.microservices.candidates.joboffer;

import pl.lunasoftware.demo.microservices.candidates.skill.SeniorityLevel;

public record CandidateSkillDto(String skillName, SeniorityLevel seniorityLevel) {}
