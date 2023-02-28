FROM openjdk:17.0-oraclelinux8
WORKDIR /app
COPY ./build/libs/company-*.jar ./company-app.jar
COPY ./docker/application.yml ./
CMD ["java", "-jar", "./company-app.jar"]
