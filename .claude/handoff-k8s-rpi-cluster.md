# Handoff: klaster k8s na RPi5

Skondensowany handoff — stan na 2026-09-24. Pełna narracja diagnostyczna (sesja po sesji, ze ślepymi zaułkami)
żyje w historii gita tego pliku (`git log -p -- .claude/handoff-k8s-rpi-cluster.md`) — zakończone punkty są
stąd usuwane, nie archiwizowane w treści.

## Obecny stan (skrót)

12/12 node'ów fizycznych w klastrze, `kubeadm` (nie k3s), Cilium (CNI + Gateway API, bez kube-proxy), MetalLB (L2),
`local-path-provisioner`. Oba mikroserwisy (`app-candidates` x3 / `app-job-offers` x2, jedna replika na node) +
ich Postgresy wdrożone, dane załadowane (100k candidates / 50k job offers, `data-generator`). Stack observability:
Prometheus/Loki/Grafana/Headlamp/Hubble + Tempo w architekturze `tempo-distributed` (Kafka+MinIO). Ambient load
(`load-background`, k6) działa 24/7. Tabela node taintów/IP jest w `CLAUDE.md` (nie duplikować tutaj).

**Zmierzony sufit RPS: 2000rps czyste przez ~10 min** (2026-09-24: 1,32M requestów, 0 KO, p99 202 ms), po
przepisaniu `findCandidateMatchIds` w `app-job-offers` na natywne SQL — Hibernate nie cache'uje tłumaczenia
HQL→SQL dla zapytań z `IN :lista` i robił je przy każdym requeście (~15% CPU job-offers). Przy 2000rps nic nie
jest na limicie (job-offers 2,16/3 rdzenie, candidates 1,8/3, Postgres job-offers 1,6/3), więc następny sufit
jest niezmierzony. Pełna historia i tabele: `k8s-cluster/RPS-SCALING.md` (#13 i "Current state") — czytać przed
każdą pracą nad skalowaniem, nie duplikować tutaj.

## TODO / Next steps (w kolejności priorytetu)

1. **[NASTĘPNY KROK] Appki eksportują 100% spanów, odrzuca dopiero `otel-collector`.** Tail sampling (1% +
   >500 ms + 4xx/5xx, `k8s-cluster/manifests/observability/values-otel-collector.yaml`) działa w collectorze, ale
   `OTEL_TRACES_SAMPLER` nie jest ustawiony w appkach (domyślnie `parentbased_always_on`) — agent w każdym JVM
   tworzy, serializuje i wysyła po gRPC każdy span każdego requestu (kontroler, JDBC, Feign), a 99% z nich
   collector wyrzuca. Zmierzone JFR-em 2026-09-24 przy 1200rps: agent + eksport spanów (`BatchSpanProcessor`,
   okhttp) to **~5% CPU** `app-job-offers`; do tego ruch sieciowy i CPU/RAM samego collectora (limit 1 CPU,
   throttlowany do ~34% okresów przy 1800rps) oraz presja na `observability-1`/`-3` przy długich testach.
   **Pułapka**: zwykły head sampling (`traceidratio`) zepsuje tail sampling — wolne i błędne trace'y trzeba mieć w
   całości, a o tym, czy trace jest wolny/błędny, wiadomo dopiero na końcu. Do przemyślenia: co realnie zyskujemy
   (ile CPU w appkach vs. w collectorze), czy jest kompromis (np. head sampling z wyjątkami po stronie SDK, mniej
   spanów per trace — wyłączenie spanów kontrolera/JDBC tam, gdzie nic nie wnoszą), i jak to zmierzyć przed/po
   (CPU per request, patrz metodologia w `RPS-SCALING.md`).

2. **Odporność na power cycle — nadal wymaga ręcznej interwencji.** Stan na 2026-09-24:
   - **Naprawione i potwierdzone po power cyclach 2026-09-23**: wyścig metryki JVM CPU (flat 0) — `MeterFilter.deny`
     (`60cdd0b`) działa, wszystkie 5 instancji raportuje poprawnie po restarcie; CoreDNS obie repliki na jednym
     node'zie (`257dbc8`, twardy anti-affinity).
   - **Otwarte — `failed to reserve container name`** po każdym pełnym power cyclu (static pody control-plane i
     zwykłe DaemonSety w `CreateContainerError`) — procedura ręczna z `ctr` w "Kluczowe pułapki → containerd".
     Trwałego fixu brak. Przy okazji sprawdzać `cilium-operator` (utrata leader election → CrashLoopBackOff).
   - **Otwarte — appki startują zanim Postgres jest gotowy** → `HikariPool ... connection has been closed`,
     503 na health, kilka restartów podów appek (samo się leczy, ale głośno; widoczne w `RESTARTS` 3-5 po każdym
     power cyclu). Do zrobienia: `startupProbe`/`initContainer` czekający na Postgresa, albo sprawdzić czy to
     wolniejszy boot node'ów bazodanowych.
   - **Otwarte — `tempo-block-builder` OOM na zaległościach Kafki** po dłuższym przestoju (offset spada poniżej
     retencji, catch-up nie mieści się w RAM `observability-1`). Obejście: reset offsetu grupy `block-builder`
     `--to-latest` (procedura w git history tego pliku, 2026-09-22). Trwale: krótsza retencja topicu
     `tempo-traces`, albo automatyczny reset przy starcie.
   - Po każdym restarcie: `kubectl get pods -A | grep -v Running`, i pamiętać, że pierwsze kilkanaście minut to
     rozgrzewka (JIT, Cilium) — nie mierzyć wtedy capacity.

3. **`k8s-rpi-worker-2` padł sieciowo w trakcie load testu (2026-09-23 18:13:50 UTC), przyczyna niezbadana.**
   Kubelet przestał raportować, node nieosiągalny (100% packet loss), wrócił dopiero po ręcznym power cyclu. To
   **znany outlier**: Ubuntu 25.10 / kernel 6.17.0-1021-raspi, reszta floty Ubuntu 24.04.4 / 6.8.0-1064-raspi —
   kandydat na przyczynę, niepotwierdzony. Do zrobienia: `journalctl -b -1` z tego node'a (SSH
   `aarczynski@192.168.10.11`), rozważyć reinstalację na 24.04.

4. **`app-candidates` HPA przypięty na sztywno 3 repliki — blokowane na Cilium.** Świeży pod po scale-upie dostaje
   pełny udział ruchu od razu (brak LB slow-startu w Gateway API Cilium) i zimny JVM zapycha pulę Hikari / wybija
   circuit breaker Envoya. `prometheus-adapter` + metryka RPS zostają żywe, odblokowanie to zmiana jednej linijki
   w `k8s-cluster/manifests/candidates/hpa.yaml`, gdy Cilium dostanie `slow_start_config` (śledzić
   [cilium/cilium#43532](https://github.com/cilium/cilium/issues/43532) lub odpowiednik). Ten sam mechanizm zimnego
   startu dotyczy każdego rolloutu — po deployu zawsze rozgrzewka przed pomiarem (`RPS-SCALING.md` #13).

5. **Skoki opóźnień co ~40 s przy ~1500rps (zgłoszone 2026-09-23) — nie odtworzyły się.** Sonda w klastrze przy
   czystym 1500rps 2026-09-24 nie złapała ani jednego skoku; wszystko, co było wtedy wykluczone (klient, sieć, CPU,
   GC, Postgres, termika), jest w `RPS-SCALING.md` #12. Jeśli wróci: postawić sondę jeszcze raz — pod `busybox:1.36`
   na `observability-3` (toleration `role=observability`), pętle `wget` co ~50 ms na Service candidates, Gateway,
   `/actuator/health` candidates (port 8081, IP poda) i `app-job-offers` bezpośrednio, log `epoch uptime ms rc`
   (`busybox date` nie ma `%N` — mierzyć z `/proc/uptime`), ID kandydata z żywej bazy i z niepustym wynikiem.
   Interpretacja: health też staje → cała JVM/node; tylko ścieżki biznesowe → baza/Feign/downstream.

6. **Następny kandydat funkcjonalny (do wyboru z użytkownikiem, nie oba naraz)**: zwiększenie wolumenu danych w
   Postgresach (skala do ustalenia — nie zgadywać liczb) **albo** niedokładne p99 w Grafanie (`histogram_quantile()`
   na rzadkim ogonie vs. Gatling, `README.md` Known issues). Keycloak/SSO świadomie za nimi, bez node'a.

7. **`registry`/`local-path-provisioner`/`metallb-controller` mogą dryfować na generyczne workery** —
   `registry.yaml` ma tolerancję `role=platform`, ale brak `nodeSelector`; pozostałe dwa nie mają nawet tolerancji.
   Przy wszystkich 5 workerach zajętych przez appki to współdzielenie node'a, które `RPS-SCALING.md` #9 nazwał
   błędem. Dopiąć `nodeSelector` na `platform-1`.

8. **Brak zabezpieczenia przed rozjazdem `candidatesDataFile` vs. baza na klastrze** — `load-data.sh` synchronizuje
   `load-background`, ale nie plik do `make candidateSimulation`. Do rozważenia: krok w symulacji weryfikujący
   próbkę ID przed testem.

9. **minikube: `app-candidates-lb` (NodePort 30080) rozkłada ruch nierówno** — to L4 (połączenie przypięte do poda)
   vs. L7 Gateway (per request) plus `.shareConnections()` w Gatlingu. Opcje: zostawić, albo przypiąć 30080 do
   Service'u Gateway'a (generowany dynamicznie przez Cilium, losowy nodePort — wymaga `kubectl patch` po utworzeniu,
   nieprzetestowane czy przetrwa reconciliację).

10. **Docker Compose — niezweryfikowane na żywo**: fix OTel/Micrometer (`otel-metrics-filter` + `MeterFilter.deny`)
    i zmienna `Instance` na dashboardzie JVM (`host.name` nie jest ustawione w Compose — może wyjść hash kontenera
    albo pusto). Odpalić `docker compose up` i sprawdzić dashboard JVM.

11. Dalekie / niepriorytetowe: HA Postgresa (CloudNativePG/Patroni — `local-path` trzyma PV na dysku node'a),
    GitOps (ArgoCD/Flux), rozszerzenie `HTTPRoute`, dashboard I/O dysku Postgresa, `postgres-exporter` na k8s
    (świadomie tylko w Compose, `a2f37c7`), panel "Running Pods" liczący fazę zamiast gotowości kontenera.

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
- **`failed to reserve container name` po power cyklu dotyka też zwykłych DaemonSetów, nie tylko statycznych podów
  control-plane (2026-09-23).** Po dwóch power cyklach w jeden wieczór 11 podów (`cilium-envoy` x3, `cilium-operator`,
  `kube-proxy` x3, `node-exporter` x4) utknęło w `CreateContainerError` na `worker-3`, `worker-5`, `observability-1`,
  `observability-2`. Rozpoznanie: `kubectl get pods -A -o json` + filtr po `state.waiting.reason=="CreateContainerError"`
  i wyciągnięcie ID z `reserved for "<id>"`. Fix ten sam co dla control-plane (`ctr -n k8s.io tasks kill/rm` +
  `containers delete <id>`), tu wystarczyło samo `containers delete` (kontenery były tylko zarezerwowane, bez taska —
  "task not found" jest oczekiwane). SSH: user `aarczynski` (nie `adamarczynski`!), IP z `inventory.ini`. Kubelet potrafi
  zarezerwować nowe ID przy kolejnej próbie — po czyszczeniu sprawdzić jeszcze raz. Auto-mode classifier zablokował
  pierwszą próbę (SSH + `sudo ctr ... delete`), zadziałało po ponownej, jawnej zgodzie użytkownika w rozmowie.
- **Twardy `podAntiAffinity` (1 replika appki na node) + padnięty node = zastępczy pod `Pending` bez wyjścia** — brak
  wolnego node'a, więc `app-job-offers` pracuje na 1/2 replik do powrotu node'a. Ta sama pułapka dotknęła `registry`
  (PV `local-path` przypięty do node'a, który padł). Znane i akceptowane, ale pamiętać przy diagnozie "dlaczego
  pod nie startuje".
- **Po awarii node'a w trakcie load testu Gatling potrafi żyć dalej godzinami bez wysyłania niczego realnie do klastra**
  (2026-09-23: ruch do appek zamarł o 18:36 UTC, proces Gatlinga pisał log do 20:26 UTC — 89 MB — z klientowymi
  timeoutami 60 s na Gateway; ręczny curl w tym czasie odpowiadał w 8 ms). Hipoteza (niezweryfikowana):
  `.shareConnections()` trzyma martwe połączenia. Po awarii node'a w trakcie testu zabijać test i puszczać od nowa.
- **Zdarzenia Kubernetes (`kubectl get events`) wygasają po ~1 h** — po awarii szybko zapisać, co jest potrzebne;
  potem zostają tylko logi (Loki) i Prometheus.
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
- **Grafana "Running Pods" (`kubernetes-cluster-dashboard`) i Headlamp mogą się rozjeżdżać dla poda w
  CrashLoopBackOff — to nie bug dashboardu, tylko inna metryka.** Panel liczy
  `count(kube_pod_status_phase{phase="Running"} == 1)` — **faza poda**, nie gotowość kontenera. Pod w
  CrashLoopBackOff zostaje w fazie `Running` między restartami (kubelet go faktycznie odpala, tylko kontener zaraz
  pada), więc licznik go nie odejmuje. Headlamp czyta `containerStatuses[].ready` (per-kontener), co poprawnie
  pokazuje `false`. Znalezione 2026-09-22: `tempo-block-builder-0` crash-looping, Headlamp poprawnie pokazywał
  "1 down", Grafana dalej liczyła "111 up" (zgadza się z realną liczbą podów w klastrze — tylko "Running" nie
  znaczy "zdrowy"). Do poprawy kiedyś: `kube_pod_container_status_ready` zamiast `kube_pod_status_phase` złapałby
  ten przypadek, ale nie zrobione teraz (poza zakresem sesji).
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

### JVM / OTel javaagent
- **`OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap
  classpath has been appended` w logach `app-candidates`/`app-job-offers` przy starcie jest nieszkodliwy, spodziewany
  szum, nie błąd.** Standardowy efekt uboczny tego, jak działa `-javaagent:./opentelemetry-javaagent.jar` —
  agenty Javy wstrzykujące instrumentację na poziomie bootstrap classloadera używają
  `Instrumentation.appendToBootstrapClassLoaderSearch()`, co wyłącza korzyści CDS (Class Data Sharing) dla
  wszystkiego poza klasami samego boot loadera (wolniejszy start JVM o ułamki sekundy, nic więcej — nie dotyczy
  poprawności działania appki ani jakości eksportowanych metryk/traców/logów). Pojawia się na każdym starcie, na
  wszystkich środowiskach (Compose, k8s, minikube) — nie mylić z realnym problemem przy przeglądaniu logów pod
  kątem `warn`/`error` (dokładnie to zdarzyło się w tej sesji: złapane przez grep po "warn" przy skanowaniu logów
  `app-job-offers` w poszukiwaniu prawdziwej przyczyny innego problemu). Nic do zrobienia — nie próbować "naprawiać"
  przez usuwanie klas z bootstrap classpath, bo to zepsułoby samą instrumentację OTel.

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
