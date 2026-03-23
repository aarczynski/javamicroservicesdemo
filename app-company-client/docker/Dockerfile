FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY ./build/libs/app-company-client-*.jar ./app-company-client.jar
COPY ./build/otel-agent/opentelemetry-javaagent.jar ./
COPY ./docker/application.yml ./
CMD ["java", "-jar", "./app-company-client.jar"]
