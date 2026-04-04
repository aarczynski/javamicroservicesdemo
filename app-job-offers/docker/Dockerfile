FROM eclipse-temurin:25-jdk-jammy
WORKDIR /app
COPY ./build/libs/app-job-offers-*.jar ./app-job-offers.jar
COPY ./build/otel-agent/opentelemetry-javaagent.jar ./
COPY ./docker/application.yml ./
CMD ["java", "-jar", "./app-job-offers.jar"]
