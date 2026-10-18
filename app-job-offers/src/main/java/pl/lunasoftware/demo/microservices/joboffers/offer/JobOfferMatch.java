package pl.lunasoftware.demo.microservices.joboffers.offer;

import pl.lunasoftware.demo.microservices.joboffers.skill.SeniorityLevel;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record JobOfferMatch(
        UUID id,
        UUID companyId,
        String companyName,
        double companyGeoLat,
        double companyGeoLon,
        String title,
        String description,
        BigDecimal salaryFrom,
        BigDecimal salaryTo,
        String currency,
        int requiredYearsOfExperience,
        int requiredOfficeDaysPercentage,
        JobOfferStatus status,
        Set<EmploymentType> employmentTypes,
        List<RequiredSkill> skills
) {

    public record RequiredSkill(String name, SeniorityLevel requiredSeniorityLevel, boolean mandatory, double weight) {
    }

    public JobOfferMatch(UUID id, UUID companyId, String companyName, Double companyGeoLat, Double companyGeoLon,
                         String title, String description, BigDecimal salaryFrom, BigDecimal salaryTo,
                         String currency, Integer requiredYearsOfExperience, Integer requiredOfficeDaysPercentage,
                         String status, String[] employmentTypes, String[] skillNames,
                         String[] skillSeniorityLevels, Boolean[] skillMandatory, Double[] skillWeights) {
        this(id, companyId, companyName, companyGeoLat, companyGeoLon, title, description, salaryFrom, salaryTo,
                currency, requiredYearsOfExperience, requiredOfficeDaysPercentage, JobOfferStatus.valueOf(status),
                toEmploymentTypes(employmentTypes),
                toRequiredSkills(skillNames, skillSeniorityLevels, skillMandatory, skillWeights));
    }

    private static Set<EmploymentType> toEmploymentTypes(String[] employmentTypes) {
        return Arrays.stream(employmentTypes)
                .map(EmploymentType::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<RequiredSkill> toRequiredSkills(String[] names, String[] seniorityLevels,
                                                        Boolean[] mandatory, Double[] weights) {
        return IntStream.range(0, names.length)
                .mapToObj(i -> new RequiredSkill(
                        names[i], SeniorityLevel.valueOf(seniorityLevels[i]), mandatory[i], weights[i]))
                .toList();
    }
}
