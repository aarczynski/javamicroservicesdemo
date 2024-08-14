FROM openjdk:21-jdk
WORKDIR /app
COPY ./build/libs/company-*.jar ./company-app.jar
COPY ./build/otel-agent/opentelemetry-javaagent.jar ./
COPY ./docker/application.yml ./
CMD ["java", "-jar", "./company-app.jar"]
