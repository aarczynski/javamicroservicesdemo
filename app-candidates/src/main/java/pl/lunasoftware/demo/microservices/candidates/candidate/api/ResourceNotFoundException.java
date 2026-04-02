package pl.lunasoftware.demo.microservices.candidates.candidate.api;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
