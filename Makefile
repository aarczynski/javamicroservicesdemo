host ?= http://localhost:8081
candidatesDataFile ?= ./data-generator/output/candidates/01-candidates.sql

start: clean_build compose_up

clean_build:
	./gradlew clean :app-job-offers:build :app-candidates:build

compose_up:
	docker compose up --build

candidateSimulation:
	./gradlew :load-test:gatlingRun --simulation pl.lunasoftware.demo.microservices.loadtest.CandidateSimulation -Dhost=$(host) -DdataFile=$(candidatesDataFile)
