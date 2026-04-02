package pl.lunasoftware.demo.microservices.candidates.joboffer;

import feign.Response;
import feign.codec.ErrorDecoder;
import pl.lunasoftware.demo.microservices.candidates.candidate.api.BadRequestException;
import pl.lunasoftware.demo.microservices.candidates.candidate.api.ResourceNotFoundException;

public class JobOffersErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 400 -> new BadRequestException("Job offers service returned bad request");
            case 404 -> new ResourceNotFoundException("Job offers service returned not found");
            default -> defaultDecoder.decode(methodKey, response);
        };
    }
}
