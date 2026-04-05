package pl.lunasoftware.demo.microservices.candidates.candidate;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.lunasoftware.demo.microservices.candidates.candidate.api.ResourceNotFoundException;
import pl.lunasoftware.demo.microservices.candidates.joboffer.CandidateSkillDto;
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOfferMatchDto;
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOffersClient;
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOffersSearchRequest;
import pl.lunasoftware.demo.microservices.candidates.skill.CandidateSkillEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final JobOffersClient jobOffersClient;

    public CandidateService(CandidateRepository candidateRepository, JobOffersClient jobOffersClient) {
        this.candidateRepository = candidateRepository;
        this.jobOffersClient = jobOffersClient;
    }

    @WithSpan
    public List<JobOfferMatchDto> findMatchingOffers(UUID candidateId) {
        CandidateEntity candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate " + candidateId + " not found"));

        List<CandidateSkillEntity> skills = candidate.getSkills();
        if (skills.isEmpty()) {
            log.info("Candidate {} has no skills, returning empty results", candidateId);
            return List.of();
        }

        Set<CandidateSkillDto> candidateSkills = skills.stream()
                .map(s -> new CandidateSkillDto(s.getSkillName(), s.getSeniorityLevel()))
                .collect(Collectors.toSet());

        JobOffersSearchRequest searchRequest = new JobOffersSearchRequest(
                candidateSkills,
                candidate.getGeoLat(),
                candidate.getGeoLon(),
                candidate.getRadiusKm(),
                candidate.getExpectedSalary(),
                candidate.getPreferredEmploymentTypes(),
                candidate.getYearsOfExperience()
        );

        List<JobOfferMatchDto> matches = jobOffersClient.searchOffers(searchRequest);
        log.info("Found {} matching offers for candidate id={}", matches.size(), candidateId);
        return matches;
    }
}
