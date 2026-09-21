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

start: ensure-insecure-registry clean_build
	-TARGET_HOST=$(targetHost) CANDIDATES_DATA_FILE=$(candidatesDataFile) docker compose up --build

ensure-insecure-registry:
	./scripts/ensure-insecure-registry.sh

load-data:
	./scripts/load-data.sh

reload-data:
	./scripts/load-data.sh --force

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

k8s-deploy: ensure-insecure-registry
	./k8s-cluster/scripts/deploy.sh

# Rare: only needed when load-background's own source (src/candidate-search.js,
# entrypoint.sh, Dockerfile.k8s) changes, not on every app deploy.
k8s-deploy-load-background: ensure-insecure-registry
	./k8s-cluster/scripts/deploy-load-background.sh

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
# minikube-deploy-only / minikube-bootstrap / minikube-image / minikube-start
# are the pieces minikube-deploy/minikube-rebuild-all chain together, exposed
# separately so a failed step can be resumed without redoing everything. See
# k8s-cluster/manifests/overlays/minikube/README.md for what's deployed and
# why each override exists.

# Also re-establishes port-forwards as its last step (minikube-forward.sh
# safely skips any service not deployed yet) - on a cluster that was
# `minikube-stop`'d (not deleted) and already has everything on it, plain
# minikube-start is enough to get access back, no separate command needed.
minikube-start:
	./k8s-cluster/scripts/minikube-start.sh

# Builds app-candidates/app-job-offers/load-background and loads them
# straight into minikube's image cache (no registry — see the script).
# Chained into minikube-rebuild-all (a fresh cluster has nothing loaded yet).
# Otherwise rare to run standalone: only needed after changing app/
# load-background source, followed by `make minikube-deploy` to pick it up.
minikube-image:
	./k8s-cluster/scripts/minikube-image.sh

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

minikube-rebuild-all: minikube-start minikube-image minikube-bootstrap minikube-forward

# Real Gateway/MetalLB LoadBalancer IP on the host (proper load balancing
# across replicas, unlike minikube-forward's straight-to-one-pod
# port-forwards) - blocks the terminal, needs sudo. Deliberately NOT
# backgrounded/chained into minikube-start: `minikube tunnel` shells out to
# sudo separately for EACH privileged-port (80) service as it starts them,
# not once up front - wrapping the whole thing in one non-interactive
# `sudo -n` (tried 2026-09-21) leaves it stuck with no route ever added,
# since those inner sudo calls have no terminal to prompt on and no way to
# know the outer process is already root. Run this in its own terminal and
# leave it open, same as minikube itself recommends.
minikube-tunnel:
	KUBECONFIG=$(shell pwd)/k8s-cluster/kubeconfig-minikube MINIKUBE_HOME=$(shell pwd)/k8s-cluster/.minikube minikube tunnel

minikube-stop: minikube-unforward
	KUBECONFIG=$(shell pwd)/k8s-cluster/kubeconfig-minikube MINIKUBE_HOME=$(shell pwd)/k8s-cluster/.minikube minikube stop

minikube-delete: minikube-unforward
	KUBECONFIG=$(shell pwd)/k8s-cluster/kubeconfig-minikube MINIKUBE_HOME=$(shell pwd)/k8s-cluster/.minikube minikube delete
