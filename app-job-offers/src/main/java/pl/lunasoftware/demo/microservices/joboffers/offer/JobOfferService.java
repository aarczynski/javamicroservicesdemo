package pl.lunasoftware.demo.microservices.joboffers.offer;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferMatch.RequiredSkill;
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

    private static final double WEIGHT_SKILLS = 0.45;
    private static final double WEIGHT_SALARY = 0.20;
    private static final double WEIGHT_DISTANCE = 0.15;
    private static final double WEIGHT_EXPERIENCE = 0.10;
    private static final double WEIGHT_REMOTE = 0.10;

    private final JobOfferRepository jobOfferRepository;

    public JobOfferService(JobOfferRepository jobOfferRepository) {
        this.jobOfferRepository = jobOfferRepository;
    }

    @WithSpan
    public List<JobOfferMatchDto> search(CandidateSearchRequest request) {
        double[] bbox = boundingBox(request.geoLat(), request.geoLon(), request.radiusKm());
        String[] skillNames = request.candidateSkills().stream()
                .map(CandidateSkillRequest::skillName)
                .distinct()
                .toArray(String[]::new);
        String[] employmentTypes = request.preferredEmploymentTypes().stream()
                .map(EmploymentType::name)
                .toArray(String[]::new);
        List<JobOfferMatch> offers = jobOfferRepository.findCandidateMatches(
                bbox[0], bbox[1], bbox[2], bbox[3],
                request.expectedSalary(),
                employmentTypes,
                skillNames
        );
        log.info("Found {} candidate-matching offers", offers.size());

        return offers.stream()
                .map(offer -> toMatchDto(offer, calculateScore(offer, request)))
                .sorted(Comparator.comparingDouble(JobOfferMatchDto::score).reversed())
                .toList();
    }

    private double calculateScore(JobOfferMatch offer, CandidateSearchRequest request) {
        double skillScore = scoreSkills(offer.skills(), request.candidateSkills());
        double salaryScore = scoreSalary(offer.salaryTo(), request.expectedSalary());
        double expScore = scoreExperience(offer.requiredYearsOfExperience(), request.yearsOfExperience());
        double remoteScore = scoreRemote(offer.requiredOfficeDaysPercentage(), request.preferredRemoteDaysPercentage());
        double distScore = scoreDistance(offer, request.geoLat(), request.geoLon(), request.radiusKm());
        return WEIGHT_SKILLS * skillScore
                + WEIGHT_SALARY * salaryScore
                + WEIGHT_DISTANCE * distScore
                + WEIGHT_EXPERIENCE * expScore
                + WEIGHT_REMOTE * remoteScore;
    }

    /**
     * Weighted skill coverage adjusted for seniority. Each missing mandatory skill halves the score.
     * Seniority gap: score halves per level below required (gap=1 → ×0.5, gap=2 → ×0.25, …).
     */
    private double scoreSkills(List<RequiredSkill> offerSkills, Set<CandidateSkillRequest> candidateSkills) {
        if (offerSkills.isEmpty()) {
            return 0.0;
        }

        Map<String, SeniorityLevel> candidateSkillMap = candidateSkills.stream()
                .collect(Collectors.toMap(CandidateSkillRequest::skillName, CandidateSkillRequest::seniorityLevel));

        double totalWeight = offerSkills.stream()
                .mapToDouble(RequiredSkill::weight)
                .sum();
        if (totalWeight == 0) {
            return 0.0;
        }

        double weightedCoverage = offerSkills.stream()
                .mapToDouble(offerSkill -> {
                    SeniorityLevel candidateLevel = candidateSkillMap.get(offerSkill.name());
                    if (candidateLevel == null) {
                        return 0.0;
                    }
                    return offerSkill.weight()
                            * seniorityFactor(candidateLevel, offerSkill.requiredSeniorityLevel());
                })
                .sum() / totalWeight;

        long missedMandatory = offerSkills.stream()
                .filter(RequiredSkill::mandatory)
                .filter(s -> !candidateSkillMap.containsKey(s.name()))
                .count();

        return weightedCoverage * Math.pow(0.5, missedMandatory);
    }

    private double seniorityFactor(SeniorityLevel candidateLevel, SeniorityLevel requiredLevel) {
        if (requiredLevel == null) {
            return 1.0;
        }
        int gap = requiredLevel.ordinal() - candidateLevel.ordinal();
        if (gap <= 0) {
            return 1.0;
        }
        return Math.pow(0.5, gap);
    }

    private double scoreSalary(BigDecimal salaryTo, BigDecimal expectedSalary) {
        double max = salaryTo.doubleValue();
        if (max == 0) {
            return 0.0;
        }
        double expected = expectedSalary.doubleValue();

        if (max >= expected) {
            return 1.0;
        }

        double ratio = max / expected;
        return Math.exp(-3.0 * (1.0 - ratio));
    }

    /**
     * Exponential decay: score drops ~26% per missing year of experience.
     */
    private double scoreExperience(int requiredYears, int candidateYears) {
        return Math.exp(-0.3 * Math.max(0, requiredYears - candidateYears));
    }

    /**
     * Cosine decay on the mismatch between offer's office requirement and candidate's willingness.
     * Gentle plateau for small differences, steep drop for large mismatches.
     */
    private double scoreRemote(int requiredOfficeDaysPercentage, int preferredRemoteDaysPercentage) {
        int candidateOfficeWillingness = 100 - preferredRemoteDaysPercentage;
        int mismatch = Math.max(0, requiredOfficeDaysPercentage - candidateOfficeWillingness);
        return Math.cos(Math.PI / 2 * mismatch / 100.0);
    }

    /**
     * Haversine distance with cosine decay, blended with remote allowance.
     * Fully remote offers (requiredOfficeDaysPercentage=0) always score 1.0.
     * Fully in-office offers use pure cosine decay: score=1.0 at d=0, score=0.0 at d=radius.
     * Hybrid offers blend proportionally: officeRatio * geoScore + (1 - officeRatio).
     */
    private double scoreDistance(JobOfferMatch offer, double candLat, double candLon, double radiusKm) {
        double officeRatio = offer.requiredOfficeDaysPercentage() / 100.0;
        double distKm = haversineKm(candLat, candLon, offer.companyGeoLat(), offer.companyGeoLon());
        double geoScore = distKm >= radiusKm ? 0.0 : Math.cos(Math.PI / 2 * distKm / radiusKm);
        return officeRatio * geoScore + (1.0 - officeRatio);
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }


    private double[] boundingBox(double lat, double lon, double radiusKm) {
        double latMin = lat - radiusKm / KM_PER_DEGREE;
        double latMax = lat + radiusKm / KM_PER_DEGREE;
        double lonDelta = radiusKm / (KM_PER_DEGREE * Math.cos(Math.toRadians(lat)));
        return new double[]{latMin, latMax, lon - lonDelta, lon + lonDelta};
    }

    private JobOfferMatchDto toMatchDto(JobOfferMatch offer, double score) {
        return new JobOfferMatchDto(
                offer.id(),
                offer.companyId(),
                offer.companyName(),
                offer.title(),
                offer.description(),
                offer.salaryFrom().setScale(2, RoundingMode.HALF_UP),
                offer.salaryTo().setScale(2, RoundingMode.HALF_UP),
                offer.currency(),
                offer.requiredOfficeDaysPercentage(),
                offer.employmentTypes(),
                offer.status(),
                score
        );
    }
}
