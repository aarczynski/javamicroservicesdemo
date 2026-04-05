package pl.lunasoftware.demo.microservices.joboffers.offer.api;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/job-offers")
public class JobOfferController {

    private final JobOfferService jobOfferService;

    public JobOfferController(JobOfferService jobOfferService) {
        this.jobOfferService = jobOfferService;
    }

    @PostMapping("/search")
    public List<JobOfferMatchDto> search(@Valid @RequestBody CandidateSearchRequest request) {
        log.info("Received candidate search request, lat={}, lon={}, radius={}km",
                request.geoLat(), request.geoLon(), request.radiusKm());
        return jobOfferService.search(request);
    }

    @Slf4j
    @RestControllerAdvice(assignableTypes = JobOfferController.class)
    static class JobOfferExceptionHandler {

        @ExceptionHandler(ResourceNotFoundException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        ErrorResponse handleNotFound(ResourceNotFoundException ex) {
            log.warn("404 Not Found: {}", ex.getMessage());
            return new ErrorResponse(ex.getMessage());
        }

        @ExceptionHandler(BadRequestException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        ErrorResponse handleBadRequest(BadRequestException ex) {
            log.warn("400 Bad Request: {}", ex.getMessage());
            return new ErrorResponse(ex.getMessage());
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
            String message = ex.getBindingResult().getFieldErrors().stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            log.warn("400 Bad Request - validation failed: {}", message);
            return new ErrorResponse(message);
        }

        record ErrorResponse(String message) {}
    }
}
