package pl.lunasoftware.demo.microservices.joboffers.offer.api;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
