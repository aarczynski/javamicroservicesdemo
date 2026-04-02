package pl.lunasoftware.demo.microservices.candidates.candidate.api;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
