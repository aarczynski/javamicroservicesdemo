package pl.lunasoftware.demo.microservices.joboffers.offer;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.lunasoftware.demo.microservices.joboffers.offer.api.CandidateSearchRequest;
import pl.lunasoftware.demo.microservices.joboffers.offer.api.JobOfferMatchDto;

import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class JobOfferService {

    private final JobOfferRepository jobOfferRepository;

    public JobOfferService(JobOfferRepository jobOfferRepository) {
        this.jobOfferRepository = jobOfferRepository;
    }

    @WithSpan
    public List<JobOfferMatchDto> search(CandidateSearchRequest request) {
        double[] bbox = boundingBox(request.geoLat(), request.geoLon(), request.radiusKm());
        List<JobOfferEntity> offers = jobOfferRepository.findCandidateMatches(
                bbox[0], bbox[1], bbox[2], bbox[3],
                request.expectedSalary(),
                request.preferredEmploymentTypes(),
                request.skillNames()
        );
        log.info("Found {} candidate-matching offers", offers.size());

        return offers.stream()
                .map(offer -> toMatchDto(offer, calculateScore(offer, request.skillNames())))
                .sorted(Comparator.comparingDouble(JobOfferMatchDto::score).reversed())
                .toList();
    }

    private double calculateScore(JobOfferEntity offer, Set<String> candidateSkillNames) {
        List<JobOfferSkillEntity> offerSkills = offer.getSkills();
        if (offerSkills.isEmpty()) {
            return 0.0;
        }
        double totalWeight = offerSkills.stream()
                .mapToDouble(s -> s.getWeight().doubleValue())
                .sum();
        double matchedWeight = offerSkills.stream()
                .filter(s -> candidateSkillNames.contains(s.getSkill().getName()))
                .mapToDouble(s -> s.getWeight().doubleValue())
                .sum();
        return totalWeight > 0 ? matchedWeight / totalWeight : 0.0;
    }

    private static double[] boundingBox(double lat, double lon, double radiusKm) {
        double latMin = lat - radiusKm / 111.0;
        double latMax = lat + radiusKm / 111.0;
        double lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
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
