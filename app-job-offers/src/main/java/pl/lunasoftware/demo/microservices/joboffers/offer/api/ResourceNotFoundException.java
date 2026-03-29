package pl.lunasoftware.demo.microservices.joboffers.offer.api;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
