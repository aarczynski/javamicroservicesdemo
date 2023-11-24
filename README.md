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
java -jar data-generator/build/libs/data-generator-0.0.1-SNAPSHOT.jar 100000
```
This generates SQL script that inserts 10 000 departments (fixed value), 100 000 employees (configurable), and generates random relations between them. Generated files are put in `data-generator` module in `output`. Import them into application database (check `docker-compose.yml` for credentials).

This also generates a JSON file with generated departments. It is used during load tests to generate requests.

These files may be extremely large (several GB), thus they are not tracked in Git.

## Running company app locally
Run following command:
```shell
./gradlew clean :company:build && docker-compose up --build
```

## Sample HTTP requests
Check `http/requests.http` file.

## Performance testing
Gatling gradle plugin is used. Run following command:
```shell
./gradlew clean :load-test:gatlingRun
```
