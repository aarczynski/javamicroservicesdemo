targetHost ?=
candidatesDataFile ?= $(shell pwd)/data-generator/output/candidates/01-candidates.sql
maxRps ?=
stepDuration ?=
ramps ?=
candidates ?=
jobOffers ?=
companies ?=

generate-data:
	./gradlew clean :data-generator:build && java -jar data-generator/build/libs/data-generator-1.0.0.jar $(candidates) $(jobOffers) $(companies)

start: clean_build
	-TARGET_HOST=$(targetHost) CANDIDATES_DATA_FILE=$(candidatesDataFile) docker compose up --build

start-ambient: clean_build
	-TARGET_HOST=$(targetHost) CANDIDATES_DATA_FILE=$(candidatesDataFile) docker compose --profile ambient up --build

ambient-load:
	-TARGET_HOST=$(targetHost) CANDIDATES_DATA_FILE=$(candidatesDataFile) docker compose --profile ambient up --build --force-recreate --no-deps load-background

clean_build:
	./gradlew clean :app-job-offers:build :app-candidates:build

candidateSimulation:
	-./gradlew :load-test:gatlingRun --simulation pl.lunasoftware.demo.microservices.loadtest.CandidateSimulation $(if $(targetHost),-DtargetHost=$(targetHost)) -DcandidatesDataFile=$(candidatesDataFile) $(if $(maxRps),-DmaxRps=$(maxRps)) $(if $(stepDuration),-DstepDuration=$(stepDuration)) $(if $(ramps),-Dramps=$(ramps))

# --- Home k8s cluster (Raspberry Pi 5) ---
# Two commands to remember day-to-day: k8s-deploy (code/dashboard changes),
# k8s-rebuild-all (bare metal -> running cluster, e.g. after reformatting all
# disks). The rest are the pieces k8s-rebuild-all chains together, exposed
# separately so a failed step can be resumed without redoing everything.

ANSIBLE_INVENTORY = k8s-cluster/ansible/inventory.ini

k8s-prep:
	ansible-playbook -i $(ANSIBLE_INVENTORY) k8s-cluster/ansible/playbook.yml

k8s-init:
	ansible-playbook -i $(ANSIBLE_INVENTORY) k8s-cluster/ansible/cluster-init.yml

k8s-bootstrap:
	./k8s-cluster/scripts/bootstrap.sh

k8s-load-data:
	./k8s-cluster/scripts/load-data.sh

k8s-reload-data:
	./k8s-cluster/scripts/load-data.sh --force

k8s-rebuild-all: k8s-prep k8s-init k8s-bootstrap k8s-load-data

k8s-deploy:
	./k8s-cluster/scripts/deploy.sh
