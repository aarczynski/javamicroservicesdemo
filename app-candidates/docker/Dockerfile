FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY ./build/libs/app-candidates-*.jar ./app-candidates.jar
COPY ./build/otel-agent/opentelemetry-javaagent.jar ./
COPY ./docker/application.yml ./
CMD ["java", "-jar", "./app-candidates.jar"]
