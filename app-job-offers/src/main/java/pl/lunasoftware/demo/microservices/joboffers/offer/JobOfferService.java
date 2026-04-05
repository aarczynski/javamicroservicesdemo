package pl.lunasoftware.demo.microservices.joboffers.offer;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.lunasoftware.demo.microservices.joboffers.company.CompanyEntity;
import pl.lunasoftware.demo.microservices.joboffers.offer.api.CandidateSearchRequest;
import pl.lunasoftware.demo.microservices.joboffers.offer.api.CandidateSkillRequest;
import pl.lunasoftware.demo.microservices.joboffers.offer.api.JobOfferMatchDto;
import pl.lunasoftware.demo.microservices.joboffers.skill.SeniorityLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JobOfferService {

    private static final double KM_PER_DEGREE = 111.0;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private static final double WEIGHT_SKILLS = 0.50;
    private static final double WEIGHT_SALARY = 0.20;
    private static final double WEIGHT_DISTANCE = 0.15;
    private static final double WEIGHT_EXPERIENCE = 0.15;

    private final JobOfferRepository jobOfferRepository;

    public JobOfferService(JobOfferRepository jobOfferRepository) {
        this.jobOfferRepository = jobOfferRepository;
    }

    @WithSpan
    public List<JobOfferMatchDto> search(CandidateSearchRequest request) {
        double[] bbox = boundingBox(request.geoLat(), request.geoLon(), request.radiusKm());
        Set<String> skillNames = request.candidateSkills().stream()
                .map(CandidateSkillRequest::skillName)
                .collect(Collectors.toSet());
        List<JobOfferEntity> offers = jobOfferRepository.findCandidateMatches(
                bbox[0], bbox[1], bbox[2], bbox[3],
                request.expectedSalary(),
                request.preferredEmploymentTypes(),
                skillNames
        );
        log.info("Found {} candidate-matching offers", offers.size());

        return offers.stream()
                .map(offer -> toMatchDto(offer, calculateScore(offer, request)))
                .sorted(Comparator.comparingDouble(JobOfferMatchDto::score).reversed())
                .toList();
    }

    private double calculateScore(JobOfferEntity offer, CandidateSearchRequest request) {
        double distScore = scoreDistance(offer.getCompany(), request.geoLat(), request.geoLon(), request.radiusKm());
        double skillScore = scoreSkills(offer.getSkills(), request.candidateSkills());
        double salaryScore = scoreSalary(offer.getSalaryTo(), request.expectedSalary());
        double expScore = scoreExperience(offer.getRequiredYearsOfExperience(), request.yearsOfExperience());
        return WEIGHT_SKILLS * skillScore
                + WEIGHT_SALARY * salaryScore
                + WEIGHT_DISTANCE * distScore
                + WEIGHT_EXPERIENCE * expScore;
    }

    /**
     * Haversine distance, then cosine decay: score=1.0 at d=0, score=0.0 at d=radius.
     * Cosine gives a gentle plateau near the candidate and a steeper drop near the boundary.
     */
    private double scoreDistance(CompanyEntity company, double candLat, double candLon, double radiusKm) {
        double distKm = haversineKm(candLat, candLon, company.getGeoLat(), company.getGeoLon());
        if (distKm >= radiusKm) return 0.0;
        return Math.cos(Math.PI / 2 * distKm / radiusKm);
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Weighted skill coverage adjusted for seniority. Each missing mandatory skill halves the score.
     * Seniority gap: score halves per level below required (gap=1 → ×0.5, gap=2 → ×0.25, …).
     */
    private double scoreSkills(List<JobOfferSkillEntity> offerSkills, Set<CandidateSkillRequest> candidateSkills) {
        if (offerSkills.isEmpty()) return 0.0;

        Map<String, SeniorityLevel> candidateSkillMap = candidateSkills.stream()
                .collect(Collectors.toMap(CandidateSkillRequest::skillName, CandidateSkillRequest::seniorityLevel));

        double totalWeight = offerSkills.stream()
                .mapToDouble(s -> s.getWeight().doubleValue())
                .sum();
        if (totalWeight == 0) return 0.0;

        double weightedCoverage = offerSkills.stream()
                .mapToDouble(offerSkill -> {
                    SeniorityLevel candidateLevel = candidateSkillMap.get(offerSkill.getSkill().getName());
                    if (candidateLevel == null) return 0.0;
                    return offerSkill.getWeight().doubleValue()
                            * seniorityFactor(candidateLevel, offerSkill.getRequiredSeniorityLevel());
                })
                .sum() / totalWeight;

        long missedMandatory = offerSkills.stream()
                .filter(JobOfferSkillEntity::isMandatory)
                .filter(s -> !candidateSkillMap.containsKey(s.getSkill().getName()))
                .count();

        return weightedCoverage * Math.pow(0.5, missedMandatory);
    }

    private double seniorityFactor(SeniorityLevel candidateLevel, SeniorityLevel requiredLevel) {
        if (requiredLevel == null) return 1.0;
        int gap = requiredLevel.ordinal() - candidateLevel.ordinal();
        if (gap <= 0) return 1.0;
        return Math.pow(0.5, gap);
    }

    /**
     * Quadratic decay: score=1.0 when expectedSalary=0, score=0.0 when expectedSalary=salaryTo.
     * Candidates expecting well below the offer ceiling are easier to hire.
     */
    private double scoreSalary(BigDecimal salaryTo, BigDecimal expectedSalary) {
        double max = salaryTo.doubleValue();
        if (max == 0) return 0.0;
        double ratio = Math.min(1.0, expectedSalary.doubleValue() / max);
        return 1.0 - ratio * ratio;
    }

    /**
     * Exponential decay: score drops ~26% per missing year of experience.
     */
    private double scoreExperience(int requiredYears, int candidateYears) {
        return Math.exp(-0.3 * Math.max(0, requiredYears - candidateYears));
    }

    private double[] boundingBox(double lat, double lon, double radiusKm) {
        double latMin = lat - radiusKm / KM_PER_DEGREE;
        double latMax = lat + radiusKm / KM_PER_DEGREE;
        double lonDelta = radiusKm / (KM_PER_DEGREE * Math.cos(Math.toRadians(lat)));
        return new double[]{latMin, latMax, lon - lonDelta, lon + lonDelta};
    }

    private JobOfferMatchDto toMatchDto(JobOfferEntity e, double score) {
        String companyName = e.getCompany() != null ? e.getCompany().getName() : null;
        return new JobOfferMatchDto(
                e.getId(),
                e.getCompanyId(),
                companyName,
                e.getTitle(),
                e.getDescription(),
                e.getSalaryFrom().setScale(2, RoundingMode.HALF_UP),
                e.getSalaryTo().setScale(2, RoundingMode.HALF_UP),
                e.getCurrency(),
                e.getOfferedEmploymentTypes(),
                e.getStatus(),
                score
        );
    }
}
