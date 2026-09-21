# Handoff: klaster k8s na RPi5

Skondensowany handoff — stan na 2026-09-20. Pełna, szczegółowa narracja diagnostyczna (sesja po sesji, ze
wszystkimi ślepymi zaułkami) żyje w historii gita tego pliku (`git log -p -- .claude/handoff-k8s-rpi-cluster.md`),
gdyby kiedyś trzeba było wrócić do detali konkretnej diagnozy.

## Obecny stan (skrót)

12/12 node'ów fizycznych w klastrze, `kubeadm` (nie k3s), Cilium (CNI + Gateway API, bez kube-proxy), MetalLB (L2),
`local-path-provisioner`. Oba mikroserwisy (`app-candidates`/`app-job-offers`) + ich Postgresy wdrożone, dane
załadowane (100k candidates / 50k job offers, `data-generator`). Stack observability: Prometheus/Loki/Grafana/
Headlamp/Hubble + Tempo w architekturze `tempo-distributed` (Kafka+MinIO). Ambient load (`load-background`, k6)
działa 24/7. Tabela node taintów/IP jest w `CLAUDE.md` (nie duplikować tutaj).

**Zmierzony sufit RPS: 1900 RPS bezpieczne, 2200 RPS już zamula.** 1900rps potwierdzone czyste (0% KO, p99=556ms,
max=1033ms, ~9 min sustained hold, 1.14M requestów) 2026-09-20 po tym jak `app-candidates` dostał 3. replikę
(`worker-5`, HPA pinned na stałe — patrz punkt 2. niżej). To jest wyższe niż poprzedni udokumentowany sufit
(1500 bezpieczne/1600 na granicy, z 2 replikami candidates) — 3. replika realnie podniosła pułap. **2200rps już
zamula, ale to nie candidates** — `app-job-offers` (dalej tylko 2 repliki, brak wolnego workera na 3.) dobija do
swojego limitu 3 rdzeni i jest realnie throttlowany, co przez synchroniczne wywołanie Feign z candidates objawia
się jako spowolnienie candidates (patrz `k8s-cluster/RPS-SCALING.md` fix #11). 1200rps potwierdzone też na pełnym
5-minutowym sustained hold, zero powtórki `observability-1` `NodeNotReady`. Pełna historia fixów RPS (async
logging, Postgres parallel workers off, split query, anti-affinity, itd.) w `k8s-cluster/RPS-SCALING.md` — czytać
przed każdą kolejną pracą nad skalowaniem, nie duplikować tutaj.

## TODO / Next steps

0. **[ZROBIONE i zweryfikowane na k8s, 2026-09-21] Fix OTel Micrometer bridge — poprzednia wersja (2026-09-20)
   wyłączyła złą flagę.** `jvm_cpu_recent_utilization_ratio` zamulało się na sztywne 0 dla pojedynczych instancji
   JVM zaraz po pierwszej realnej próbce (gorzej po pełnym restarcie klastra). Sesja 2026-09-20 wyłączyła
   `OTEL_INSTRUMENTATION_MICROMETER_ENABLED` sądząc, że to on bridguje Micrometer→OTel — **błędnie**: po kolejnym
   pełnym restarcie klastra (2026-09-21, power outage) problem wrócił identycznie (1 instancja na serwis, flat 0
   przez >30 min ciągłych próbek, potwierdzone `query_range`), mimo że ta flaga była poprawnie `false` na
   wszystkich podach. Real root cause znaleziony przez sprawdzenie `metadata.yaml` obu modułów instrumentacji w
   repo `open-telemetry/opentelemetry-java-instrumentation`:
   - `instrumentation/micrometer/micrometer-1.5` (`OTEL_INSTRUMENTATION_MICROMETER_ENABLED`) — instrumentuje
     **appki własny, ręcznie tworzony** `MeterRegistry`. Ta appka żadnego takiego nie tworzy — flaga nigdy nie
     robiła nic w tym projekcie.
   - `instrumentation/spring/spring-boot-actuator-autoconfigure-2.0`
     (`OTEL_INSTRUMENTATION_SPRING_BOOT_ACTUATOR_AUTOCONFIGURE_ENABLED`) — opis wprost: *"This instrumentation
     configures the OpenTelemetry Micrometer bridge to receive metrics from Spring Boot Actuator. It does not
     produce telemetry on its own."* **To jest faktyczny bridge** — i został zostawiony `true` przez cały czas.
   Potwierdzone live 2026-09-21: mimo `MICROMETER_ENABLED=false` na obu podach ze stuck-0, Prometheus dalej miał
   serię `process_cpu_usage{otel_scope_name="io.opentelemetry.micrometer-1.5"}` dla dokładnie tych instancji —
   dowód, że bridge nadal eksportował i nadal się bił o te same nazwy metryk z natywną instrumentacją semconw
   (`io.opentelemetry.runtime-telemetry-java8`), tak jak opisuje
   [opentelemetry-java-instrumentation#11122](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/11122)
   ("your best option is not to enable both of these instrumentations at the same time"). Oba moduły są
   `disabled_by_default: true` upstream — projekt jawnie włączał oba bez potrzeby.
   **Fix (branch `fix/otel-micrometer-jvm-metric-clash`)**: `OTEL_INSTRUMENTATION_SPRING_BOOT_ACTUATOR_AUTOCONFIGURE_ENABLED`
   ustawione na `false` obok już-`false` `MICROMETER_ENABLED`, w `compose.yml` i obu
   `k8s-cluster/manifests/{candidates,job-offers}/app.yaml`.
   - **k8s (fizyczny klaster)**: zaaplikowane i wdrożone (`kubectl apply` + `rollout restart` obu Deploymentów,
     2026-09-21). **Zweryfikowane żywe**: żaden z 5 nowych podów nie eksportuje już `process_cpu_usage` (stare
     serie z `io.opentelemetry.micrometer-1.5` to tylko widmowe próbki z usuniętych podów, wygasną same);
     `jvm_cpu_recent_utilization_ratio` faluje normalnie na wszystkich 5 instancjach (`query_range` na nowych
     instance ID — brak flat 0).
   - **Minikube**: dziedziczy z bazowych manifestów — **zweryfikowane wieczorem 2026-09-21** (patrz notatka
     "SUPERSEDOWANE" niżej dla finalnej wersji z `otel-metrics-filter`, nie tym pierwotnym "oba false").
   - **Docker Compose**: zmiana zrobiona w `compose.yml`, **`docker compose up` i weryfikacja dashboardu JVM dalej
     nie zrobione — jedyne środowisko z 3 bez live weryfikacji.**
   **Lekcja ogólna**: przy instrumentacjach OTel javaagent zawsze sprawdzać `metadata.yaml` konkretnego modułu w
   repo upstream zamiast zgadywać po nazwie zmiennej środowiskowej co dana flaga robi — nazwy takie jak
   `SPRING_BOOT_ACTUATOR_AUTOCONFIGURE_ENABLED` nie sugerują że to one kontrolują bridge metryk.

   **[SUPERSEDOWANE tego samego dnia, 2026-09-21 wieczorem] "Oba `false`" był kompromisem (poprawny CPU, martwe
   `hikaricp_*` na dashboardzie Postgresa — Hikari Pool/Pending Connections), nie finalnym rozwiązaniem.**
   Użytkownik chciał obu naraz. Sprawdzone: nie da się tego osiągnąć samą flagą — SDK OTel nie ma dla samodzielnego
   javaagenta żadnego wbudowanego mechanizmu filtrowania metryk po nazwie/scope (`otel.experimental.metrics.view.config`
   istnieje tylko dla OTel Spring Boot Startera, którego tu nie ma). Jedyny działający, oficjalnie wspierany sposób:
   **własne rozszerzenie javaagenta** — nowy moduł Gradle `otel-metrics-filter/`
   (`MicrometerJvmMetricFilterCustomizer implements AutoConfigurationCustomizerProvider`,
   `addMetricExporterCustomizer`), które odrzuca metryki `jvm.*` **tylko** ze scope'u `io.opentelemetry.micrometer-1.5`
   tuż przed eksportem — natywna instrumentacja zostaje jedynym źródłem `jvm.*` (wszystkie 38 nazw, bez strat),
   most Micrometer zostaje jedynym źródłem `hikaricp.*` i reszty. Podpięte przez `OTEL_JAVAAGENT_EXTENSIONS=./otel-metrics-filter.jar`
   + z powrotem `OTEL_INSTRUMENTATION_SPRING_BOOT_ACTUATOR_AUTOCONFIGURE_ENABLED=true`. Zweryfikowane empirycznie
   (dwukrotnie, po tym jak pierwszy test złapał stary, niezaktualizowany obraz w cache minikube — patrz pułapka
   `minikube image load` niżej): `jvm_cpu_recent_utilization_ratio` faluje normalnie na wszystkich instancjach na
   **obu** klastrach (RPi i minikube), `hikaricp_connections_active` ma dane na obu. Jar kopiowany do obrazu obok
   agenta (`build.gradle`: `copyOtelMetricsFilter`, Dockerfile: `COPY ./build/otel-agent/otel-metrics-filter.jar ./`).
   Wdrożone i zacommitowane na stałe na RPi i minikube — nie tymczasowy eksperyment.
   **Pułapka po drodze**: `minikube image load` (nawet z domyślnym `--overwrite=true`) potrafi cicho **nie**
   odświeżyć zawartości tagu `:local` w wewnętrznym cache Dockera minikube, mimo zgłoszenia sukcesu — appka w
   podach dalej używała pliku jara sprzed całej sesji (`ls -la /app/` pokazywał starą datę), fałszywie sugerując że
   fix nie działa. Fix: `minikube image rm <tag>` (jawnie, ignorować błąd "must force" o kontenerach wciąż
   używających starego obrazu — to tylko potwierdza że stare kontenery żyją) → `minikube image load <tag>` ponownie
   → `kubectl rollout restart` żeby pody faktycznie przeszły na nowy obraz. Zawsze weryfikować `ls -la /app/` w
   świeżym podzie po `minikube-image` + redeploy, nie ufać samemu komunikatowi sukcesu skryptu.

   **[ZROBIONE, 2026-09-21] Trwały load balancer na minikube, bez `minikube tunnel`.** Load test przez
   `kubectl port-forward` (`localhost:8080`) zawsze trafia w **jeden** konkretny pod (wybrany raz, przy starcie
   forwarda) — realny problem, gdy chce się przetestować rozkład ruchu na repliki. `minikube tunnel` w tle
   (background wrapper przez `sudo -n`) okazał się niedziałający — `minikube tunnel` osobno woła `sudo` dla
   **każdego** serwisu z portem uprzywilejowanym (80), nie raz na starcie, więc nie da się tego bezpiecznie
   owinąć jednym nieinteraktywnym `sudo` (próba i wycofanie tego samego dnia, patrz git log). Zamiast tego:
   `minikube start --ports=30080:30080` (driver Docker publikuje port kontenera na hosta **trwale**, przy
   tworzeniu kontenera — bez sudo, bez tunelu, bez terminala trzymanego otwartym) + własny, jawny `NodePort`
   Service `app-candidates-lb` (`k8s-cluster/manifests/overlays/minikube/nodeport-candidates.yaml`, `nodePort: 30080`
   na sztywno) — **nie** Service generowany automatycznie przez Gateway (`cilium-gateway-api-gateway`), bo ten
   dostaje losowy nodePort przy każdym odtworzeniu, nie da się go przypiąć do stałej wartości `--ports`.
   `--ports` działa tylko przy tworzeniu kontenera — wymaga `minikube delete` + rebuild, nie da się dodać do już
   działającego minikube. Zweryfikowane żywe: różne pody dostają ruch przy kolejnych requestach (nie ten sam pod
   w kółko jak przy porcie 8080).

1. **[ZROBIONE] `prometheus-adapter` (2026-09-20).** Zainstalowany (`helm install`, po jednej blokadzie classifiera
   "Cluster-Wide Workload Creation" — zadziałało na "rób sam"), na `platform-1`. `hpa.yaml` (`candidates`) na RPS
   (`external` metric `candidates_requests_per_second`, target `AverageValue: 500`, `maxReplicas: 3`,
   `behavior.scaleDown.stabilizationWindowSeconds: 300` — patrz komentarz w pliku dla uzasadnienia liczb),
   zweryfikowane żywe: `kubectl -n candidates get hpa app-candidates` pokazuje realny `TARGETS` (nie `<unknown>`).
   **Gotcha znaleziona live**: pierwsza wersja `values-prometheus-adapter.yaml` (bez `resources.namespaced: false`
   w regule `external`) crashowała na każdym query z `unable to convert resource namespaces into label: no generic
   resource label form specified for this metric` — `http_server_request_duration_seconds_count` nie ma etykiety
   `namespace` (przychodzi przez OTel remote_write, tylko `job`+`instance`), a adapter domyślnie próbuje ją tam
   wstrzyknąć dla każdego external metric query (namespaced API). Fix: `resources: {namespaced: false}` w regule —
   patrz `docs/externalmetrics.md` w repo `kubernetes-sigs/prometheus-adapter` ("Cross-Namespace or No Namespace
   Queries"), bezpieczne tu bo `job="app-candidates"` w `seriesQuery` już jest jednoznaczne.

2. **[BLOKOWANE na Cilium, obejście na miejscu] `app-candidates` HPA spięty na sztywno 3 repliki (2026-09-20).**
   **Problem**: świeży pod po HPA scale-up (2→3) dostaje pełny udział ruchu natychmiast po przejściu
   `readinessProbe` — braku LB slow-startu (patrz punkt niżej, ten sam dzień). Żywy test 1500rps złapał
   konsekwencję wprost: p99 zapytań do bazy na świeżym podzie skoczyło z bazowych ~5ms do 150-215ms na ~60-90s
   (30-40x wolniej), co przy tej samej liczbie requestów/s (prawo Little'a: concurrency = arrival_rate ×
   czas_obsługi) zapchało pulę połączeń Hikari i wygenerowało realne 500-tki (`HikariPool-1 - Connection is not
   available`, `waiting=124` przy puli 40). Powtórzyło się dwa razy tego samego dnia mimo kolejnych łatek.
   **Próbowane, obie wdrożone, żadna nie wyeliminowała problemu w 100%:**
   - Hikari `maximum-pool-size`/`minimum-idle` 30 → 40 + Postgres `max_connections` 100 → 150
     (`application.yml`/`postgres.yaml`) — pomogło częściowo, ale przy prawdziwym buście (164 równoległych
     żądań na jednym podzie) i tak się zapchało.
   - `readinessProbe.initialDelaySeconds` 20 → 60 (`app.yaml`) — daje JVM więcej czasu w spokoju, ale nie
     rozgrzewa JIT-a naprawdę (to wymaga realnego ruchu) — user ocenił jako niewystarczające po kolejnym teście.
   **Decyzja (na jawne polecenie)**: zamiast dalej łatać objawy, `hpa.yaml` przestawiony na sztywno
   `minReplicas: 3, maxReplicas: 3` — bez zdarzeń skalowania nie ma świeżego, zimnego poda do zalania ruchem,
   więc cała klasa problemu znika. `prometheus-adapter`/metryka RPS zostają żywe (nieużywane do realnego
   skalowania), żeby odblokowanie później było zmianą jednej linijki, nie przebudową od zera.
   **Prawdziwy fix wymaga Cilium**: potwierdzone tego samego dnia — Cilium generuje Envoy Cluster dla Gateway
   API wewnątrz `cilium-operator`, bez hooka dla `slow_start_config`. Najbliższy odpowiednik, otwarty i
   niezmergowany CFP na per-service circuit breaking
   ([cilium/cilium#43532](https://github.com/cilium/cilium/issues/43532)), wymagał od autora forka
   `cilium-operator`. **Czekamy aż to (albo odpowiednik dla slow-startu) wyląduje w Cilium** — wtedy odblokować
   `minReplicas` z powrotem do 2. Do tego czasu `app-candidates` zajmuje na stałe 3 z 5 workerów (razem z
   2 od `app-job-offers` — wszystkie 5 workerów permanentnie zajęte, zero wolnego node'a).

3. **[ZROBIONE, w tym fizycznie] `platform-2` → `worker-5` (2026-09-20).** Ten sam powód co wcześniej: MetalLB w
   trybie L2 jest active-passive per IP — tylko `platform-1` faktycznie obsługuje ruch Gateway (`.100`), `platform-2`
   przy 1200rps stał bezczynny (~1.8% CPU), failover przestał być wart dedykowanego node'a.
   **Zmiana decyzji względem wcześniejszej wersji tej notatki**: pierwotny plan `sso-1`+Keycloak (ten sam dzień)
   odłożony — user zdecydował zamiast tego trzymać ten Pi jako generyczny spare/worker ("1 wolny rpi do dyspozycji,
   jak coś będzie kuleć"), a nie od razu wiązać go z konkretnym przyszłym serwisem. Keycloak/SSO wraca jako pomysł
   bez przypisanego node'a — gdy się pojawi, doprecyzować wtedy które ryzyka z poprzedniej wersji tej notatki
   (Keycloak/Quarkus introspection jako potencjalny nowy bottleneck przy ~1500rps, real SSO nie lokalna walidacja
   JWT) nadal obowiązują.
   **Zrobione (config + fizycznie, na jawne polecenie "rób sam"):** `inventory.ini` (`k8s-rpi-platform-2` →
   `k8s-rpi-worker-5`, IP przenumerowane `.3` → `.14` — user zaktualizował rezerwację DHCP w Omada),
   `l2-advertisement.yaml` (nodeSelector Gateway zostaje tylko na `platform-1`, zaaplikowane live), `CLAUDE.md`
   (tabela taintów) — oraz cordon+drain `k8s-rpi-platform-2`, `kubeadm reset` przez SSH, reboot (żeby złapał nowe
   DHCP `.14` — stary known_hosts wpis dla `.14` z poprzedniego życia tego IP trzeba było usunąć,
   `ssh-keygen -R`), `kubectl delete node k8s-rpi-platform-2`, `make k8s-prep` (hostname → `k8s-rpi-worker-5`),
   `make k8s-init` (join jako worker, bez taintu — zweryfikowane: `Taints: <none>` po tym jak Cilium wystartował).
   **Przy okazji zrobione też (ten sam dzień, ta sama sesja):** HPA dla `app-candidates` — patrz punkt 1. powyżej
   dla finalnej, RPS-owej wersji (pierwsza wersja była na CPU, zamieniona po tym jak realny load test pokazał że
   próg 70% ledwo nie został przekroczony i się nie wyzwolił). `worker-5` to jedyny wolny slot dla 3. repliki dzięki
   istniejącej twardej podAntiAffinity, brak jawnego nodeSelectora. `load-background` przeniesiony z
   `observability-2` (był najbardziej obciążonym node'em observability po tym jak `observability-3` odciążył `-1`
   ale nie `-2`) na `platform-1`. Naprawiony też niezależny bug: literówka IP w `deploy-load-background.sh`
   (`REGISTRY_RE` miał `.104` zamiast `.190`) przez co pinning tagu w manifeście od dawna cicho nic nie robił.
   Affinity entity-operatora Kafki (`kafka-cluster.yaml`) też dociągnięta i zaaplikowana — patrz
   `k8s-cluster/RPS-SCALING.md`/git log dla szczegółów tego osobnego fixu.
4. **[NASTĘPNA SESJA, kandydat #1 — na jawne polecenie 2026-09-20] Zwiększenie wolumenu danych w Postgresach
   (candidates/job-offers), bez konkretów jeszcze.** Obecny wolumen: 100k candidates / 50k job offers, generowany
   przez `data-generator` i ładowany przez `load-data.sh`/`make k8s-reload-data`. Cel/docelowa skala nieustalone w
   tej sesji — do doprecyzowania z użytkownikiem, kiedy przyjdzie pora (nie zgadywać liczb). **Kandydat #2
   (alternatywa, nie oba naraz)**: problem estymacji percentyli (`README.md`'s Known issues —
   `histogram_quantile()` na rzadkim ogonie histogramu daje niedokładne p99 względem Gatlinga, np. zmierzone
   2026-09-20: Grafana ~900ms vs realne p99=556-1576ms w zależności od runu). Keycloak/SSO (patrz punkt 3. wyżej)
   zostaje świadomie za oboma tymi kandydatami — bez node'a, bez terminu.
5. **`registry`/`local-path-provisioner`/`metallb-controller` dryfują na generyczne workery zamiast trzymać się
   `platform-1`.** Znalezione 2026-09-20: `registry.yaml` ma tolerancję `role=platform`, ale brak `nodeSelector`
   (tolerancja tylko pozwala, nie wymusza); `local-path-provisioner`/`metallb-controller` nie mają nawet tolerancji.
   Efekt: te pody konkurują o CPU z appkami na workerach (dokładnie ten typ współdzielenia, który
   `RPS-SCALING.md` fix #9 już raz nazwał błędem). Do naprawy przy okazji reorganizacji platform/sso (pkt 1).
6. **HA/replikacja Postgresa (CloudNativePG/Patroni)** — `local-path-provisioner` trzyma PV lokalnie na dysku
   node'a, więc samo dopuszczenie schedulowania na oba node'y bazodanowe nic nie da przy awarii. Potrzebny operator
   ze streaming replication. Odłożone, niepriorytetowe.
7. **GitOps (ArgoCD/Flux)** — cel końcowy, żeby stan klastra był w pełni odtwarzalny z repo bez ręcznych
   `helm install`/`kubectl apply`. Świadomie na końcu planu, nie teraz.
8. **Lokalny k8s (minikube) do testowania manifestów przed wdrożeniem na fizyczny klaster — ZROBIONE i
   zmergowane** (branch `experiment/minikube-local-cluster`, `make minikube-rebuild-all`). Otwarty tylko drobny
   punkt: trzymać w sync ewentualne przyszłe zmiany registry/MinIO między overlayem minikube a produkcyjnymi
   manifestami (już raz się rozjechały, patrz pkt niżej o pułapkach).
9. **Brak trwałego zabezpieczenia przed rozjazdem `candidatesDataFile` (lokalny plik dla `load-test`) vs. baza
   faktycznie załadowana na klastrze.** `load-data.sh` po każdym imporcie synchronizuje `load-background`, ale nic
   nie pilnuje pliku używanego ręcznie do `make candidateSimulation` — do rozważenia: osobny plik/katalog dla
   "danych aktualnie na klastrze" albo krok w symulacji weryfikujący próbkę ID przed testem.
10. Drobne, niepriorytetowe: rozszerzenie `HTTPRoute` o kolejne reguły/serwisy; weryfikacja dostępu do Gateway z
   innych maszyn w LAN; dashboard I/O dysku dla Postgresa (metryki node-exportera już są, brak paneli);
   `postgres-exporter` (metryki natywne Postgresa — connections/cache hit/locki) nie wdrożony, świadomie odłożone.
11. **Commit bieżących zmian** — sprawdzić `git status` na starcie kolejnej sesji; w chwili pisania tej wersji
    handoffu working tree jest czyste (wszystko z poprzednich sesji już zacommitowane/zmergowane), ale kilka
    wcześniejszych wpisów w historii tego pliku opisywało niezacommitowane zmiany — zawsze weryfikować `git status`
    zamiast ufać starym zapiskom.

## Kluczowe pułapki / lekcje (żeby nie powtórzyć błędu)

### Automatyzacja / bootstrap
- **`GatewayClass "cilium"` nigdy nie był tworzony przez żaden skrypt** — istniał na klastrze tylko dzięki
  zapomnianemu ręcznemu krokowi sprzed pełnej automatyzacji. Ujawnione dopiero przy pełnym `kubeadm reset` +
  rebuild (2026-09-20): Gateway wisiał w `Pending`, MetalLB nie miał czego ogłaszać. **Fix na stałe**: nowy
  `k8s-cluster/manifests/cilium/gatewayclass.yaml` + krok w `bootstrap.sh` zaraz po instalacji Gateway API CRDs.
- **`podAntiAffinity` bez jawnego `namespaces` w `labelSelector` widzi tylko własny namespace** — cross-namespace
  reguła (np. `app-candidates` unikający `app-job-offers` w innym namespace) wygląda poprawnie skonfigurowana, ale
  cicho nigdy się nie odpala. Zawsze dopisywać jawną listę `namespaces`.
- **Twardy `podAntiAffinity` + ciasny budżet node'ów wymaga `maxSurge: 0`** w strategii rollout — domyślny
  `maxSurge: 25%` próbuje odpalić dodatkowy pod przy każdym rollout; z dokładnie jednym node'em na replikę nie ma
  dla niego miejsca i rollout się zakleszcza, aż ktoś ręcznie usunie stary pod.
- **`bootstrap.sh`: `kubectl apply -f <katalog>` na katalogu z `kustomization.yaml` w środku wybucha** (próbuje
  sparsować plik kustomize jako zwykły manifest) — wymieniać pliki po nazwie, nie aplikować całego katalogu.
- **`load-data.sh --force` kasował demo-seed Flywaya bezpowrotnie** — `TRUNCATE ... CASCADE` przed bulk-importem
  kasował też wiersze zasiane przez migrację Flyway, która nigdy się nie powtórzy (`flyway_schema_history` już ją
  oznaczył jako wykonaną). Fix: po truncate, przed importem, skrypt teraz odpala realny plik migracji
  (`V1_1__demo-data.sql`) przez `psql -f`, nie kopię jego treści.
- **Lokalny `candidatesDataFile` łatwo rozjeżdża się cicho z bazą na klastrze** — Gatlingowy check
  (`status().in(200, 404)`) traktuje 404 jako sukces, więc test na nieaktualnych ID daje fałszywe "0% KO", nigdy
  faktycznie nie dotykając realnego matchingu. Zawsze weryfikować próbkę ID curl-em przed zaufaniem wynikom
  load-testu (patrz też pamięć `feedback_verify_loadtest_data`).
- **Eksperymentalne tagi obrazów budowane ręcznie (`docker build`/`push` z palca) nie przetrwają rebuildu
  rejestru** — commitować kod ZANIM się go długo testuje na klastrze, żeby `make k8s-deploy` zawsze był awaryjnym
  wyjściem po utracie rejestru.

### containerd / Ansible
- **`config_path` w containerd z dwiema ścieżkami rozdzielonymi dwukropkiem cicho psuje pull przez CRI/kubelet**,
  mimo że config wygląda poprawnie w `containerd config dump`. Kubelet i tak leci po HTTPS, ignorując
  `hosts.toml`, bez żadnego logu o próbie odczytu. Fix: `config_path` jako pojedyncza ścieżka
  (`/etc/containerd/certs.d`). **`ctr images pull` jest bezużyteczne do testowania tego problemu** — nie czyta w
  ogóle ustawień CRI, daje fałszywe potwierdzenia. Testować przez realny `kubectl delete pod` + obserwację.
- Dopisanie starego inline `[registry.mirrors]`/`[registry.configs]` OBOK `config_path` zabija cały plugin CRI
  (`"mirrors" cannot be set when "config_path" is provided`) — nie mieszać obu stylów configu.
- **Po power-cyklu całego klastra static pody control-plane na masterze (`etcd`, `kube-apiserver`,
  `kube-controller-manager`, `kube-scheduler`) mogą wisieć w Headlamp/`kubectl get pods` jako
  `CreateContainerError` z `"failed to reserve container name ... is reserved for <id>"`, mimo że realne procesy
  za `<id>` **już działają** (żywy PID w `ctr -n k8s.io tasks list`, `kubectl` normalnie odpowiada). To fałszywy
  status, nie realna awaria: kubelet po restarcie gubi synchronizację z containerd i w kółko próbuje utworzyć
  nowy kontener pod tą samą nazwą/numerem próby, za każdym razem obijając się o rezerwację trzymaną przez
  kontener, który już poprawnie wystartował. **`crictl` nie jest zainstalowany na tych node'ach — używać `ctr`
  bezpośrednio** (`sudo ctr -n k8s.io containers list` / `tasks list`). Samo `systemctl restart kubelet` NIE
  rozwiązuje problemu. Fix: dla każdego z 4 ID z komunikatu błędu — `sudo ctr -n k8s.io tasks kill -s SIGKILL <id>`
  → `sudo ctr -n k8s.io tasks rm <id>` → `sudo ctr -n k8s.io containers delete <id>` — zwalnia rezerwację nazwy,
  kubelet od razu tworzy czysty kontener (parosekundowa przerwa w apiserverze, akceptowalna na single-masterowym
  klastrze bez HA). **Powtórzyło się identycznie przy KOLEJNYM power-cyklu tego samego dnia (2026-09-21,
  ~1h później)** — to nie jednorazowy fluk, trzeba to robić po każdym power-cyklu całego klastra, dopóki nie
  znajdzie się trwały fix (nieznaleziony jeszcze; podejrzenie: coś w kolejności/timingu boot-time między
  containerd a kubelet na tym sprzęcie/OS). Fix identyczny za drugim razem (nowe ID kontenerów, sama procedura).
  **Efekt uboczny do sprawdzenia przy każdym takim incydencie**: `cilium-operator` może wpaść w
  `CrashLoopBackOff` przez utratę leader election w oknie, gdy apiserver był niedostępny (`"Leader election
  lost, shutting down"` w jego logach, `dial tcp <master>:6443: connect: connection refused`) — samo się nie
  naprawia od razu przez rosnący backoff kubeleta; `kubectl delete pod` na nim (Deployment go odtworzy) jest
  szybsze niż czekanie na kolejny backoff. Sprawdzać `kubectl get pods -A | grep -v Running` całościowo po
  naprawie control-plane, nie tylko 4 static pody na masterze.

### MetalLB / Gateway / sieć
- MetalLB w trybie L2 jest **active-passive per IP** — tylko jeden node ogłasza dany adres ARP-em na raz, drugi
  node w puli nie dokłada przepustowości, tylko przejmuje przy awarii. Nie zakładać, że dodanie drugiego node'a do
  `L2Advertisement` daje load-balancing.
- Domyślne chart-owe DaemonSety (MetalLB speaker, node-exporter, itp.) mają **zaszyte na sztywno** tolerancje tylko
  dla `control-plane`/`master` — trzeba jawnie dopisywać tolerancje dla własnych taintów (`role=database`,
  `role=observability`, `role=platform`) w values, inaczej DaemonSet nie wstanie na tainted node'ach. Przy
  dodawaniu nowego taintu — pamiętać o dopisaniu tolerancji do **wszystkich** DaemonSetów (Cilium, cilium-envoy,
  Alloy, node-exporter, metallb-speaker), inaczej cicho przestają pokrywać nowy node.
- Historyczny wielotygodniowy problem "TTL exceeded co ~10 min" (spike'i HTTP, `Handler timeout` na apiserverze)
  okazał się być w warstwie fizycznej sieci Omada (switch/router/kontroler), nie w k8s — naprawiony aktualizacją
  firmware + nową anteną + pełnym restartem klastra. Jeśli podobny regularny, kilkuminutowy cykl anomalii kiedyś
  wróci: sprawdzać fizyczną sieć (ping do bramy, dowody pakietowe) RÓWNOLEGLE z warstwą k8s, nie zakładać z góry że
  to appka/Cilium/apiserver.

### Observability / Tempo / dane
- **Tempo 3.x w trybie mikroserwisowym (`tempo-distributed`) wymaga Kafki** — to twardy wymóg architektury, nie
  opcja do wyłączenia. Monolityczny single-binary chart Tempo utknął na appVersion 2.10.8 i nigdy nie dostanie
  configu pod schemat Tempo 3.x (`app.Config` przebudowany od zera) — jedyna droga do Tempo 3.x to `tempo-distributed`.
- **`storage.trace.backend: local` w `tempo-distributed` nie działa wielopodowo** — każdy pod montuje osobny
  `emptyDir`, bloki zapisane przez jeden komponent są niewidoczne dla innych. Wymaga współdzielonego backendu S3
  (MinIO).
- **MinIO zniknęło z Docker Hub** (usunięte całkowicie we wrześniu 2026) — obrazy `minio/minio`/`minio/mc` trzeba
  ciągnąć z `quay.io/minio/...`, ten sam tag. **Już naprawione w produkcyjnym `k8s-cluster/manifests/minio/minio.yaml`**
  (zweryfikowane 2026-09-20: oba kontenery na `quay.io`).
- **Grafana `datasource.jsonData.timeInterval` musi zgadzać się z realnym Prometheus `scrape_interval`** — jeśli
  się rozjadą (np. `timeInterval=1s` przy `scrape_interval=1m`), `$__rate_interval` może skolabsować do okna
  mniejszego niż odstęp między próbkami i panele `rate()` pokazują "No data", mimo że surowe metryki i pipeline są
  zdrowe. Oba muszą się zmieniać razem.
- **Panele CPU z hardkodowanym oknem `rate(...[5m])` rozmywają krótkie load testy** — przy 1-2 minutowym teście
  suma/rozkład CPU na dashboardzie może wyglądać spokojnie, mimo że node jest faktycznie w 100%. Sprawdzać
  `jvm_cpu_recent_utilization_ratio` (prawie natychmiastowy) lub `kubectl top`/Headlamp zamiast ufać szerokiemu
  oknu. Wszystkie dashboardy w tym repo już używają `$__rate_interval` (self-adjusting) zamiast hardkodowanego okna
  — preferować to podejście w każdym nowym panelu.
- **Tempo query-frontend przy małym `limit` i szerokim zakresie czasu może nie doczekać się wkładu `live-store`** —
  search dzieli się na równoległe joby (backend/blocklist vs. live-store) i zwraca wynik, gdy zbierze `limit`
  trafień, nie czekając na wszystkie joby. Przy domyślnym `limit=20` w Grafana Explore świeże trace'y (<15 min)
  potrafią być niewidoczne, mimo że `live-store` ma je aktualne co do sekundy. To zachowanie Tempo 3.0.3, nie bug
  konfiguracji — obejście: podnieść pole "Limit" w Search Options do ≥100 przy sprawdzaniu świeżości trace'ów.
- **Zegary node'ów bez lokalnego NTP dają fałszywą przyczynowość w trace'ach** (dziecko-span startujący przed
  rodzicem) — `systemd-timesyncd` z domyślnym publicznym NTP ma zbyt duży jitter (13-48ms) i zbyt rzadkie
  odpytywanie (do 34 min). Fix: lokalny serwer NTP (`chrony`) na jednym z node'ów klastra dla całej podsieci —
  jitter w LAN to ułamki ms. Pułapka po drodze: `chronyd` w trybie serwera wymaga **jawnego `port 123`** w
  configu, inaczej w ogóle nie bindował gniazda serwerowego mimo że wygląda poprawnie.
- **Synchroniczny `ConsoleAppender` Logbacka pod obciążeniem potrafi (przypadkiem) działać jak rate-limiter** —
  wszystkie wątki Tomcata czekające na wspólny lock zapisu logu ograniczają realny throughput, ale też chronią
  appkę przed wyczerpaniem puli wątków przez prawdziwą pracę. Po przejściu na `AsyncAppender` ten "przypadkowy
  bezpiecznik" znika — trzeba osobno zadbać o realne zabezpieczenie (np. rozdzielić port `/actuator/health` od
  portu biznesowego, żeby liveness probe nie umierał razem z nasyconą pulą Tomcata pod realnym obciążeniem).
- **Grafana timeseries panel `lineInterpolation: "smooth"` wizualnie kasuje realną losowość danych** — spline
  interpolacja zaokrągla ostre skoki punkt-po-punkcie w gładką krzywą, więc dashboard może wyglądać "za spokojnie"
  mimo że dane w Prometheusie są poprawnie ziarniste. Znalezione 2026-09-20: 12 paneli (`basic-http-monitoring.json`,
  `jvm-monitoring.json`) miało to ustawienie, przez co losowy ruch z Gatlinga wyglądał na dashboardzie jak gładka
  obwiednia zamiast postrzępionej linii. **Fix: `"linear"` na każdym nowym timeseries panelu**, chyba że wygładzanie
  jest świadomym wyborem — dashboard obserwowalności ma pokazywać prawdziwe zachowanie systemu, nie estetykę.
- **Grafana datasource `timeInterval` jest podłogą rozdzielczości dla KAŻDEGO zapytania na tym datasource, nie
  tylko dla `rate()`/`$__rate_interval`.** To była prawdziwa (i jedyna faktycznie ważna) przyczyna "znikniętej piły"
  na panelu "Heap Memory" — nie miała nic wspólnego z Prometheus `scrape_interval`. JVM metryki (`jvm_memory_used_bytes`
  i inne) w tym projekcie idą przez agenta OTel jako **push co 1s** (`OTEL_METRIC_EXPORT_INTERVAL=1000` w
  `k8s-cluster/manifests/{candidates,job-offers}/app.yaml`) prosto do Prometheusa przez `remote_write` —
  `scrape_interval` Prometheusa (15s) ich w ogóle nie dotyczy, bo to nie jest pull. Zweryfikowane bezpośrednio:
  zapytanie do Prometheusa z `step=1` zwróciło podręcznikową piłę GC (357→416→**380** spadek→450→**347** spadek→...,
  MB), więc dane 1-sekundowe realnie tam siedziały. Mimo to panel w Grafanie renderował płaski/poszarpany zygzak,
  bo `timeInterval: "15s"` na datasource Prometheusa (ustawiony świadomie dla poprawnego `$__rate_interval` na
  panelach RPS/error — patrz wyżej) działa też jako "Min interval" dla zwykłych zapytań gauge, więc Grafana i tak
  próbkowała co 15s zamiast co 1s, gubiąc ~14 z 15 punktów na sekundę. **Fix: `"interval": "1s"` na poziomie
  panelu** (Heap Memory, Non-Heap Memory, Memory Used by Pool w `jvm-monitoring.json`) — nadpisuje floor tylko dla
  tych trzech paneli, nie rusza globalnego `timeInterval` (które musi zostać 15s dla `$__rate_interval` gdzie
  indziej). Osobno usunięte też sztywne `"min": 0` na osi Y tych samych paneli (ściskało wahanie Heap Used ~330-440MB
  do ~15% wysokości wykresu na tle Heap Max ~780MB) — to poprawka komplementarna, nie substytut fixu interwału.
  **Lekcja ogólna**: zanim zmienisz oś/interpolację żeby "odsłonić" ukryty sygnał, sprawdź surowym zapytaniem z
  małym `step` czy dane o wyższej rozdzielczości w ogóle są w Prometheusie — jeśli tak, winny jest limit
  rozdzielczości zapytania (datasource `timeInterval`/panel `interval`), nie wizualizacja.
- **Panel Loki bez cache'a przelicza całą historię od zera przy każdym odświeżeniu** — `count_over_time` po
  wszystkich serwisach na oknie 30 min potrafi skanować >1.5 mln linii/500MB (potwierdzone przez `stats.summary` w
  odpowiedzi Loki), dając 5-6s czasu ładowania nawet na spokojnym ruchu, więcej po burst teście. Fix (2026-09-20,
  `values-loki.yaml`): włączony **embedded cache** Loki (`query_range.results_cache`/`chunk_store_config.chunk_cache_config`,
  `max_size_mb: 50` każdy) — działa w tym samym procesie/podzie, bez nowej infrastruktury. **Nie mylić z chart-owym
  `resultsCache`/`chunksCache: enabled: true`** — to osobna, cięższa opcja, która deployuje dedykowany memcached
  (świadomie wyłączona w tym repo jako "extras not needed for home-lab"). Zmierzony efekt: powtórzone zapytanie na
  nakładający się zakres 6s→0.13s. Limit pamięci Loki podniesiony `1Gi→1280Mi` przy okazji, żeby zachować ten sam
  margines na burst testy co przed dodaniem cache'u.

### Load-testing / metodologia wydajności
- **Testować jedną zmienną na raz pod realnym współbieżnym obciążeniem** — bundlowana zmiana dwóch rzeczy naraz
  potrafi zamaskować, że jedna z nich jest katastrofalna a druga to czysty zysk (patrz historia w
  `RPS-SCALING.md`). Pojedynczy `EXPLAIN ANALYZE` czy request nie przewiduje zachowania pod współbieżnością.
  Więcej równoległości (większy connection pool, więcej parallel workerów Postgresa, więcej replik) nie zawsze
  pomaga, jeśli prawdziwy limit jest gdzie indziej — tylko przesuwa kolejkowanie w gorsze miejsce.
- **Jeden JVM/Postgres na fizyczny node RPi5 to wymóg poprawności, nie optymalizacja** — współdzielenie node'a
  przez dwie CPU-głodne appki dało kiedyś 87/13 split obciążenia i spiralę (throttling → wolniejsza replika →
  Envoy round-robin per-połączenie nierówno rozkłada ruch → jeszcze wolniej), kończącą się realnymi błędami przy
  RPS, który powinien być czysty.
- **Krótkie load testy** (`stepDuration=1m` wystarczy) — długie sustained testy przy wysokim RPS to właśnie to, co
  potrafi zabić node observability (nadmiar trace'ów/logów/metryk), nie sam pomiar.
- Gatling: `.shareConnections()` w `httpProtocolBuilder()` jest wymagane powyżej ~300rps — bez tego klient (Mac)
  wyczerpuje własną pulę portów efemerycznych szybciej niż zwalniają się z `TIME_WAIT`, dając fałszywe błędy po
  stronie klienta, zanim klaster w ogóle zbliży się do własnego limitu.
- Masowy restart całego klastra zawsze daje kilkanaście-kilkadziesiąt minut degradacji (JIT warmup appek + Cilium/
  CNI reconciliation) — nie traktować pierwszego load testu po restarcie jako miarodajnego pomiaru capacity.
