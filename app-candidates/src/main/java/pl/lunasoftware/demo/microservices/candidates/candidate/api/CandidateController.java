package pl.lunasoftware.demo.microservices.candidates.candidate.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.lunasoftware.demo.microservices.candidates.candidate.CandidateService;
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOfferMatchDto;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/candidates")
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping("/{id}/matching-offers")
    public List<JobOfferMatchDto> matchingOffers(@PathVariable UUID id) {
        log.info("Received matching offers request, candidateId={}", id);
        return candidateService.findMatchingOffers(id);
    }

    @Slf4j
    @RestControllerAdvice(assignableTypes = CandidateController.class)
    static class CandidateExceptionHandler {

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

        @ExceptionHandler(Exception.class)
        @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        ErrorResponse handleUnexpected(Exception ex) {
            log.error("Unexpected error", ex);
            return new ErrorResponse("Internal server error");
        }

        record ErrorResponse(String message) {}
    }
}
