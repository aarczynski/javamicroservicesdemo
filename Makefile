host ?= http://localhost:8081
targetHost ?= http://app-candidates:8080
candidatesDataFile ?= $(shell pwd)/data-generator/output/candidates/01-candidates.sql

start: clean_build compose_up

start-ambient: clean_build compose_up_ambient

clean_build:
	./gradlew clean :app-job-offers:build :app-candidates:build

compose_up:
	docker compose up --build

compose_up_ambient:
	TARGET_HOST=$(targetHost) docker compose --profile ambient up --build

candidateSimulation:
	./gradlew :load-test:gatlingRun --simulation pl.lunasoftware.demo.microservices.loadtest.CandidateSimulation -Dhost=$(host) -DdataFile=$(candidatesDataFile)
