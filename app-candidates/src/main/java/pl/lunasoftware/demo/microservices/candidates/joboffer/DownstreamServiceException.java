package pl.lunasoftware.demo.microservices.candidates.joboffer;

import lombok.Getter;

@Getter
public class DownstreamServiceException extends RuntimeException {

    private final String serviceName;

    public DownstreamServiceException(String serviceName, Throwable cause) {
        super(cause);
        this.serviceName = serviceName;
    }

}
