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
# Project-local, not ~/.kube or ~/.minikube — see minikube-start.sh. ONE
# command to remember: minikube-up (bare -> fully running AND reachable:
# Cilium/Gateway/MetalLB, full observability stack, apps+Postgres — no
# data-generator load, just Flyway's own baked-in demo data — then port-
# forwards to the host so it ends with working localhost URLs). Blocks in
# the foreground once everything's up, same shape as `make start`'s `docker
# compose up` — Ctrl+C stops it, not a second command. The rest are the
# pieces it chains together, exposed separately so a failed step can be
# resumed without redoing everything. See k8s-cluster/manifests/overlays/minikube/README.md
# for what's deployed and why each override exists.

minikube-start:
	./k8s-cluster/scripts/minikube-start.sh

minikube-bootstrap:
	./k8s-cluster/scripts/minikube-bootstrap.sh

minikube-bootstrap-observability:
	./k8s-cluster/scripts/minikube-bootstrap-observability.sh

minikube-deploy:
	./k8s-cluster/scripts/minikube-deploy.sh

minikube-forward:
	./k8s-cluster/scripts/minikube-forward.sh

minikube-unforward:
	./k8s-cluster/scripts/minikube-unforward.sh

minikube-up: minikube-start minikube-bootstrap minikube-bootstrap-observability minikube-deploy minikube-forward

minikube-status:
	KUBECONFIG=$(shell pwd)/k8s-cluster/kubeconfig-minikube kubectl get pods -A

minikube-tunnel:
	KUBECONFIG=$(shell pwd)/k8s-cluster/kubeconfig-minikube MINIKUBE_HOME=$(shell pwd)/k8s-cluster/.minikube minikube tunnel

minikube-stop: minikube-unforward
	KUBECONFIG=$(shell pwd)/k8s-cluster/kubeconfig-minikube MINIKUBE_HOME=$(shell pwd)/k8s-cluster/.minikube minikube stop

minikube-delete: minikube-unforward
	KUBECONFIG=$(shell pwd)/k8s-cluster/kubeconfig-minikube MINIKUBE_HOME=$(shell pwd)/k8s-cluster/.minikube minikube delete
