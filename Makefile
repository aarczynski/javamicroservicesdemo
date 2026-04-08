targetHost ?=
candidatesDataFile ?= $(shell pwd)/data-generator/output/candidates/01-candidates.sql

start: clean_build
	-docker compose up --build

ambient-load:
	-TARGET_HOST=$(targetHost) CANDIDATES_DATA_FILE=$(candidatesDataFile) docker compose --profile ambient up --build --force-recreate --no-deps load-background

start-ambient: clean_build
	-TARGET_HOST=$(targetHost) CANDIDATES_DATA_FILE=$(candidatesDataFile) docker compose --profile ambient up --build

clean_build:
	./gradlew clean :app-job-offers:build :app-candidates:build

candidateSimulation:
	-./gradlew :load-test:gatlingRun --simulation pl.lunasoftware.demo.microservices.loadtest.CandidateSimulation $(if $(targetHost),-DtargetHost=$(targetHost)) -DcandidatesDataFile=$(candidatesDataFile)
