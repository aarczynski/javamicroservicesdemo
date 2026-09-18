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

# --- minikube (local dev cluster, no RPi hardware needed) ---
# Project-local, not ~/.kube or ~/.minikube — see minikube-start.sh. Same
# shape as the RPi cluster above: two commands to remember day-to-day —
# minikube-deploy (code/manifest changes to the apps) and minikube-rebuild-all
# (bare -> fully running: Cilium/Gateway/MetalLB, full observability stack,
# apps+Postgres+load-background — no data-generator load, just Flyway's own
# baked-in demo data). BOTH end by port-forwarding to the host IN THE
# BACKGROUND (via minikube-forward, which each chains as its last step) and
# return immediately — the terminal stays free. `make minikube-stop`/
# `minikube-delete` clean the forwards up (minikube-unforward, chained as
# their first step); re-running minikube-forward restarts them.
# minikube-deploy-only / minikube-bootstrap / minikube-start are the pieces
# minikube-deploy/minikube-rebuild-all chain together, exposed separately so
# a failed step can be resumed without redoing everything. See
# k8s-cluster/manifests/overlays/minikube/README.md for what's deployed and
# why each override exists.

minikube-start:
	./k8s-cluster/scripts/minikube-start.sh

minikube-bootstrap:
	./k8s-cluster/scripts/minikube-bootstrap.sh

minikube-deploy: minikube-deploy-only minikube-forward

minikube-deploy-only:
	./k8s-cluster/scripts/minikube-deploy.sh

minikube-load-data:
	./k8s-cluster/scripts/minikube-load-data.sh

minikube-reload-data:
	./k8s-cluster/scripts/minikube-load-data.sh --force

minikube-forward:
	./k8s-cluster/scripts/minikube-forward.sh

minikube-unforward:
	./k8s-cluster/scripts/minikube-unforward.sh

minikube-rebuild-all: minikube-start minikube-bootstrap minikube-forward

minikube-tunnel:
	KUBECONFIG=$(shell pwd)/k8s-cluster/kubeconfig-minikube MINIKUBE_HOME=$(shell pwd)/k8s-cluster/.minikube minikube tunnel

minikube-stop: minikube-unforward
	KUBECONFIG=$(shell pwd)/k8s-cluster/kubeconfig-minikube MINIKUBE_HOME=$(shell pwd)/k8s-cluster/.minikube minikube stop

minikube-delete: minikube-unforward
	KUBECONFIG=$(shell pwd)/k8s-cluster/kubeconfig-minikube MINIKUBE_HOME=$(shell pwd)/k8s-cluster/.minikube minikube delete
