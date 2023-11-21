# Work In Progress
App to analyze distributed microservices app and DB performance using Gatling and OpenTelemetry.
## Modules:
* **company** - a simple CRUD microservice. Data model consists of two tables: department and employee. They are in many-to-many relationship (an employy can work in multiple departments, while a department may have multiple employees).
* **data-generator** - an utility tool to generate SQL file(s) to populate test database with significant amount of data.
# Running locally
Requires JDK17+, Docker, and Docker Compose installed on your machine.
## Generating test data
Run following commands:
```shell
./gradlew clean :data-generator:build
java -jar data-generator/build/libs/data-generator-0.0.1-SNAPSHOT.jar 2000 200000
```
This generates SQL script that inserts 2 000 departments, 200 000 employees, and generates random relations between them. Generated files are put in `company` module `resources/db/migration/postgres` folder. It will be loaded by Flyway during application startup.

This file may be extremely large (several GB) leading to very long artifact building and application startup times. You may prefer to import this file into DB manually.

It is possible to change data size, changing above params. It is recommended to use multiples of 1 000. Otherwise, generated amount data may be slightly inaccurate. When no params or wrong params are provided, default values will be used: 1 000 departments, 10 000 employees.

Generated SQL scripts are not tracked by Git.

## Running company app locally
Run following command:
```shell
./gradlew clean build && docker-compose up --build
```

## Sample HTTP requests
Check `http/requests.http` file.

## Performance testing
Gatling gradle plugin is used. Run following command:
```shell
./gradlew clean :load-test:gatlingRun
```
