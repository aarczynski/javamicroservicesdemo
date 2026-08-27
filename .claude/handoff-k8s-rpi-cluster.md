# Handoff: klaster k8s na RPi5 — stan na 2026-08-27

Cel: domowy klaster Kubernetes na Raspberry Pi 5 (8GB, NVMe), docelowo do wdrożenia `javamicroservicesdemo` (candidates, job-offers, ich bazy, observability).

**Gotowe:** 6 node'ów `Ready` pod finalnymi nazwami, MetalLB, `local-path-provisioner`, cały stack observability (Prometheus/Loki/Tempo/otel-collector/Grafana) działający end-to-end, oba Postgresy (`candidates`, `job-offers`) wdrożone i załadowane danymi z `data-generator`, oba mikroserwisy (`app-candidates`, `app-job-offers`) wdrożone i zweryfikowane end-to-end, ambient load (`load-background`, k6) działający w klastrze jako osobny pod (przypięty na `observability-1`), **ingress przez Cilium Gateway API działający end-to-end** (klaster zmigrowany z kube-proxy na `kubeProxyReplacement`, `.100` jako publiczne wejście API przez port 80), `kube-state-metrics`/`node-exporter` + dashboard "Kubernetes Cluster" w Grafanie, Headlamp jako UI do przeglądu klastra (`.198`, auto cluster-admin). Repo (`k8s-cluster/`, ten handoff) skomitowane na branchu `k8s-observability-stack`, jeszcze niezmergowane.

**Następny krok:** worker-3/worker-4, weryfikacja telemetrii appek w Grafanie, zastosowanie i weryfikacja fixów z 2026-08-28 (Tempo `GOMEMLIMIT`, DNS `preferIPv4Stack` — patrz "Następny krok" niżej).

## app-candidates / app-job-offers — ukończone (2026-08-27)

Wdrożone bezpośrednio przez Claude (jawne "ty robisz"), build+push obrazów z Maca; `docker login`/token obsługiwał użytkownik w osobnym terminalu (token nigdy nie przechodził przez sesję Claude).

**Rejestr obrazów: ghcr.io, publiczne pakiety** (nie self-hosted `registry:2` w klastrze — odrzucone jako "szkoda node'a", choć rejestr w klastrze i tak nie wymaga dedykowanego node'a, tylko lekkiego poda na generic workerze; nie zdążyło dojść do realizacji, bo user wybrał ghcr). `ghcr.io/aarczynski/app-candidates:v1`, `ghcr.io/aarczynski/app-job-offers:v1`. **Uwaga (pułapka, zajęła najwięcej czasu przy tym kroku):** świeżo wypchnięty pakiet w ghcr jest domyślnie **prywatny**, nawet z publicznego repo — trzeba ręcznie zmienić w GitHub → profil → Packages → pakiet → Package settings → Danger Zone → Change visibility → Public, dla każdego pakietu osobno. `docker manifest inspect` z Maca **mylnie pokazywał sukces jako "publiczny"**, bo używał zapisanych credentiali z `~/.docker/config.json` (ten sam plik co `docker login`) — nie był to realny anonimowy request. Realna weryfikacja anonimowej widoczności: request `curl` bezpośrednio po token z `https://ghcr.io/token?scope=repository:<user>/<img>:pull&service=ghcr.io`, potem `GET /v2/<user>/<img>/manifests/<tag>` z tym tokenem, bez użycia `docker` CLI.

**Build obrazów:** `./gradlew clean :app-job-offers:build :app-candidates:build` (jak `make clean_build`) produkuje jary + otel-agent, potem zwykły `docker build -f <service>/docker/Dockerfile <service>` — Mac arm64 = RPi5 arm64, bez cross-buildu. Tag wersjonowany (`v1`), nie `latest` — świadoma decyzja: deterministyczny stan w manifeście, rollback = zmiana tagu + `kubectl apply`, zamiast `:latest` + `rollout restart`.

**Manifesty** (`k8s-cluster/manifests/candidates/app.yaml`, `k8s-cluster/manifests/job-offers/app.yaml`) — plain YAML `Deployment` (1 replika) + `Service`, bez `nodeSelector`/tolerations (appki lądują naturalnie na generic workerach, `worker-1`/`worker-2`, bo tylko baza/observability mają taints):
- `command` nadpisuje domyślny `CMD` z Dockerfile'a, dopisując `-javaagent:./opentelemetry-javaagent.jar` (Dockerfile sam z siebie nie uruchamia agenta OTel — to robił dopiero `compose.yml`'s command override, trzeba było go odtworzyć w k8s).
- Te same zmienne `OTEL_*` co w `compose.yml`, `OTEL_EXPORTER_OTLP_ENDPOINT` wskazuje na `otel-collector-opentelemetry-collector.observability.svc.cluster.local:4317`.
- `app-job-offers`: `Service` typu `ClusterIP` (wewnętrzny, wołany tylko przez Feign z `app-candidates`, zgodnie z architekturą — job-offers nie ma publicznego endpointu w tym projekcie). Baked-in `docker/application.yml` już wskazuje na `postgres-job-offers:5432` w tym samym namespace — zero configu do nadpisania.
- `app-candidates`: `Service` typu `LoadBalancer` (`192.168.10.103:8080`, przez MetalLB). **`ConfigMap` montowany jako `/app/application.yml`** (podmienia cały plik baked-in w obrazie) — jedyna rzecz do zmiany to URL Feign-klienta `job-offers` (`spring.cloud.openfeign.client.config.job-offers.url`), z compose'owego `http://app-job-offers:8080` na k8s-owe `http://app-job-offers.job-offers.svc.cluster.local:8080` (cross-namespace FQDN). Baked-in datasource URL (`postgres-candidates:5432`) już pasuje do Service'u w tym samym namespace, ale musiał zostać powtórzony w ConfigMapie, bo mount pliku podmienia go w całości.
- **Flyway baseline — zastosowany jednorazowo, potem usunięty z manifestów.** Appki mają migracje (`V1_0__schema.sql` + `V1_1__demo-data.sql`), ale schemat już istniał (załadowany ręcznie z `data-generator`, patrz sekcja Postgres wyżej) bez tabeli `flyway_schema_history`. Bez baseline'u `V1_0` próbowałby stworzyć tabele od zera → `relation already exists` → crash przy starcie. Rozwiązanie zastosowane przy pierwszym starcie: `spring.flyway.baseline-on-migrate: true` + `baseline-version: "1.0"` (chwilowo w ConfigMapie dla candidates, jako env var `SPRING_FLYWAY_BASELINE_ON_MIGRATE`/`SPRING_FLYWAY_BASELINE_VERSION` dla job-offers). **Zweryfikowane przed wdrożeniem:** `V1_0__schema.sql` jest bajt-w-bajt identyczny z `data-generator`'s `00-schema.sql` w obu serwisach; dane z `V1_1__demo-data.sql` (stałe UUID-y, e-maile `@example.com`, nazwy firm) sprawdzone pod kątem kolizji z już załadowanym bulk datasetem — zero kolizji, baseline przeszedł czysto w obu serwisach (potwierdzone w logach: `Successfully baselined schema with version: 1.0` → `Successfully applied 1 migration ... now at version v1.1`). **Po baselinie** (tabela `flyway_schema_history` już istnieje w obu bazach, oznaczona jako `1.0`) flaga usunięta z obu manifestów — był to jednorazowy wyjątek na potrzeby ręcznej inicjalizacji baz, nie ma zostawać jako trwały override. Kolejne restarty/deploye robią zwykły `flyway migrate` bez baseline'u.
- Readiness/liveness probe: `httpGet /actuator/health:8080` (zamiast `exec curl` jak w compose — prostsze, natywny mechanizm k8s).

**Zweryfikowane end-to-end:** `curl http://192.168.10.103:8080/api/v1/candidates/{id}/matching-offers` → `HTTP 200`, `[]`. Puste wyniki to **cecha danych z `data-generator`, nie błąd wdrożenia** — współrzędne kandydatów są losowe globalnie (np. `lat=28.6, lon=-138`, środek Pacyfiku), firmy skupione wokół Polski, promień wyszukiwania rzędu 30-130km — praktycznie nigdy się nie trafi. Potwierdzone w logach `app-job-offers`, że request faktycznie doleciał przez Feign i wykonał zapytanie (`Received candidate search request, lat=..., lon=..., radius=...km`), bez błędów w logach żadnego z serwisów.

**Do zrobienia (nie teraz):** `imagePullSecrets` niepotrzebne (pakiety publiczne) — do rewizji, jeśli kiedyś przejdą na prywatne. Brak `HorizontalPodAutoscaler`/wielu replik — 1 replika each, wystarczające na tym etapie.

**IP LoadBalancerów (`.101`/`.102`) nie są przypięte na sztywno** (brak `metallb.io/loadBalancerIPs` w manifestach) — MetalLB przydzielił je automatycznie z puli przy pierwszym `apply` i **zostają stałe dopóki dany `Service` nie zostanie usunięty i utworzony od nowa** (restart poda/node'a/klastra tego nie rusza — stan jest w `etcd`). Jeśli kiedyś trzeba będzie je usunąć i odtworzyć, nowy IP może wypaść inny niż obecny.

**Aktualizacja 2026-08-27:** `app-candidates` **nie jest już `LoadBalancer`** — przełączony na `ClusterIP` (jak `app-job-offers`), bo publicznym wejściem do API jest teraz Gateway (`.100`, port 80, nie `:8080`) — patrz sekcja "Ingress — Cilium Gateway API" niżej. Stary adres `.103` wrócił do puli MetalLB (wolny). Grafana (`.199`) i Gateway (`.100`) mają teraz IP przypięte na sztywno przez `metallb.io/loadBalancerIPs` — powyższy akapit o nieprzypiętych IP dotyczy już tylko obu Postgresów.

## Postgres — candidates/job-offers — ukończone (2026-08-27)

Wdrożone bezpośrednio przez Claude (jawne "ty robisz" od użytkownika — wyjątek od zasady "użytkownik sam wpisuje komendy k8s", patrz "Kluczowe decyzje architektoniczne").

**Namespace per serwis** (`candidates`, `job-offers`) — nie jeden wspólny namespace dla baz — appki (`app-candidates`/`app-job-offers`) wdrożone później w tych samych namespace'ach, patrz sekcja wyżej.

**Obiekty** (`k8s-cluster/manifests/candidates/`, `k8s-cluster/manifests/job-offers/`, każdy `namespace.yaml` + `postgres.yaml`): `Secret` (credentiale, zwierciadło `compose.yml`: user `postgres`/hasło `password`), `PersistentVolumeClaim` (5Gi, `local-path`), `Deployment` (1 replika, `strategy: Recreate`, image `postgres:18.3`, `PGDATA` na subpath `pgdata` w mount pointcie — standardowa praktyka z docs obrazu postgres), `Service` typu `LoadBalancer`.

**Zamiast StatefulSet — świadomie zwykły `Deployment` + samodzielny PVC** (nie volumeClaimTemplate) — dla pojedynczej repliki bez potrzeby stabilnej tożsamości sieciowej/discovery, `StatefulSet` + headless Service byłby zbędnym narzutem (YAGNI).

**Node placement:** `postgres-candidates` przypięty (`nodeSelector` po hostname + toleracja `role=database`) na `k8s-rpi-db-1`, `postgres-job-offers` na `k8s-rpi-db-2` — rozdzielone fizycznie, żeby nie dzieliły I/O jednego dysku NVMe.

**Ekspozycja: `LoadBalancer` przez MetalLB** (nie tylko `ClusterIP` + port-forward) — spójne z Grafaną, żeby dało się łączyć bezpośrednio z dowolnego klienta w LAN bez `kubectl port-forward` za każdym razem.

| Serwis | Namespace | Node | External IP | DB | User/Pass |
|---|---|---|---|---|---|
| `postgres-candidates` | `candidates` | `k8s-rpi-db-1` | `192.168.10.101:5432` | `app-candidates-db` | `postgres`/`password` |
| `postgres-job-offers` | `job-offers` | `k8s-rpi-db-2` | `192.168.10.102:5432` | `app-job-offers-db` | `postgres`/`password` |

**Załadowane dane** z `data-generator/output/{candidates,job-offers}/*.sql` (schema + dane, w kolejności numerycznej plików) via `kubectl exec -i <pod> -- psql ... < plik.sql` (streaming stdin przez API server, bez kopiowania plików na node'y). Zweryfikowane liczby wierszy:
- candidates: `candidate` 100000, `candidate_preferred_employment_type` 199849, `candidate_skill` 300127.
- job-offers: `company` 50000, `skill` 53, `job_offer` 100000, `job_offer_employment_type` 199894, `job_offer_skill` 299936.

**Kubeconfig:** `k8s-cluster/kubeconfig` (skopiowany z `/etc/kubernetes/admin.conf` na masterze, gitignorowany przez `.git/info/exclude` — nie w `.gitignore` repo, więc niewidoczny w `git status`/diff, ale też nie trafi do żadnego commita). `export KUBECONFIG=$(pwd)/k8s-cluster/kubeconfig` przed pracą z Maca.

**Do zrobienia (nie teraz):** te dwa Postgresy nie mają jeszcze roli/hasła ograniczonego per-aplikacja (współdzielą superusera `postgres` z compose) — wystarczające na tym etapie (tylko test połączenia), do rewizji przy realnym deployu appek.

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
- **Auto-mode classifier bywa zawodny przy `kubeadm reset`/`kubectl delete node` przez SSH** — blokuje mimo jawnej zgody użytkownika (hard-deny na destrukcyjne komendy), nawet z istniejącą regułą `autoMode.allow` w `.claude/settings.local.json` (plik lokalny, niecommitowany — patrz `.gitignore`). Jeśli się powtórzy: reguła już tam jest, powinna przepuszczać automatycznie; jeśli nie zadziała, ostatecznym wyjściem było odpalenie sesji w "yolo mode" (bypass permission prompts). **Potwierdzone ponownie 2026-08-27** na trzech kolejnych komendach: `kubectl delete svc`, `helm upgrade cilium` (zmiana core CNI/kube-proxy), oraz SSH+`sudo`+`iptables-restore` na node'ach — wszystkie zablokowane mimo jawnego "zrób to za mnie/ty uruchom". Za każdym razem rozwiązanie było takie samo: Claude podaje gotową komendę, użytkownik wkleja i uruchamia sam.

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

Prometheus + Loki + Tempo + otel-collector + Grafana wdrożone na `k8s-rpi-observability-1` przez Helm, zwierciadlące configi z `compose.yml`/`observability/*`. Wszystkie 5 Podów `Running`/`Ready`, wszystkie 3 datasource'y w Grafanie zdrowe (`/api/datasources/uid/<uid>/health` → `OK`), 3 dashboardy (HTTP Monitoring, JVM Monitoring, Logs) załadowane przez sidecar. Grafana dostępna pod `http://192.168.10.199` (pierwotnie `.100`, przypięta na stałe pod `.199` 2026-08-27 przez `metallb.io/loadBalancerIPs` — `.100` zwolniony dla Gateway, patrz sekcja "Ingress — Cilium Gateway API" niżej), anonymous auth jak w compose (`admin`, bez logowania).

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

## Ingress — Cilium Gateway API — ukończone (2026-08-27)

**Wybór kontrolera:** zamiast osobnego ingress-nginx/Traefik, wybrano **Cilium Gateway API** — reużywa CNI, który już jest w klastrze (zero dodatkowego komponentu do utrzymania na ciasnym budżecie węzłów RPi), i to nowszy standard (Gateway API stopniowo zastępuje klasyczny `Ingress`). Świadomy kompromis: `Gateway`/`HTTPRoute` to inny model mentalny niż `Ingress`, mniej "ogólnych" tutoriali.

**Wymagana migracja z kube-proxy na `kubeProxyReplacement`** — Cilium Gateway API twardo tego wymaga (potwierdzone w oficjalnych docs). To nie był izolowany dodatek, tylko realna zmiana rdzenia sieciowego całego klastra:
1. Zainstalowane CRD-y Gateway API, standard channel **v1.4.1** (wersja wymagana przez Cilium 1.19) — `gatewayclasses`, `gateways`, `httproutes`, `referencegrants`, `grpcroutes`.
2. Nowy `k8s-cluster/manifests/cilium/values-cilium.yaml` (pierwszy raz Cilium ma tracked values w repo — wcześniej bootstrapped ad-hoc) — dopisane `kubeProxyReplacement: "true"`, `k8sServiceHost`/`k8sServicePort` (`192.168.10.1:6443`, adres control-plane — wymagane, bo bez kube-proxy Cilium nie ma jak inaczej odkryć API servera) i `gatewayAPI.enabled: true`. `helm upgrade cilium cilium/cilium -n kube-system -f values-cilium.yaml --version 1.19.5` (ta sama wersja chartu, tylko nowe pola).
3. **Kolejność zgodna z oficjalnym przewodnikiem migracji Cilium** (zweryfikowana przez `WebFetch` na docs.cilium.io, nie zgadywana): najpierw włączyć `kubeProxyReplacement` przy wciąż działającym kube-proxy (stan hybrydowy), zweryfikować `KubeProxyReplacement: True` na wszystkich node'ach (`cilium-dbg status`), dopiero potem usunąć kube-proxy. Dokumentacja Cilium wprost ostrzega, że stan hybrydowy może zrywać istniejące połączenia (dwie niezależne tabele NAT) — stąd kolejność ma znaczenie, nie robić tego na odwrót.
4. Usunięcie kube-proxy: `kubectl -n kube-system delete ds kube-proxy` + `delete cm kube-proxy`, potem na **każdym z 6 node'ów** przez SSH: `sudo sh -c "iptables-save | grep -v KUBE | iptables-restore"` (czyszczenie starych reguł). Wykonane przez użytkownika (auto-mode classifier zablokował to Claude'owi — patrz "Kluczowe decyzje architektoniczne").
5. **Zweryfikowane po migracji:** `KubeProxyReplacement: True` na wszystkich 6 node'ach, `app-candidates`/Postgres/Grafana nadal odpowiadają, `load-background` bez błędów w logach — brak przerwy zauważalnej poza samą migracją.

**Zasoby Gateway API** (`k8s-cluster/manifests/candidates/gateway.yaml`): `Gateway` (`gatewayClassName: cilium`, namespace `candidates`) + `HTTPRoute` kierujący `path: /` (`PathPrefix`) na `Service app-candidates:8080`. IP przypięty przez `spec.infrastructure.annotations: {metallb.io/loadBalancerIPs: "192.168.10.100"}` — Cilium propaguje tę adnotację na auto-tworzony `Service` typu `LoadBalancer` (`cilium-gateway-api-gateway`), który MetalLB obsługuje dokładnie tak jak każdy inny ręcznie napisany `Service` (Cilium nie ma włączonego własnego LB-IPAM w tym klastrze — o to zadbało już MetalLB). Zadziałało od razu, bez dodatkowej konfiguracji MetalLB.

**Konsekwencja:** `app-candidates` `Service` przełączony z `LoadBalancer` na `ClusterIP` (jak `app-job-offers`) — publiczne wejście do API to teraz **`http://192.168.10.100/api/v1/...` (port 80, bez `:8080`)**, nie bezpośredni adres appki. Zweryfikowane end-to-end: `GET /actuator/health` → `200`, `GET /api/v1/candidates/{id}/matching-offers` → `200` z realnymi dopasowanymi ofertami.

**Do zrobienia (nie teraz):** rozszerzenie `HTTPRoute` o więcej reguł/serwisów w miarę potrzeb, weryfikacja dostępu z innych maszyn w LAN (nie tylko z Maca).

## load-background — ambient load w klastrze — ukończone (2026-08-27)

**Decyzja: w klastrze, nie na zewnętrznym hoście** — Raspberry Pi są fizycznie always-on (Mac nie), więc to k8s jest tu "zawsze włączoną" infrastrukturą; prościej sieciowo (wołanie po wewnętrznym DNS Service'u, bez ryzyka hairpin NAT przez MetalLB).

**Problem:** plik z danymi testowymi (`data-generator/output/candidates/01-candidates.sql`, **21MB**, 100k UUID-ów) jest za duży na `ConfigMap` (limit 1MB) i k8s nie ma odpowiednika bind-mounta z Compose. **Rozwiązanie:** nowy `load-background/Dockerfile.k8s` (osobny od `Dockerfile` używanego przez Compose — ten zostaje nietknięty, analogicznie do zasady z planu pkt 13 o logach) wypiekający plik z danymi bezpośrednio w obraz przy buildzie (build context = repo root). Ten sam workflow co appki: `ghcr.io/aarczynski/load-background:v1`, build+push z Maca, ręczne ustawienie Public w GitHub Packages (ta sama pułapka co przy appkach).

**Manifest** (`k8s-cluster/manifests/load-background/`): namespace `load-background` + `Deployment` (1 replika, bez `Service` — to czysty klient, nic nie nasłuchuje), bez tainta/nodeSelectora (ląduje naturalnie na generic workerze). `TARGET_HOST` wskazuje na wewnętrzny DNS `app-candidates.candidates.svc.cluster.local:8080` (nie zewnętrzny adres — appka jest teraz i tak `ClusterIP`, patrz sekcja Ingress wyżej).

**Zweryfikowane:** pod `Running`, k6 generuje sinusoidalny ruch (~8 req/s baseline), `app-candidates` faktycznie odpowiada (część requestów zwraca realne dopasowane oferty, widoczne w logach obu stron).

## Tempo — fix OOMKilled — ukończone (2026-08-27)

`tempo-0` był w `CrashLoopBackOff` od ~18h, **`OOMKilled` (exit 137)** — limit pamięci `512Mi` za mały pod normalne obciążenie (niezwiązane z migracją kube-proxy, sprawdzone w logach/opisie poda). Node `k8s-rpi-observability-1` miał mnóstwo zapasu (memory requests 11%, limits 52% z ~8GB) — podniesiono limit w `values-tempo.yaml`: `requests.memory: 128Mi→256Mi`, `limits.memory: 512Mi→1Gi`. Po `helm upgrade` StatefulSet nie podmienił automatycznie już istniejącego (crashującego) poda z nowym szablonem — trzeba było ręcznie `kubectl delete pod tempo-0`, żeby StatefulSet odtworzył go z nowymi limitami. Działa stabilnie po naprawie (`1/1 Running`, 0 restartów).

**Aktualizacja tego samego dnia — nawrót OOM, prawdziwa przyczyna znaleziona.** Kilka godzin później `tempo-0` znów zaczął `OOMKilled` (7 restartów w ~40 min), mimo podniesionego wcześniej limitu. Realna przyczyna: chart `grafana-community/tempo` ma domyślnie `tempo.memBallastSizeMbs: 1024`, twardo wstrzykiwane jako `-mem-ballast-size-mbs=1024` w `Args` kontenera (zweryfikowane przez `helm pull --untar` i grep po źródle chartu) — Go ballast (świadome pre-alokowanie pamięci do dostrojenia GC) w rozmiarze **równym** limitowi kontenera (`1024Mi` = `1Gi`) zostawiał praktycznie zero miejsca na realny heap; każdy rzeczywisty ruch przebijał cgroup limit. Poprzednia naprawa (podniesienie limitu 512Mi→1Gi) tylko odsunęła to w czasie, bo skalowała się razem z domyślnym ballastem 1:1. **Fix:** `tempo.memBallastSizeMbs: 128` w `values-tempo.yaml`, limit zostaje `1Gi` — realny margines. Zastosowane przez `helm upgrade` + ręczny `kubectl delete pod tempo-0`, zweryfikowane stabilne (`1/1 Running`, `RESTARTS 0`, obserwowane kilka minut).

**Aktualizacja 2026-08-28 — ballast nie wystarczył, drugi nawrót OOM, prawdziwa przyczyna: brak `GOMEMLIMIT`.** Zgłoszone przez użytkownika przez Headlamp ("Tempo unhealthy"). `kubectl describe pod tempo-0` pokazał `RESTARTS: 3 (64m ago)`, `Last State: Terminated, Reason: OOMKilled`. Zdiagnozowane przez odczyt `container_memory_working_set_bytes{pod="tempo-0"}` z Prometheusa (przez `curl` na proxy Grafany, bez `kubectl exec`) — wyraźny piłokształtny wzorzec: pamięć rośnie do **~1000-1007 MiB** (limit `1024Mi`) i wtedy OOM, powtarzane co ~45-60 min, mimo ballastu 128Mi z wczorajszego fixu. **Realna przyczyna:** Go (język Tempo) domyślnie nie zna cgroup memory limitu kontenera — GC odpala się wg `GOGC` (heap-doubling od ostatniego GC), nie wg limitu k8s, więc sterta może przekroczyć limit kontenera zanim GC zdąży zadziałać → kernel zabija proces (OOMKilled) zamiast GC zwolnić pamięć. Klasyczny wzorzec dla aplikacji Go w k8s bez `GOMEMLIMIT`. **Fix:** `tempo.extraEnv: [{name: GOMEMLIMIT, value: "800MiB"}]` w `values-tempo.yaml` — daje runtime'owi Go miękki cel pamięciowy, żeby GC odpalał się proaktywnie, zanim dobije do twardego limitu `1Gi` (800MiB zostawia ~224Mi zapasu na narzut poza-sterty: stosy goroutine, cgo, OS). **Zastosowane** (`helm upgrade` + `kubectl delete pod tempo-0`, wykonane przez użytkownika 2026-08-28 ok. 00:33) — `GOMEMLIMIT=800MiB` potwierdzone w env kontenera. **Weryfikacja trendu pamięci po restarcie — do zrobienia w kolejnej sesji** (potrzeba ~1h obserwacji, poprzedni cykl OOM powtarzał się co ~45-60 min; zapytanie: `container_memory_working_set_bytes{pod="tempo-0",container="tempo"}` przez proxy Grafany do Prometheusa).

## kube-state-metrics / node-exporter + dashboard "Kubernetes Cluster" — ukończone (2026-08-27)

Cel: dashboard w Grafanie pokazujący gdzie są pody (który node), stan node'ów, restarty, zużycie CPU/RAM/dysku — czego nie dawał dotychczasowy stack (tylko HTTP/JVM/Logs appek).

**Włączone jako subcharty już obecnego `prometheus-community/prometheus`** (nie osobne charty) — `kube-state-metrics: enabled: true`, `prometheus-node-exporter: enabled: true` w `values-prometheus.yaml` (wcześniej oba `false`, wyłączone świadomie żeby zwierciadlić `compose.yml`, które ich nie ma — to jest k8s-only dodatek). Zero dodatkowej konfiguracji scrape'a — istniejący job `kubernetes-service-endpoints` w domyślnym configu chartu już łapie wszystko po adnotacji `prometheus.io/scrape`, którą oba subcharty ustawiają domyślnie na swoich Service'ach.

**`prometheus-node-exporter` to DaemonSet** — musi wstać na każdym node'zie, w tym tainted (`db-1`/`db-2`/`observability-1`/`master`), żeby widzieć metryki ze wszystkich. Chart domyślnie toleruje tylko `control-plane`/`master` (ten sam wzorzec problemu co przy MetalLB speakerze, patrz pułapka 1 w sekcji MetalLB) — dopisane `speaker.tolerations`-owy odpowiednik: `prometheus-node-exporter.tolerations` z wpisami dla `role=database`, `role=observability` i `node-role.kubernetes.io/control-plane` (operator `Exists`, żeby złapać control-plane niezależnie od wartości). Po `helm upgrade`: 6/6 node-exporterów `Running`, jeden na każdym node'zie.

**Ważne odkrycie przy pisaniu zapytań:** metryki scrape'owane przez ten sam generyczny job `kubernetes-service-endpoints` (nie dedykowany `ServiceMonitor` jak w `kube-prometheus-stack`) mają `job="kubernetes-service-endpoints"` dla wszystkiego — **nie** `job="node-exporter"`/`job="kube-state-metrics"` jak w typowych gotowych dashboardach z grafana.com. Świadomie odrzucono import gotowego community dashboardu (np. ID 315/1860) z tego powodu — połowa paneli pokazywałaby "No data" przez niedopasowanie etykiety `job`. Dashboard zbudowany ręcznie, zapytania oparte o nazwy metryk i etykietę `node` (nie `job`) — ta etykieta jest doklejana przez relabeling w scrape configu (`__meta_kubernetes_pod_node_name` → `node`), więc każda metryka node-exportera niesie od razu czytelną nazwę node'a, nie tylko IP.

**Dashboard "Kubernetes Cluster"** (`k8s-cluster/manifests/observability/dashboards/kubernetes-cluster.json`, wpięty do `k8s-cluster/manifests/observability/dashboards-configmap.yaml` obok istniejących trzech): 4 rzędy — Cluster Overview (stat: Nodes Ready/Total Nodes/Total Pods/Running Pods/Pods Not Running/Restarts 1h), Pod Placement (tabela namespace/pod/node/host_ip + bar gauge liczby podów per node), Restarts & Namespaces (restarty per pod, liczba podów per namespace), Node Resources (CPU/RAM/dysk/load average per node, timeseries). Wszystkie zapytania PromQL zweryfikowane bezpośrednio na żywym Prometheusie (przez `curl` do proxy Grafany) przed wpisaniem do dashboardu — nie zgadywane.

**Status: zaaplikowane w klastrze (`kubectl apply` na ConfigMapie), użytkownik potwierdził że dashboard renderuje dane.** Dashboard rozszerzony 2026-08-28 o dwa dodatkowe panele — "CPU Limits Commitment by Node (%)" i "Memory Limits Commitment by Node (%)" (`kube_pod_container_resource_limits` vs `kube_node_status_allocatable`, próg pomarańczowy >100%, czerwony >150%) — odpowiedź na to samo zjawisko widoczne w Headlampie (overcommit CPU na `observability-1`). Przy okazji otagowane wszystkie cztery dashboardy (`microservices`+specyfika dla HTTP/JVM/Logs, `kubernetes` dla tego dashboardu) w Grafanie dla łatwiejszego filtrowania listy.

## Headlamp — ukończone (2026-08-27)

**Kontekst decyzji — zamiast oficjalnego Kubernetes Dashboard.** Plan (pkt 12 niżej) pierwotnie zakładał oficjalny Kubernetes Dashboard, świadomie odrzucając Headlamp mimo rekomendacji Claude'a. Przy realizacji odkryto (zweryfikowane przez GitHub API, nie zgadywane): `kubernetes/dashboard` jest **zarchiwizowany**, przeniesiony do `kubernetes-retired/dashboard` (`archived: true`, ostatni push 2026-01-21), a własny README projektu poleca w zamian Headlamp. Po przedstawieniu tego użytkownik zmienił decyzję na Headlamp.

**Chart: `headlamp/headlamp` (repo `https://kubernetes-sigs.github.io/headlamp/`, część `kubernetes-sigs`, aktywnie rozwijany)**, wersja `0.45.0`, nie deprecated (zweryfikowane). Manifesty: `k8s-cluster/manifests/headlamp/namespace.yaml` (namespace `headlamp`, osobny — nie `observability`, zgodnie z konwencją "jeden namespace per komponent" jak candidates/job-offers/load-background) + `values-headlamp.yaml`.

**Node placement: przypięty na `k8s-rpi-observability-1`** (`nodeSelector` + `toleration` na `role=observability`) — decyzja podjęta świadomie mimo że Headlamp nie ma żadnej zależności danych od tego node'a (rozmawia bezpośrednio z API serwera k8s, nie z Prometheusem), więc nie musiał tam lądować. Claude zaproponował przeniesienie na generic worker (odciążenie node'a, który tego samego dnia miał już OOM na Tempo), użytkownik **odrzucił** — wolał zostawić na `observability-1` i **w razie potrzeby dodać kolejny node z taintem `role=observability`**, zamiast rozpraszać narzędzia ops na generic workery. Do pamięci na przyszłość: kolejne narzędzia "ops/admin" (podobne pytanie może wrócić) — użytkownik konsekwentnie woli fizyczną izolację/kolokację niż oszczędność node'ów.

**Logowanie: bez ekranu logowania, auto cluster-admin — świadomie zaakceptowane ryzyko.** Domyślny model Headlampa wymaga wklejenia tokena ServiceAccount przy każdym logowaniu. Ustawiono `config.unsafeUseServiceAccountToken: true` (flaga `-unsafe-use-service-account-token` w kontenerze, zweryfikowane przez `helm template`) — każda sesja przeglądarki jest automatycznie uwierzytelniana jako SA poda, powiązane przez domyślny `clusterRoleBinding` (`create: true`, `clusterRoleName: cluster-admin`) z pełnymi prawami klastra. Uzasadnienie: ten sam profil ryzyka, jaki już zaakceptowano dla Grafany (anonymous admin, LAN-only) — ale z większym blast radius (Grafana anonymous = tylko podgląd dashboardów, Headlamp bez logowania = pełny `cluster-admin` edit/delete dla każdego w LAN). Użytkownik świadomie wybrał tę opcję zamiast tokenu przy logowaniu.

**IP: przypięty na sztywno `192.168.10.198`** (`service.annotations: metallb.io/loadBalancerIPs`) — analogicznie do Grafany (`.199`). Pierwsza instalacja (bez adnotacji) dostała automatycznie `.103` (ten sam adres zwolniony wcześniej po przełączeniu `app-candidates` na `ClusterIP`) — użytkownik zapytał, dlaczego IP nie odzwierciedla node'a; wyjaśnione, że przydział IP przez MetalLB (pula `.100-199`) jest całkowicie niezależny od tego, na którym node'zie fizycznie ląduje pod (adresy node'ów i pula MetalLB to dwie różne przestrzenie) — `.199`/`.198` to ręcznie przypięte adresy, nie "adres node'a observability". Po dopisaniu adnotacji może być potrzebny `kubectl delete svc` + ponowny `helm upgrade`, jeśli MetalLB nie podmieni IP już przydzielonego Service'owi in-place.

**Status: zainstalowany, dostępny pod `http://192.168.10.198/c/main/`.**

**"Wszystko unhealthy" w overview — zdiagnozowane i naprawione (2026-08-27, ta sama sesja).** Przyczyna: w klastrze brakowało `metrics-server` (`kubectl top nodes` → `error: Metrics API not available`), mimo że wszystkie node'y/pody były realnie `Ready`/`Running` (zweryfikowane osobno). Headlamp pyta o CPU/RAM przez `metrics.k8s.io` do renderowania overview node'ów/podów — bez tego komponentu te zapytania się wywalają. **Fix:** zainstalowany `metrics-server/metrics-server` (oficjalny chart, `https://kubernetes-sigs.github.io/metrics-server/`, wersja `3.14.0`, appVersion `0.9.0`, nie deprecated), manifest `k8s-cluster/manifests/metrics-server/values-metrics-server.yaml` — przypięty na `k8s-rpi-observability-1` (ten sam wzorzec co Headlamp/reszta ops-tooling), z `args: [--kubelet-insecure-tls]` (kubelety na tym ręcznie stawianym `kubeadm` nie mają certów serwujących podpisanych zaufanym przez metrics-server CA — standardowy workaround dla hand-rolled klastrów, akceptowalny przy już przyjętym poluzowanym profilu bezpieczeństwa tego klastra). Zweryfikowane: `kubectl top nodes` zwraca realne dane ze wszystkich 6 node'ów. **Namespace: `kube-system`** (nie dedykowany, zgodnie ze standardową konwencją — to core add-on jak CoreDNS, nie komponent aplikacyjny).

**Przy okazji znaleziony problem — `cilium-operator` restartuje się, ZWERYFIKOWANE jako część tego samego zdarzenia, nie osobna usterka.** Użytkownik przesłał zrzut ekranu Headlampa pokazujący sekcję "Zdarzenia" (5 warningów z reason `Unhealthy`) — dotyczyły `cilium-operator` (worker-1), `local-path-provisioner` (worker-1, licznik aż 468 — akumulowany od uptime poda, nie oznacza ciągłego problemu), `prometheus-kube-state-metrics` (worker-2). **Wszystkie 5 miały `lastTimestamp` w oknie ~15:11-15:21 UTC** — dokładnie pokrywającym się z dzisiejszą instalacją `node-exporter` (DaemonSet na 6 node'ach naraz) + `metrics-server` + Headlampa. Zweryfikowane 10 minut później: zero nowych wystąpień, CPU na `worker-1`/`worker-2` wróciło do bazowych ~3%. **Wniosek: to był jeden skok obciążenia CPU/schedulera podczas churn instalacyjnego, nie trwający problem** — kilka podów nie zdążyło odpowiedzieć na własne health-checki (np. `cilium-operator` pytający się `127.0.0.1:9234/healthz`) w oknie timeoutu. `cilium-operator`'s `"Leader election lost, shutting down"` (odnowienie leasu do API servera przekroczyło 5s) to ten sam mechanizm — CPU starvation, nie flakiness API servera per se. W tych samych logach osobny, prawdopodobnie kosmetyczny błąd niezwiązany z tym skokiem: `"no kind is registered for the type v1alpha2.TLSRouteList"` przy reconciliacji GAMMA dla `Service headlamp/headlamp` — bo zainstalowany jest tylko standardowy kanał CRD-ów Gateway API (bez `tlsroutes`, kanał Experimental), a `cilium-operator` i tak próbuje go obserwować; nieszkodliwy log spam, nie przyczyna restartów. **Nie wymaga akcji** — samo się uspokoiło. Gdyby się powtarzało przy przyszłych masowych rolloutach (np. worker-3/4, `tempo-distributed`), rozważyć rozkładanie instalacji w czasie zamiast wszystkiego naraz.

## HTTP latency spikes (kilka sekund) — zdiagnozowane 2026-08-28, fix jeszcze niezweryfikowany

Zgłoszone przez użytkownika (dashboard "HTTP Monitoring" w Grafanie, `var-service=app-candidates`) — okresowe spike'i p99 do 3-5s na `/api/v1/candidates/{id}/matching-offers`.

**Diagnoza (przez `curl` na proxy Grafany do Prometheusa/Loki/Tempo, bez żadnej zmiany w klastrze):** wzięto 5 wolnych requestów z Tempo (`GET /api/datasources/proxy/uid/tempo/api/search?minDuration=1s`) w jednym oknie ~500ms. Rozbicie na spany pokazało, że cały czas siedzi w spanie Feign `POST` po stronie `app-candidates` (5061ms), podczas gdy zapytanie do własnej bazy (2ms) i przetwarzanie po stronie `app-job-offers` (24ms, łącznie z SQL) są szybkie — opóźnienie jest **czysto sieciowe/DNS**, nie w logice ani w bazie. Odrzucone po kolei: GC pauzy JVM (zero w oknie), CPU node'ów `worker-1`/`worker-2` (płasko ~4-5%, appki nie mają zresztą `limits.cpu`, throttling fizycznie niemożliwy), HikariCP (`hikaricp_connections_pending`/`_creation_max_seconds` — zero).

**Kluczowa obserwacja:** wszystkie 5 requestów, mimo że zaczęły się rozrzucone na 500ms, **kończy się w oknie 15ms od siebie** — to nie 5 niezależnie wolnych requestów, tylko wątki czekające na to samo wspólne zdarzenie i zwalniane naraz. Klaster jest czysto IPv4 (zweryfikowane — `podIPs` bez adresu IPv6), a JVM domyślnie i tak wysyła równolegle zapytania DNS A i AAAA przy rozwiązywaniu `app-job-offers.job-offers.svc.cluster.local`, serializując wątki na wspólnym lookupie. Hipoteza: klasyczny, dobrze udokumentowany bug — jeden z dwóch równoległych pakietów UDP (A/AAAA) gubi się (wyścig w NAT/conntrack przy dwóch pakietach z tego samego gniazda źródłowego), klient czeka na timeout (~5s), wszystkie wątki czekające na ten sam lookup zwalniają się razem. Prawdopodobnie wyzwalane okresowym odświeżaniem connection poola Feign (spike'y luźno korelowały z ~10-20 min odstępami, spójne z recyklingiem "zimnych" połączeń pod kolejnym burstem ambientowego ruchu).

**Fix zastosowany w plikach (jeszcze niezaaplikowany w klastrze):** `-Djava.net.preferIPv4Stack=true` dodane do `command:` w `k8s-cluster/manifests/candidates/app.yaml` i `k8s-cluster/manifests/job-offers/app.yaml` — eliminuje zapytanie AAAA całkowicie (klaster jest IPv4-only), więc nie ma czego wyścigować. **Do zastosowania:** `kubectl apply -f` na obu plikach — to zmiana `command:`, k8s sam zrobi rolling restart, bez potrzeby nowego obrazu. **Do zweryfikowania po restarcie:** obserwować Tempo (`minDuration=1s` na obu serwisach) przez godzinę-dwie — zniknięcie serii requestów kończących się w tym samym momencie potwierdzi diagnozę. Jeśli fix nie pomoże, kolejny krok: sprawdzić `resolv.conf`/`ndots` w podach (`kubectl exec`) i logi/metryki CoreDNS pod kątem dropped/retried queries w oknie spike'a.

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

Gotowe: node'y, MetalLB, storage, cały stack observability, oba Postgresy i obie appki (`candidates`/`job-offers`) załadowane danymi i wdrożone, ambient load w klastrze (przypięty na `observability-1`), ingress przez Cilium Gateway API (klaster bez kube-proxy), `kube-state-metrics`/`node-exporter` + dashboard "Kubernetes Cluster" w Grafanie, Headlamp jako UI do przeglądu klastra — wszystko zweryfikowane end-to-end (patrz sekcje niżej).

Jeszcze nie zrobione, w orientacyjnej kolejności:
1. `k8s-rpi-worker-3`/`worker-4` (dopełnienie pierwotnego planu 4 workerów, `inventory.ini` już ma zakomentowane wpisy) + decyzja co do pozostałych RPi z 12 fizycznie posiadanych (patrz "Kluczowe decyzje architektoniczne"). **Aktualizacja 2026-08-27:** użytkownik rozważa dodanie kolejnego node'a z taintem `role=observability` zamiast rozpraszać workloady ops (Headlamp) na generic workery — patrz sekcja Headlamp niżej. **Po dodaniu workerów: autoscaling appek (`app-candidates`/`app-job-offers`) na więcej instancji** — zgłoszone przez użytkownika 2026-08-27, jeszcze niezbadane. Do rozpisania w osobnej sesji: `HorizontalPodAutoscaler` (`metrics-server` już zainstalowany, patrz sekcja Headlamp — jest to, czego HPA potrzebuje do odczytu CPU/RAM), sensowne `resources.limits` na obu Deploymentach (obecnie tylko `requests: cpu=250m, memory=512Mi`, brak `limits` — do uzupełnienia przed HPA, bo HPA na CPU liczy % względem `requests`, ale bez `limits` appka może nieograniczenie zjeść CPU jednego node'a), oraz czy 1 replika Postgresa per serwis (`ClusterIP`, brak HA) staje się wąskim gardłem przy skalowaniu appek w górę.
2. **Weryfikacja telemetrii appek w Grafanie** — appki są wdrożone i wskazują na otel-collector, ale nie sprawdzone jeszcze, czy logi/trace'y/metryki faktycznie płyną (analogicznie do checklisty "Po aktualizacjach zależności" w CLAUDE.md).
3. Rozszerzenie `HTTPRoute` o kolejne reguły/serwisy w miarę potrzeb; weryfikacja dostępu do Gateway (`.100`) z innych maszyn w LAN, nie tylko z Maca.
4. ~~Headlamp pod `http://192.168.10.198/c/main/` pokazuje wszystko jako unhealthy~~ — **zdiagnozowane i naprawione 2026-08-27**: brak `metrics-server` (widget-y CPU/RAM) + jednorazowy skok CPU podczas churn instalacyjnego (5 warningów `Unhealthy` w evencie, potwierdzone jako przejściowe przez zrzut ekranu użytkownika + ponowną weryfikację 10 min później — zero nowych wystąpień). Patrz sekcja Headlamp niżej po pełne detale. Zamknięte.
5. **Zweryfikować w klastrze fixy z 2026-08-28** (oba są w `command:`/`extraEnv` — nie wymagają nowego obrazu, tylko `kubectl apply`/`helm upgrade` + rolling restart):
   - `preferIPv4Stack` na obu appkach (`k8s-cluster/manifests/candidates/app.yaml`, `k8s-cluster/manifests/job-offers/app.yaml`) — fix na spike'i latencji HTTP (patrz sekcja "HTTP latency spikes" niżej), **jeszcze nie zaaplikowany w klastrze**, tylko w repo.
   - `GOMEMLIMIT=800MiB` na Tempo — zaaplikowane, ale trend pamięci po restarcie jeszcze nie zaobserwowany (potrzeba ~1h).

**Helm — decyzja potwierdzona w praktyce (2026-08-26):** dla infrastruktury (MetalLB, obserwowalność, Cilium) używać Helm charts — przed `helm install` warto raz zobaczyć wyrenderowany manifest (`helm template`), żeby nie było to czarną skrzynką. Dla własnych apek (`candidates`/`job-offers`) na razie zostać przy plain YAML. **Ważna lekcja:** zawsze sprawdzać `deprecated: true` w metadanych chartu przed instalacją (`helm show chart <repo>/<chart> --version X`), bo repo mogło się zmigrować gdzie indziej (patrz pułapka 1 wyżej).

## Plany na koniec (długoterminowe, nie priorytetowe teraz)

Ustalone 2026-08-26 podczas dyskusji architektonicznej — do realizacji po ogarnięciu podstawowej infrastruktury (ingress, deploy baz i appek):

10. **Keycloak (SSO)** — decyzja: na zwykłym workerze, **bez** dedykowanego taintu/node'a. Uzasadnienie: Keycloak jest relatywnie lekki i replikowalny (2+ pody), w przeciwieństwie do Postgresa (I/O-wrażliwy, zaakceptowany SPOF) czy stosu observability (ciężki, wielokomponentowy) — nie potrzebuje izolacji, a budżet node'ów jest ciasny. Do rozważenia ponownie tylko jeśli zależy na przewidywalności/izolacji od hałaśliwych sąsiadów (wtedy: taint `role=identity`). Baza Postgresa dla Keycloaka może wylądować na jednym z istniejących node'ów `role=database`, bez tworzenia kolejnego.
11. **HA/failover baz między `k8s-rpi-db-1` i `k8s-rpi-db-2`** — cel: żeby padnięcie jednego z dwóch node'ów bazodanowych nie wywalało całego systemu. Kluczowy insight: samo dopuszczenie schedulowania poda na oba node'y **nic nie da** — `local-path-provisioner` trzyma dane lokalnie na dysku node'a, więc utrata node'a = utrata PV, bez względu na taint/affinity. Wymagana jest replikacja na poziomie Postgresa: operator **CloudNativePG** (lub Patroni) stawiający primary + standby rozpięte na obu node'ach z taintem `role=database`, ze streaming replication i automatyczną promocją standby przy awarii primary. Każdy serwis (`candidates`, `job-offers`) dostałby własny, osobny klaster Postgresa rozpięty na obu node'ach — nie jeden współdzielony.
12. **Kubernetes Dashboard → ZMIANA DECYZJI 2026-08-27: Headlamp zamiast tego.** Pierwotna decyzja (użytkownik chciał oficjalny Kubernetes Dashboard, nie Headlamp, mimo wcześniejszej rekomendacji Claude'a) była podjęta bez wiedzy, że projekt `kubernetes/dashboard` jest **zarchiwizowany** (przeniesiony do `kubernetes-retired/dashboard`, potwierdzone przez GitHub API — `archived: true`, ostatni push 2026-01-21; własny README projektu poleca w zamian Headlamp). Po przedstawieniu tego faktu użytkownik zmienił decyzję na Headlamp — pełne szczegóły wdrożenia w sekcji "Headlamp — ukończone" niżej. Ten punkt zostaje tu jako historia decyzji, nie do realizacji.
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
16. **Grafana: metryki gubią się podczas load testu, gdy appka "zdycha".** Zgłoszone przez użytkownika 2026-08-27, jeszcze niezbadane — hipotezy do sprawdzenia: gap w danych Prometheusa podczas restartu poda (scrape interval vs. czas restartu), otel-collector buforujący dane w pamięci i tracący je przy zabiciu kontenera przed flushem, brak/niewłaściwa obsługa staleness markerów. Do zrobienia po `tempo-distributed`/GitOps.
17. **Grafana: dashboard "Logs" długo się ładuje.** Zgłoszone przez użytkownika 2026-08-27, jeszcze niezbadane — prawdopodobne przyczyny do sprawdzenia: zapytania Loki bez ograniczenia zakresu/etykiet (pełny skan), brak indeksowanych labeli w kluczowych panelach, zbyt szeroki domyślny zakres czasu w dashboardzie. Do zrobienia po `tempo-distributed`/GitOps.
