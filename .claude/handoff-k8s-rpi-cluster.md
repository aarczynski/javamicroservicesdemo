# Handoff: klaster k8s na RPi5 — stan na 2026-08-26

Cel: domowy klaster Kubernetes na Raspberry Pi 5 (8GB, NVMe), docelowo do wdrożenia `javamicroservicesdemo` (candidates, job-offers, ich bazy, observability).

**Gotowe:** 6 node'ów `Ready` pod finalnymi nazwami, MetalLB, `local-path-provisioner`, cały stack observability (Prometheus/Loki/Tempo/otel-collector/Grafana) działający end-to-end. Repo (`k8s-cluster/`, ten handoff) skomitowane na branchu `k8s-observability-stack`, jeszcze niezmergowane.

**Następny krok:** deploy `candidates`/`job-offers` na klaster (patrz "Następny krok" niżej).

## Stan fizyczny — aktualne node'y

| Nazwa | IP | Rola / taint | Status |
|---|---|---|---|
| `k8s-rpi-master` | `192.168.10.1` | `control-plane`, taint `node-role.kubernetes.io/control-plane:NoSchedule` | `Ready` |
| `k8s-rpi-db-1` | `192.168.10.50` | taint `role=database:NoSchedule` | `Ready` |
| `k8s-rpi-db-2` | `192.168.10.51` | taint `role=database:NoSchedule` | `Ready` |
| `k8s-rpi-observability-1` | `192.168.10.90` | taint `role=observability:NoSchedule` — cały stack observability tu działa | `Ready` |
| `k8s-rpi-worker-1` | `192.168.10.10` | brak tainta, generic worker (hostuje Hubble Relay/UI) | `Ready` |
| `k8s-rpi-worker-2` | `192.168.10.11` | brak tainta, generic worker | `Ready` |

Wszystkie: Ubuntu 24.04.4 LTS aarch64, `kubelet`/`kubeadm` `v1.36.4`, SSH user `aarczynski`, passwordless sudo skonfigurowane, boot z NVMe.

Node'y bazodanowe i observability używają nazw generycznych, nie per-serwis — patrz "Kluczowe decyzje architektoniczne". Nazwy/IP wyżej to **wynik dwóch rund rename** przeprowadzonych 2026-08-26 (pierwotne nazwy typu `k8s-rpi-joboffers-db`/`.200`, potem shift na `db-0`/`.200`→`db-1`/`.50` itd., żeby zwolnić `192.168.10.100-199` pod pulę MetalLB) — historia rename'u nieistotna dla dalszej pracy, tabela wyżej to stan końcowy.

## Kluczowe decyzje architektoniczne

- **kubeadm**, nie k3s — świadomy wybór, cel to nauka pełnego stosu (CNI/storage/ingress/LB dobierane ręcznie), nie gotowe defaulty.
- **Kubernetes v1.36**.
- **CNI: Cilium** (z Hubble) — wybrany zamiast Calico ze względu na synergię z resztą stacku observability.
- **Storage: `local-path-provisioner`** — świadomie zaakceptowany SPOF na tym etapie (PV trzymany lokalnie na dysku node'a).
- **IP: statyczne przez rezerwację DHCP w kontrolerze Omada.** Gateway/switch VLAN-u: `192.168.10.254`. DHCP dynamiczny zawężony do `192.168.10.200-253`; `1-99` to rezerwacje node'ów; `100-199` to pula MetalLB.
- **Node'y bazodanowe i observability nazwane generycznie** (`db-N`, nie per-serwis) — cel: wymienne node'y mogące hostować dowolną bazę (Postgres i/lub NoSQL, także kilka na jednym node'zie — taint `role=database` jest silnik-agnostyczny). Numeracja node'ów **0-indexed** dla workerów, ale bazy skończyły na 1-indexed (`db-1`/`db-2`) po drugiej rundzie rename — niespójność zaakceptowana, nieistotna funkcjonalnie. `master` i `observability-1` bez numeracji/z numeracją inną niż reszta — świadome odstępstwa.
- **Liczba RPi: użytkownik ma fizycznie 12 sztuk**, nie 8 jak zakładał pierwotny plan (1 master + 2 bazy + 1 observability + 4 workery). 4 obecnie w użyciu (`master`, `db-1`, `db-2`, `observability-1`) + 2 workery = 6. **Otwarta decyzja co do pozostałych 6**: albo więcej workerów, albo HA control-plane (3 mastery — "przyszłe ćwiczenie").
- **Robocza preferencja użytkownika:** przy pracach na klastrze (SSH/ansible/kubeadm/kubectl na node'ach) użytkownik chce sam wpisywać komendy — Claude podaje komendę, czeka na wynik, nie wykonuje sam przez Bash. Nie dotyczy reszty repo (Gradle/testy). **Obserwowane wyjątki** (zawsze jawny, ad-hoc sygnał typu "ty zrób", nie trwała zmiana): dołączanie nowych node'ów i rename całej floty node'ów 2026-08-26 — Claude wykonał SSH/ansible/kubeadm/kubectl bezpośrednio.
- **Auto-mode classifier bywa zawodny przy `kubeadm reset`/`kubectl delete node` przez SSH** — blokuje mimo jawnej zgody użytkownika (hard-deny na destrukcyjne komendy), nawet z istniejącą regułą `autoMode.allow` w `.claude/settings.local.json` (plik lokalny, niecommitowany — patrz `.gitignore`). Jeśli się powtórzy: reguła już tam jest, powinna przepuszczać automatycznie; jeśli nie zadziała, ostatecznym wyjściem było odpalenie sesji w "yolo mode" (bypass permission prompts).

## MetalLB — ukończone (2026-08-26)

**Instalacja:** `helm repo add metallb https://metallb.github.io/metallb`, namespace `metallb-system`, L2 mode (`--set frrk8s.enabled=false` — Omada nie mówi BGP, `frr-k8s` tylko marnowałby zasoby na RPi5).

**Pula IP: `192.168.10.100-192.168.10.199`**.

**Problem napotkany po pierwszym `helm install`:** tylko 3 z 6 speakerów wstało (master + 2 workery) — brak na tainted node'ach (`db-1`/`db-2`/`observability-1`). Przyczyna: szablon `speaker` DaemonSet w chart MetalLB ma **zaszyte na sztywno** tolerations tylko dla `node-role.kubernetes.io/master`/`control-plane` — nic dla własnych taintów `role=database`/`role=observability`. Klucz `speaker.tolerations` w values **dokleja się** do zaszytych wpisów (nie zastępuje ich) — zweryfikowane przez `helm template` przed realnym `upgrade`.

**Rozwiązanie — `helm upgrade` z dodatkowymi tolerations:**
```bash
helm upgrade metallb metallb/metallb -n metallb-system \
  --set frrk8s.enabled=false \
  --set "speaker.tolerations[0].key=role" \
  --set "speaker.tolerations[0].operator=Equal" \
  --set "speaker.tolerations[0].value=database" \
  --set "speaker.tolerations[0].effect=NoSchedule" \
  --set "speaker.tolerations[1].key=role" \
  --set "speaker.tolerations[1].operator=Equal" \
  --set "speaker.tolerations[1].value=observability" \
  --set "speaker.tolerations[1].effect=NoSchedule"
```
Uwaga zsh: nawiasy kwadratowe w `--set` trzeba cudzysłowować, inaczej zsh interpretuje je jako glob (`no matches found`).

Po upgrade: 6/6 speakerów `Running` (po jednym na każdym node'zie, w tym tainted).

**CR-y** (`k8s-cluster/manifests/metallb/`): `ip-address-pool.yaml` (`IPAddressPool` `default-pool`), `l2-advertisement.yaml` (`L2Advertisement` `default-l2`, referencja przez `spec.ipAddressPools`, nie `metadata.name`). Instalacja Helm i CR-y **nie są zarządzane przez Ansible** — czysto `helm`/`kubectl` z Maca (brak GitOps na tym etapie, patrz plan pkt 15).

## Observability stack — ukończone (2026-08-26)

Prometheus + Loki + Tempo + otel-collector + Grafana wdrożone na `k8s-rpi-observability-1` przez Helm, zwierciadlące configi z `compose.yml`/`observability/*`. Wszystkie 5 Podów `Running`/`Ready`, wszystkie 3 datasource'y w Grafanie zdrowe (`/api/datasources/uid/<uid>/health` → `OK`), 3 dashboardy (HTTP Monitoring, JVM Monitoring, Logs) załadowane przez sidecar. Grafana dostępna pod `http://192.168.10.100` (pierwszy adres z puli MetalLB), anonymous auth jak w compose (`admin`, bez logowania).

**Storage:** `local-path-provisioner` v0.0.37 zainstalowany jako pierwszy krok (`kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/v0.0.37/deploy/local-path-storage.yaml`) — na klastrze wcześniej nie było żadnego `StorageClass`. Tworzy `StorageClass` o nazwie `local-path` (`WaitForFirstConsumer`, `reclaimPolicy: Delete`), używaną przez wszystkie PVC-e stacku.

**Pliki w repo** (`k8s-cluster/manifests/observability/`): `namespace.yaml`, `values-prometheus.yaml`, `values-loki.yaml`, `values-tempo.yaml`, `values-otel-collector.yaml`, `values-grafana.yaml`, `dashboards-configmap.yaml` (wygenerowany z `observability/grafana/provisioning/dashboards/*.json`, labelowany `grafana_dashboard: "1"`). Każdy komponent przypięty przez `nodeSelector: kubernetes.io/hostname: k8s-rpi-observability-1` + tolerację na taint `role=observability:NoSchedule`.

**Wersje chartów i obrazów (świadomie zaakceptowany dryf vs `compose.yml`, patrz niżej):**
| Komponent | Chart (repo) | Chart version | App version | `compose.yml` pin |
|---|---|---|---|---|
| Prometheus | `prometheus-community/prometheus` | 29.27.0 | v3.14.0 | v3.14.0 (zgodne) |
| Loki | `grafana-community/loki` | 18.11.3 | 3.7.6 | 3.7.6 (zgodne) |
| Tempo | `grafana-community/tempo` | 2.3.0 | 2.10.8 | 3.0.3 (**dryf**, patrz niżej) |
| Grafana | `grafana-community/grafana` | 12.11.2 | 13.2.0 (nadpisane na `13.1.4` przez `image.tag` w values) | 13.1.4 (zgodne, wymuszone) |
| otel-collector | `open-telemetry/opentelemetry-collector` | 0.171.0 | obraz nadpisany na `otel/opentelemetry-collector-contrib:0.159.0` przez `image.repository`/`image.tag` | 0.159.0 (zgodne, wymuszone) |

**Decyzja o dryfie Tempo (2.10.8 vs 3.0.3) — TYMCZASOWA, do usunięcia (patrz plan pkt 14 niżej).** Zaakceptowana krótkoterminowo, bo `2.10.8` po prostu działa. **Przetestowane empirycznie** — override `tempo.tag: 3.0.3` na chart `grafana-community/tempo` (single-binary) **realnie się wywala**: `failed parsing config: ... field compactor not found in type app.Config`, `field ingester not found in type app.Config`. To nie brak paru nowych opcjonalnych sekcji (`backend_scheduler`/`backend_worker`) — Tempo 3.x ma **przebudowany od zera schemat configu** (`app.Config`), którego chart single-binary w ogóle nie generuje i nigdy nie będzie (patrz pułapka 4 niżej). Użytkownik ocenił ten rozjazd jako nieakceptowalny na dłużej ("do bani ten rozjazd wersji") — **plan: migracja na `tempo-distributed` PRZED wdrożeniem GitOps/IaC** (pkt 14), żeby nie kodyfikować w GitOps configu, który i tak trzeba będzie przepisać.

**Uwaga o statusie "deprecated" dla Tempo:** stary chart `grafana/tempo` (zamrożone repo, patrz pułapka 1 niżej) miał `deprecated: true` we własnych metadanych. **Obecnie zainstalowany `grafana-community/tempo` (2.3.0) tej flagi NIE ma** — zweryfikowane. Jedyny pozostały "smrodek" to sam typ chartu — "Single Binary Mode" to architektura, którą Grafana Labs traktuje jako drugorzędną wobec `tempo-distributed` (mikroserwisy), ale to nie jest formalny deprecation.

**Cztery pułapki napotkane po drodze (ważne dla przyszłych zmian w tym stacku):**

1. **Repo `https://grafana.github.io/helm-charts` jest ZAMROŻONE** — całe zmigrowane do `grafana-community/helm-charts` (cutover 2026-01-30). Instalacja Grafany/Loki/Tempo ze starego repo dawała `level=WARN msg="this chart is deprecated"` i realnie przestarzałe wersje (np. Loki 3.6.12 zamiast realnego najnowszego 3.7.6). **Zawsze używać `grafana-community/*`, nie `grafana/*`**, dla tych trzech chartów. `prometheus-community/*` i `open-telemetry/*` nie są tym dotknięte. Sprawdzać przez `helm show chart <repo>/<chart> --version X | grep deprecated` przed instalacją, jeśli w wątpliwość.
2. **otel-collector: `health_check` extension domyślnie binduje tylko na `localhost:13133`**, nie `0.0.0.0`. W Compose to działało (własny `/http-probe` łączył się z `localhost` w tym samym kontenerze), ale w k8s kubelet łączy się z **IP Poda** — `connection refused`, restart loop. Poprawka: jawnie `endpoint: 0.0.0.0:13133` w `extensions.health_check` (patrz `values-otel-collector.yaml`).
3. **`prometheus-community/prometheus` chart wystawia Service `prometheus-server` na porcie 80**, nie 9090 (podobnie jak Grafana na 80, nie 3000 wewnętrznie) — 9090 to tylko `targetPort` kontenera. Datasource Prometheusa w Grafanie i exporter `prometheusremotewrite` w otel-collectorze muszą celować w `:80`.
4. **Grafana Labs aktualizuje szablon configu chartu `tempo` (single-binary) wolniej niż `tempo-distributed`** — dowód: w tym samym repo `tempo-distributed` jest już na appVersion `3.0.3`, a `tempo` (single-binary) utknął na `2.10.8`. Realna przyczyna: Tempo 3.x ma przebudowany schemat configu (`app.Config`), a chart single-binary nigdy nie został do tego dopisany — override `tempo.tag` na nowszy obraz **zawsze się wywali**. Jedyna droga do Tempo 3.x na tym stacku: przejście na `tempo-distributed` (patrz plan pkt 14).

## Hubble Relay/UI

Działa na generic workerach (`k8s-rpi-worker-1`/`-2`), scheduler sam omija tainted node'y. Decyzja użytkownika: zostaje w klastrze mimo niepewności co do realnego użycia — było częścią uzasadnienia wyboru Cilium zamiast Calico. Do ponownej oceny, jeśli faktycznie nigdy nie będzie używane do debugowania sieci.

## Ansible

Katalog: `k8s-cluster/ansible/` w repo (nie wchodzi w Gradle build, skomitowany na branchu `k8s-observability-stack`).

```
ansible.cfg
inventory.ini          — grupy: k8s_master, k8s_database, k8s_observability, k8s_workers
group_vars/all.yml      — kubernetes_version: "1.36"
playbook.yml            — hosts: k8s_all (całe inventory, bez --limit leci na wszystkich)
roles/node_prep/tasks/main.yml
roles/node_prep/handlers/main.yml
```

`inventory.ini` zgodny z aktualnym stanem klastra (tabela node'ów wyżej) — zakomentowane wpisy `k8s-rpi-worker-3`/`worker-4` czekają na dołączenie kolejnych fizycznych Pi.

Uruchomienie (z Maca, z katalogu `k8s-cluster/ansible/`):
```bash
ansible -i inventory.ini k8s_all -m ping
ansible-playbook -i inventory.ini playbook.yml
# albo z ograniczeniem do jednego/kilku node'ów:
ansible-playbook -i inventory.ini playbook.yml --limit k8s-rpi-worker-1,k8s-rpi-worker-2
```
Passwordless sudo już skonfigurowane na wszystkich node'ach — `--ask-become-pass` niepotrzebne. SSH bez hasła: klucz już skonfigurowany; nowy host trzeba zweryfikować interaktywnie pierwszym `ssh` (host key verification) — użytkownik robi to ręcznie, nie automatyzować przez `ssh-keyscan`.

`kubeadm init`/`join` pozostaje ręczny (tokeny tymczasowe, 24h) — Ansible robi tylko node prep (hostname, kubelet/kubeadm/containerd), nie sam bootstrap klastra.

## Następny krok (do zrobienia w kolejnej sesji)

Gotowe: node'y, MetalLB, storage, cały stack observability (patrz sekcje wyżej).

Jeszcze nie zrobione, w orientacyjnej kolejności:
1. `k8s-rpi-worker-3`/`worker-4` (dopełnienie pierwotnego planu 4 workerów, `inventory.ini` już ma zakomentowane wpisy) + decyzja co do pozostałych RPi z 12 fizycznie posiadanych (patrz "Kluczowe decyzje architektoniczne").
2. Ingress controller.
3. Deploy baz Postgres na `k8s-rpi-db-1`/`k8s-rpi-db-2`.
4. Deploy `candidates`/`job-offers` — w tym wskazanie ich na już działający otel-collector: `otel-collector-opentelemetry-collector.observability.svc.cluster.local:4317`.
5. Kubernetes Dashboard na `k8s-rpi-observability-1` (patrz plan pkt 12 niżej).
6. **Budowanie i wgrywanie obrazów Javy — do ustalenia, nie pilne.** `docker build` nadal działa (containerd tylko *uruchamia* obrazy, nie wpływa na budowanie) — Dockerfile'e już istnieją (`app-candidates/docker/Dockerfile`, `app-job-offers/docker/Dockerfile`). Mac jest arm64, ta sama architektura co RPi5 — bez cross-buildu. Brakujący element: registry, z którego RPi pobiorą obraz (Docker Hub prywatne / `ghcr.io` / własny `registry:2` w klastrze). Alternatywa dla `docker build`: **Jib** (plugin Gradle, buduje obraz Javy bez Dockera/demona, bezpośredni push).

**Helm — decyzja potwierdzona w praktyce (2026-08-26):** dla infrastruktury (MetalLB, obserwowalność, docelowo ingress-nginx) używać Helm charts — przed `helm install` warto raz zobaczyć wyrenderowany manifest (`helm template`), żeby nie było to czarną skrzynką. Dla własnych apek (`candidates`/`job-offers`) na razie zostać przy plain YAML. **Ważna lekcja:** zawsze sprawdzać `deprecated: true` w metadanych chartu przed instalacją (`helm show chart <repo>/<chart> --version X`), bo repo mogło się zmigrować gdzie indziej (patrz pułapka 1 wyżej).

## Plany na koniec (długoterminowe, nie priorytetowe teraz)

Ustalone 2026-08-26 podczas dyskusji architektonicznej — do realizacji po ogarnięciu podstawowej infrastruktury (ingress, deploy baz i appek):

10. **Keycloak (SSO)** — decyzja: na zwykłym workerze, **bez** dedykowanego taintu/node'a. Uzasadnienie: Keycloak jest relatywnie lekki i replikowalny (2+ pody), w przeciwieństwie do Postgresa (I/O-wrażliwy, zaakceptowany SPOF) czy stosu observability (ciężki, wielokomponentowy) — nie potrzebuje izolacji, a budżet node'ów jest ciasny. Do rozważenia ponownie tylko jeśli zależy na przewidywalności/izolacji od hałaśliwych sąsiadów (wtedy: taint `role=identity`). Baza Postgresa dla Keycloaka może wylądować na jednym z istniejących node'ów `role=database`, bez tworzenia kolejnego.
11. **HA/failover baz między `k8s-rpi-db-1` i `k8s-rpi-db-2`** — cel: żeby padnięcie jednego z dwóch node'ów bazodanowych nie wywalało całego systemu. Kluczowy insight: samo dopuszczenie schedulowania poda na oba node'y **nic nie da** — `local-path-provisioner` trzyma dane lokalnie na dysku node'a, więc utrata node'a = utrata PV, bez względu na taint/affinity. Wymagana jest replikacja na poziomie Postgresa: operator **CloudNativePG** (lub Patroni) stawiający primary + standby rozpięte na obu node'ach z taintem `role=database`, ze streaming replication i automatyczną promocją standby przy awarii primary. Każdy serwis (`candidates`, `job-offers`) dostałby własny, osobny klaster Postgresa rozpięty na obu node'ach — nie jeden współdzielony.
12. **Kubernetes Dashboard** — użytkownik chce oficjalny Kubernetes Dashboard (nie Headlamp, mimo że to była rekomendacja Claude'a). **Decyzja: postawić na node'zie `k8s-rpi-observability-1`**, nie na generic workerze. Techniczna konsekwencja: node ma taint `role=observability:NoSchedule`, więc Deployment Dashboardu będzie potrzebował odpowiedniej `toleration` (+ ewentualnie `nodeSelector`/`nodeAffinity`).
13. **Logi w k8s: przejście na wzorzec node-file + DaemonSet (Promtail/Alloy), jako OSTATNI krok planu**, **po** Kubernetes Dashboard. Ważne: **Docker Compose zostaje bez zmian** — to osobna, równoległa ścieżka logowania wyłącznie dla k8s, nie migracja obecnego setupu. Kontekst: obecny setup (Compose) to NIE bezpośredni push appki do Loki — leci przez OTel Java agent → OTLP gRPC → `otel-collector` → eksport `otlp_http` do natywnego endpointu Loki (`observability/otel-collector/otel-collector.yml`). Zakres zmian tylko dla k8s:
    - Nowy DaemonSet Promtail/Alloy (manifest + configmap + RBAC) — jeden pod na każdym node'zie, w tym tainted (jak MetalLB speaker — infrastruktura, nie workload do izolacji).
    - W k8s: wyłączyć eksport logów przez OTel javaagent (`OTEL_LOGS_EXPORTER=none`) — traces/metrics OTLP bez zmian, logi przestają iść tą drogą.
    - Logback dalej pisze na stdout z `trace_id`/`span_id` w MDC (agent już to wstrzykuje) — Alloy/Promtail wyciąga je jako Loki labels przez pipeline stage (regex/JSON), potrzebne do zachowania linku Loki→Tempo w Grafanie (`derivedFields`, patrz CLAUDE.md).
    - `otel-collector` w k8s traci pipeline `logs` (traces+metrics zostają) — w Compose zero zmian.
    - Świadomy tradeoff: dwie różne ścieżki logowania między środowiskami (Compose: OTLP przez agenta; k8s: stdout+DaemonSet) — trzeba pilnować spójności labels (zwłaszcza `trace_id`) w obu.
14. **Migracja Tempo z single-binary (`grafana-community/tempo`) na `tempo-distributed` — MUSI być zrobiona PRZED GitOps (pkt 15).** Reakcja na frustrację użytkownika rozjazdem wersji ("do bani ten rozjazd wersji") po testach opisanych w sekcji "Observability stack — ukończone" (dryf Tempo 2.10.8 vs 3.0.3 — patrz tam pełne uzasadnienie i pułapka 4). Powód kolejności: nie ma sensu kodyfikować w GitOps (ArgoCD/Flux) configu dla single-binary Tempo, skoro i tak trzeba go wkrótce przepisać pod `tempo-distributed` — podwójna robota. Zakres zmian, do rozpisania w osobnej sesji:
    - `tempo-distributed` to architektura mikroserwisowa (distributor, ingester, compactor, querier, query-frontend jako osobne komponenty) zamiast jednego Poda — realnie więcej Podów/RAM/CPU na jedynym node'zie `k8s-rpi-observability-1` (8GB RAM, dzielone też z Prometheus/Loki/Grafana/otel-collector/local-path-provisioner). Do zweryfikowania, czy się zmieści bez tuningu resource requests/limits w dół.
    - `values-tempo.yaml` do przepisania od zera pod schemat `tempo-distributed` (inny kształt values niż obecny single-binary chart) — nie prosty bump wersji.
    - Storage: `tempo-distributed` też wspiera `backend: local` (filesystem) bez wymuszania obiektowego storage (S3/GCS/MinIO) — do potwierdzenia przy pisaniu values, żeby nie wciągnąć niepotrzebnie MinIO na klaster.
    - Po migracji: usunąć z handoffu i z `values-tempo.yaml` cały kontekst o dryfie wersji (ta sekcja + komentarze w pliku) — stanie się nieaktualny.
15. **GitOps — cel na koniec planu.** Użytkownik chce, żeby stan klastra finalnie był w pełni odtwarzalny z repo, bez ręcznego `helm install`/`kubectl apply` z pamięci — szczególnie na wypadek przeformatowania RPi. Kandydaci: **ArgoCD** lub **Flux**, pilnujące zgodności klastra z `k8s-cluster/manifests/`. Kontekst decyzji: obecny stan to częściowy IaC — Ansible robi node prep, ale `kubeadm init`/`join` jest ręczny i instalacje Helm były robione ad-hoc przez `--set`/`-f values.yaml` z linii komend. **Kolejność w planie: GitOps na końcu**, po ogarnięciu ingressu, deployu baz i appek — nie teraz, żeby nie wprowadzać dodatkowego narzędzia przedwcześnie. Do rozważenia przy realizacji: czy przy okazji też zautomatyzować `kubeadm init`/`join` w Ansible (osobna decyzja, nie blokuje GitOps dla warstwy aplikacyjnej/CR-ów).
