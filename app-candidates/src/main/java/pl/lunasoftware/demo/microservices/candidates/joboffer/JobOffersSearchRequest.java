package pl.lunasoftware.demo.microservices.candidates.joboffer;

import pl.lunasoftware.demo.microservices.candidates.candidate.EmploymentType;

import java.math.BigDecimal;
import java.util.Set;

public record JobOffersSearchRequest(
        Set<CandidateSkillDto> candidateSkills,
        double geoLat,
        double geoLon,
        double radiusKm,
        BigDecimal expectedSalary,
        Set<EmploymentType> preferredEmploymentTypes,
        int yearsOfExperience,
        int preferredRemoteDaysPercentage
) {}
