package pl.lunasoftware.demo.microservices.candidates.candidate;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.lunasoftware.demo.microservices.candidates.candidate.api.ResourceNotFoundException;
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOfferMatchDto;
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOffersClient;
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOffersSearchRequest;
import pl.lunasoftware.demo.microservices.candidates.skill.CandidateSkillEntity;
import pl.lunasoftware.demo.microservices.candidates.skill.CandidateSkillRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final CandidateSkillRepository candidateSkillRepository;
    private final JobOffersClient jobOffersClient;

    public CandidateService(
            CandidateRepository candidateRepository,
            CandidateSkillRepository candidateSkillRepository,
            JobOffersClient jobOffersClient
    ) {
        this.candidateRepository = candidateRepository;
        this.candidateSkillRepository = candidateSkillRepository;
        this.jobOffersClient = jobOffersClient;
    }

    @WithSpan
    public List<JobOfferMatchDto> findMatchingOffers(UUID candidateId) {
        CandidateEntity candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + candidateId));

        List<CandidateSkillEntity> skills = candidateSkillRepository.findByCandidateId(candidateId);
        if (skills.isEmpty()) {
            log.info("Candidate {} has no skills, returning empty results", candidateId);
            return List.of();
        }

        Set<String> skillNames = skills.stream()
                .map(CandidateSkillEntity::getSkillName)
                .collect(Collectors.toSet());

        JobOffersSearchRequest searchRequest = new JobOffersSearchRequest(
                skillNames,
                candidate.getGeoLat(),
                candidate.getGeoLon(),
                candidate.getRadiusKm(),
                candidate.getExpectedSalary(),
                candidate.getPreferredEmploymentTypes()
        );

        List<JobOfferMatchDto> matches = jobOffersClient.searchOffers(searchRequest);
        log.info("Found {} matching offers for candidate id={}", matches.size(), candidateId);
        return matches;
    }
}
