package pl.lunasoftware.demo.microservices.candidates.joboffer;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "job-offers",
        configuration = JobOffersClientConfig.class
)
public interface JobOffersClient {

    @PostMapping("/api/v1/job-offers/search")
    List<JobOfferMatchDto> searchOffers(@RequestBody JobOffersSearchRequest request);
}
