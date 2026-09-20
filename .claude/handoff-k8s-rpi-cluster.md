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

**Zmierzony sufit RPS: 1500 RPS bezpieczne, 1600 RPS na granicy.** 1500rps potwierdzone czyste (0% KO, p99=78ms)
2026-09-20 po hard `podAntiAffinity` + CPU limit 3, **ponownie potwierdzone po rebalansie `observability-2`→`-1`**
(load-background+Hubble przeniesione live, patrz niżej). 1600rps (`candidatesimulation-20260920143054799`) już
pokazuje wyraźnie dłuższe czasy odpowiedzi na dashboardzie HTTP Monitoring — traktować jako granicę, nie
bezpieczny poziom operacyjny. 1200rps potwierdzone też na pełnym 5-minutowym sustained hold, zero powtórki
`observability-1` `NodeNotReady`. Pełna historia fixów RPS (async logging, Postgres parallel workers off, split
query, anti-affinity, itd.) w `k8s-cluster/RPS-SCALING.md` — czytać przed każdą kolejną pracą nad skalowaniem, nie
duplikować tutaj.

## TODO / Next steps

1. **[NOWE] `platform-2` → `sso-1` + Keycloak/SSO (priorytet: niski, "za jakiś czas").** Zdecydowane 2026-09-20:
   MetalLB w trybie L2 jest active-passive per IP — tylko `platform-1` faktycznie obsługuje ruch Gateway (`.100`),
   `platform-2` przy 1200rps stał bezczynny (~1.8% CPU), więc failover przestał być wart dedykowanego node'a.
   `platform-2` idzie jako `sso-1` pod Keycloak (nowy taint, np. `role=sso:NoSchedule` — nazwa do ustalenia).
   **Ważny kontekst architektoniczny (ustalony w sesji, zapisać przy implementacji):**
   - To ma być **prawdziwe SSO, nie lokalna walidacja JWT**. Jedna appka (prawdopodobnie `app-candidates`) pobiera
     token, druga (`app-job-offers`) weryfikuje go przez wywołanie do Keycloaka — najpewniej **introspection
     endpoint**, nie lokalna walidacja podpisu.
   - **Ryzyko do zmierzenia PRZED integracją z candidates/job-offers**: pojedyncza instancja Keycloak na 1 RPi5
     obsługująca introspection przy docelowym ruchu ~1500rps to prawdopodobny **nowy bottleneck** — Keycloak/Quarkus
     ma cięższy koszt per-request niż appki tego projektu, a introspection to network round-trip + uwierzytelnienie
     confidential clienta przy każdym wywołaniu. Zmierzyć Keycloak w izolacji (Gatling bezpośrednio na
     `/introspect`) tą samą metodologią co reszta projektu (jedna zmienna na raz, pod realnym obciążeniem) zanim
     się to wpina do appek.
   - Do zrobienia razem z tą reorganizacją: `l2-advertisement.yaml` nodeSelector zostaje tylko na `platform-1`
     (świadoma utrata failoveru dla `.100`), zaktualizować `inventory.ini`/nazwę/IP.
2. **[NOWE] Zwiększenie wolumenu danych w Postgresach (candidates/job-offers) — priorytet: przyszłość, bez
   konkretów jeszcze.** Obecny wolumen: 100k candidates / 50k job offers, generowany przez `data-generator` i
   ładowany przez `load-data.sh`/`make k8s-reload-data`. Cel/docelowa skala nieustalone w tej sesji — do
   doprecyzowania z użytkownikiem, kiedy przyjdzie pora (nie zgadywać liczb).
3. **`registry`/`local-path-provisioner`/`metallb-controller` dryfują na generyczne workery zamiast trzymać się
   `platform-1`.** Znalezione 2026-09-20: `registry.yaml` ma tolerancję `role=platform`, ale brak `nodeSelector`
   (tolerancja tylko pozwala, nie wymusza); `local-path-provisioner`/`metallb-controller` nie mają nawet tolerancji.
   Efekt: te pody konkurują o CPU z appkami na workerach (dokładnie ten typ współdzielenia, który
   `RPS-SCALING.md` fix #9 już raz nazwał błędem). Do naprawy przy okazji reorganizacji platform/sso (pkt 1).
4. **HA/replikacja Postgresa (CloudNativePG/Patroni)** — `local-path-provisioner` trzyma PV lokalnie na dysku
   node'a, więc samo dopuszczenie schedulowania na oba node'y bazodanowe nic nie da przy awarii. Potrzebny operator
   ze streaming replication. Odłożone, niepriorytetowe.
5. **GitOps (ArgoCD/Flux)** — cel końcowy, żeby stan klastra był w pełni odtwarzalny z repo bez ręcznych
   `helm install`/`kubectl apply`. Świadomie na końcu planu, nie teraz.
6. **Lokalny k8s (minikube) do testowania manifestów przed wdrożeniem na fizyczny klaster — ZROBIONE i
   zmergowane** (branch `experiment/minikube-local-cluster`, `make minikube-rebuild-all`). Otwarty tylko drobny
   punkt: trzymać w sync ewentualne przyszłe zmiany registry/MinIO między overlayem minikube a produkcyjnymi
   manifestami (już raz się rozjechały, patrz pkt niżej o pułapkach).
7. **Brak trwałego zabezpieczenia przed rozjazdem `candidatesDataFile` (lokalny plik dla `load-test`) vs. baza
   faktycznie załadowana na klastrze.** `load-data.sh` po każdym imporcie synchronizuje `load-background`, ale nic
   nie pilnuje pliku używanego ręcznie do `make candidateSimulation` — do rozważenia: osobny plik/katalog dla
   "danych aktualnie na klastrze" albo krok w symulacji weryfikujący próbkę ID przed testem.
8. Drobne, niepriorytetowe: rozszerzenie `HTTPRoute` o kolejne reguły/serwisy; weryfikacja dostępu do Gateway z
   innych maszyn w LAN; dashboard I/O dysku dla Postgresa (metryki node-exportera już są, brak paneli);
   `postgres-exporter` (metryki natywne Postgresa — connections/cache hit/locki) nie wdrożony, świadomie odłożone.
9. **Commit bieżących zmian** — sprawdzić `git status` na starcie kolejnej sesji; w chwili pisania tej wersji
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
