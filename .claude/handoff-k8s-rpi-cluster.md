# Handoff: klaster k8s na RPi5

Skondensowany handoff — stan na 2026-10-22. Pełna narracja diagnostyczna (sesja po sesji, ze ślepymi zaułkami)
żyje w historii gita tego pliku (`git log -p -- .claude/handoff-k8s-rpi-cluster.md`) — zakończone punkty są
stąd usuwane, nie archiwizowane w treści.

## Obecny stan (skrót)

12/12 node'ów fizycznych w klastrze, `kubeadm` (nie k3s, Kubernetes 1.36), Cilium 1.20.2 (CNI + Gateway API v1.6.2,
bez kube-proxy), MetalLB (L2), `local-path-provisioner`. Oba mikroserwisy (`app-candidates` x3 / `app-job-offers` x2,
jedna replika na node) + ich Postgresy wdrożone, dane załadowane (**200k candidates / 1,5M job offers / 10k
companies** od 2026-10-23: 1M z `data-generator` + 500k kopii z nowymi id, opis w README „Row counts”; wcześniej 200k/350k od
2026-10-18, 100k/150k). Stack observability: Prometheus/Loki/Grafana/Headlamp/Hubble + Tempo w architekturze
`tempo-distributed` (Kafka+RustFS). Ambient load (`load-background`, k6) działa 24/7. Tabela node taintów/IP jest w
`CLAUDE.md` (nie duplikować tutaj).

**Płytki D0/C1 rozdzielone wg ról (przebudowa 2026-10-03):** 4× D0 → `master` + `observability-1/2/3`, 8× C1 →
`platform-1`, `db-1/2`, `worker-1..5` — wszystkie appki i Postgresy na C1 (mapowanie MAC → rola w `inventory.ini`).

**Klaster stoi na `main`** (read model zmergowany 2026-10-23), obrazy appek `50ae17e` (wdrożone ponownie 2026-10-23 18:15 po teście top 50 `771f80c` z lokalnego brancha `perf/job-offer-top50`; wcześniej `main` `48b0e5d`, Boot 4.1.1, OTel agent 2.32.0). Podbicie zależności wdrożone w
całości 2026-10-15: Cilium 1.20.2, Gateway API v1.6.2, Strimzi 1.3.0, Prometheus chart 29.37.0, Loki 18.15.1,
tempo-distributed 3.12.0, otel-collector 0.162.0 (chart 0.176.0), Alloy 1.13.1, Grafana 13.1.8 (chart 13.6.0 — 13.2
ma regres z toastem „Unauthorized” dla anonimów, grafana/grafana#131876, poprawka dopiero w 13.3). Checki z
`CLAUDE.md` (w tym linki logi ↔ trace) przechodzą na Compose, minikube i klastrze. Kubernetes 1.36 → 1.37 świadomie
poza tym podbiciem (osobna operacja).

**Zmierzony sufit (2026-10-03, po przebudowie): 2100rps czyste** (10 min, 0% KO, p50/p95/p99 25/~210/471 ms na
klastrze stojącym ~2,5 h). 2200rps prawie, ale nie czysto (22 KO, p99 1859 ms) — ogranicza CPU `postgres-job-offers`
(2,88/3 rdzenia, throttling 55%). Cała historia zmian odtworzona krok po kroku od stanu bazowego (400rps):
`k8s-cluster/RPS-SCALING.md` — czytać przed każdą pracą nad skalowaniem, nie duplikować tutaj. Każdy krok stamtąd ma
swój lokalny branch `replay/*` (manifesty z gałkami + przypięte obrazy w rejestrze) — odtworzenie stanu to
`kubectl apply` manifestów z brancha. `make candidateSimulation` przeciw klastrowi sam robi wymuszony pełny GC w
appkach przed testem (`make k8s-gc`).

**Rozgrzewka po przebudowie/restarcie trwa ~2,5 h, nie 15-20 min** — te same dane i kod, każdy kolejny przebieg
lepszy (JIT, Cilium, nadrabianie Tempo/Kafki; dokładna przyczyna niezbadana). Mierzyć capacity dopiero na klastrze
stojącym kilka godzin.

## TODO / Next steps (w kolejności priorytetu)

1. **Read model `job_offer_match` — zmergowany do `main` 2026-10-23, wpis w RPS-SCALING (2100 rps przy 1,5M).**
   **Wynik do RPS-SCALING (2026-10-23 21:02, `50ae17e`, 1,5M, JVM-y ~45 min po deployu + 5 min rozgrzewki): 2100 rps,
   10 min, 0% KO, p50/p95/p99 21/89/167 ms, max 947.** Hikari `app-job-offers` śr. 15/20 zajętych, śr. 14,5
   czekających (max 82), czekanie śr. 6,5 ms, trzymanie 4,8 ms; CPU `app-job-offers` 2,35/3 na replikę (throttling do
   12%), Postgres 1,82/3 (~865 µs/req), `app-candidates` ~1,57/3 na replikę, `postgres-candidates` 0,54/3. GC
   `app-job-offers` z metryk: ~2,6 pauzy/s po ~8,7 ms (~2,3% czasu) — mniej niż w JFR przy 2M (niżej).
   Dawny opis brancha `perf/job-offer-read-model`: zwykła tabela `job_offer_match` (`V1_3`) zamiast
   materialized view, utrzymywana triggerami na `job_offer`, `job_offer_skill`, `job_offer_employment_type`, `company`,
   `skill` w transakcji zapisu, więc dane nigdy nie są nieaktualne. Masowy import idzie z
   `PGOPTIONS='-c job_offer_match.sync=off'` i jednym `rebuild_job_offer_match()` na końcu (oba `*load-data`).
   Zweryfikowane: testy (w tym 7 na synchronizację), Compose i minikube (pełny load + checki observability z linkami
   logi ↔ trace), na klastrze migracja 8,4 s, read model = źródło (244 969 wierszy), index-only scan bez heap fetches,
   checki observability zielone. 8 równoległych transakcji na tej samej ofercie zostawia read model zgodny ze źródłem
   (lock `FOR NO KEY UPDATE` w `refresh_job_offer_match`).
   - **Load testy 2026-10-22 wieczorem** (10 min stałej fazy, Mac na LAN w VLAN 10 i zasilaniu, appki rozgrzane 9 h;
     1M = pełny zbiór z `data-generator`, 1,5M/2M = 1M + kopie 500k/1M ofert z nowymi id, jak przy widoku):

     | Oferty (aktywne) | rps | KO | p50/p95/p99 | `postgres-job-offers` | `app-job-offers` (2×3) | Hikari `app-job-offers` (10) |
     |---|---:|---:|---|---|---|---|
     | 1M (700k) | 2100 | 0% | 13/52/85 ms | 1,42/3, ~680 µs/req | 4,10, ~1950 µs/req | śr. 5,4 aktywnych, ~1 czekający |
     | 1,5M (1,05M) | 2100 | 0,01% (124×503) | 42/747/1237 ms | 1,79/3, ~850 µs/req | 4,97, throttling 19% | pełna, śr. ~100 czekających |
     | 2M (1,4M) | 2100 | 5,43% (503) | 675/2326/3041 ms | 2,44/3, throttling 26% | 5,14, throttling 30% | pełna, ~185 czekających |
     | 2M (1,4M) | 1800 | 0% | 29/207/473 ms | 2,20/3, ~1220 µs/req | 4,74, ~2630 µs/req | śr. 8,8 aktywnych, ~50 czekających |

     Przy 1M lepiej niż `main` przy 350k (p99 85 vs 157 ms, Postgres ~680 vs ~1250 µs/req). Limit od 1,5M:
     `app-job-offers` — pula Hikari pełna, CPU blisko limitu z throttlingiem; Postgres ma zapas do 2M. Kopie podwajają
     dopasowania na kandydata, więc rośnie też praca appki (scoring, serializacja) — nie tylko bazy.
     Dane na klastrze (od 2026-10-23 18:10): **1,5M ofert (1M + 500k kopii, 1 049 687 aktywnych) + tabela pomocnicza
     `bench_original_offer`** (id oryginałów; kopie dokłada/usuwa skrypt z `job_offer_match.sync=off` + rebuild + vacuum)
     — przed kolejnym pomiarem na realnym zbiorze przeładować (`make k8s-load-data` albo import samego job-offers bez generowania).
   - **Top 50 (2026-10-23, `771f80c`) — odłożone, nie na tym branchu.** Commity `b368387` + `771f80c` (+ pin
     manifestów i ten opis w `e3e8eeb`) leżą na lokalnym branchu `perf/job-offer-top50`; `perf/job-offer-read-model` ma
     tylko read model. Zamiast sztywnego limitu ma być paginacja (osobny punkt niżej). Odpowiedź to 50 najlepszych ofert (wcześniej wszystkie dopasowane; przy 2M
     średnio 33, max 830, teraz średnio 14, 15% kandydatów trafia w limit). Najpierw zrobione jako dwa zapytania
     (scoring + szczegóły dla 50, `b368387`) — gorzej: 2M/2100 7,98% KO, 2M/1800 4,64% KO. JFR: wiersze i scoring to
     mały udział CPU `app-job-offers`, każde native query ma stały narzut Hibernate/Spring Data + osobny checkout z puli
     (drugie zapytanie samo 13% CPU). Wrócone do jednego zapytania z limitem w pamięci. Wyniki na 2M, świeże JVM-y
     (~40 min po deployu, 5 min rozgrzewki):

     | Wersja | rps | KO | p50/p95/p99 | CPU/req `app-job-offers` | CPU/req `app-candidates` | czas trzymania połączenia |
     |---|---:|---:|---|---|---|---|
     | wszystkie oferty | 2100 | 5,43% | 675/2326/3041 ms | ~2450 µs | ~2300 µs | 7,9 ms |
     | top 50 | 2100 | 2,13% | 556/2189/2945 ms | ~2490 µs | ~2130 µs | 7,6 ms |
     | wszystkie oferty | 1800 | 0% | 29/207/473 ms | ~2630 µs | ~2430 µs | 6,7 ms |
     | top 50 | 1800 | 0% | 17/117/710 ms | ~2500 µs | ~2110 µs | 5,6 ms |

     Prawdziwy limit przy rosnących danych to **czas trzymania połączenia** przez zapytanie (1M 3,1 ms → 1,5M 5,9 →
     2M 7,9 ms przy 2×10 połączeniach), nie CPU per wiersz w appce — rośnie praca Postgresa na zapytanie (więcej ofert
     w bounding boxie), Postgres ma 2,5/3 rdzenia z throttlingiem przy 2M/2100. Kolejne dźwignie: mniej pracy Postgresa
     na zapytanie, ew. pula Hikari, GC (niżej).
     Sprawdzić też, czy `jit=off`/`force_generic_plan` nadal pomagają.
   - **Pauzy GC w `app-job-offers` (JFR 2026-10-23, 2M, 120 s pod obciążeniem)** — JVM jedzie na **Serial GC**
     (`DefNew` + `SerialOld`, 0 wątków GC): ergonomia wybiera G1 dopiero od ≥2 CPU **i ≥1792 MB** pamięci, a kontener
     ma limit 1536Mi, a we flagach `java` nie ma wyboru GC. Heap `-Xms384m -Xmx384m`, młoda generacja ~100 MB
     (heapUsed 94→196 MB). Wynik: 227 young GC w 120 s (~1,9/s), mediana pauzy 28,7 ms, p99 38,9, max 40 ms — razem
     6,7 s, czyli **5,6% czasu cała JVM stoi** (stop-the-world, jednym wątkiem). W trakcie pauzy wszystkie żądania w
     locie trzymają połączenia z puli, więc pauzy dokładają się do czasu trzymania połączenia i do ogona p99.
     `app-candidates` ma ten sam heap i limit → prawdopodobnie też Serial (niesprawdzone). Nieruszane. Kandydaci do
     sprawdzenia: `-XX:+UseParallelGC` albo `-XX:+UseG1GC` (3 CPU, wielowątkowe young GC) i/lub większy heap
     (limit 1536Mi daje miejsce, młoda generacja rzadziej) — mierzyć osobno, 10-min run.
   - Punkt odniesienia — materialized view z 2026-10-17/18 (`jit=on`, `plan_cache_mode=auto`, dane >1M to kopie 1M):
     1M — Postgres ~1,3/3 (~630 µs/req), `app-job-offers` ~4,4/6; 1,5M — 0% KO, p99 255 ms, Postgres 1,8/3,
     `app-job-offers` 4,8/6; 1,75M — p99 910 ms (granica); 2M — 0,62% KO. Limitem było CPU `app-job-offers`.
     Model znormalizowany (`main`) przy 350k: Postgres 2,62/3 (~1250 µs/req).
   - Read model pod ciągłymi zapisami nie był mierzony — update czyści bity visibility map, index-only scan wtedy
     sięga do tabeli do następnego autovacuum. Przy dodawaniu zapisów ofert: test z zapisami w tle, ew. tuning
     autovacuum dla `job_offer_match`.

2. **Paginacja wyników wyszukiwania zamiast sztywnego top 50 (dodane 2026-10-23).** Top 50 tylko ucina listę i nie
   da się dojść do dalszych ofert. Do zrobienia `page`/`size` w `POST /job-offers/search`, przekazywane z endpointu
   `app-candidates` przez Feign, w odpowiedzi strona + `totalElements`; ranking stabilny (`score` malejąco, potem
   `id`). Dwa warianty:
   - **W aplikacji** (sortowanie w pamięci, skip + limit) — prosta poprawka API, ale każda strona kosztuje tyle co
     pełne wyszukiwanie (żeby wiedzieć, co jest na stronie 3, trzeba wczytać i ocenić wszystkie dopasowania), więc
     wydajnościowo = top 50.
   - **W bazie** (score w SQL, `ORDER BY score DESC, id LIMIT/OFFSET`) — ma sens tylko, gdy kolumny potrzebne do
     score są w indeksie pokrywającym: ranking z samego indeksu, całe wiersze (z `description`) tylko dla jednej strony
     zamiast dla wszystkich dopasowań → krótszy czas trzymania połączenia. Koszt: scoring umiejętności (różnica
     seniority, kara za brak obowiązkowych) w SQL na tablicach, a CPU idzie do Postgresa, który przy 2M/2100 ma już
     2,5/3. Osobny eksperyment z pomiarem.
   Nie dzielić na dwa zapytania (ranking + szczegóły strony) — zmierzone na top 50 (`b368387`): każde native query to
   checkout z puli + stały narzut Hibernate/Spring Data, drugie zapytanie samo 13% CPU `app-job-offers`, KO 2M/2100
   7,98% vs 5,43% bez limitu. Pomiar top 50 (wariant „w aplikacji”, `771f80c`) jest w punkcie 1.

3. **Chaos monkey (dodane 2026-10-02).** Celowe zabijanie podów/node'ów pod obciążeniem, żeby sprawdzić odporność
   klastra i appek. Narzędzie do wyboru. Pamiętać o znanych ograniczeniach: twardy `podAntiAffinity` bez wolnego
   node'a (zastępczy pod zostaje `Pending`), Gatling po awarii node'a potrafi wisieć bez wysyłania ruchu.

4. **Autoscaling rozwala klaster — duży ruch od razu idzie na nierozgrzany JVM.** `app-candidates` HPA jest dlatego
   przypięty na sztywno 3 repliki. Świeży pod po scale-upie dostaje pełny udział ruchu od razu (brak LB slow-startu
   w Gateway API Cilium) i zimny JVM zapycha pulę Hikari / wybija circuit breaker Envoya. `prometheus-adapter` +
   metryka RPS zostają żywe, odblokowanie to zmiana jednej linijki w `k8s-cluster/manifests/candidates/hpa.yaml`.
   Do rozwiązania: albo Cilium dostanie `slow_start_config` (śledzić
   [cilium/cilium#43532](https://github.com/cilium/cilium/issues/43532) lub odpowiednik), albo rozgrzewka JVM
   przed przyjęciem ruchu (np. warmup w `startupProbe`/readiness, CDS/AOT cache). Ten sam mechanizm zimnego startu
   dotyczy każdego rolloutu — po deployu zawsze rozgrzewka przed pomiarem.

5. **Flamegraph `app-candidates` — co zjada tyle CPU (dodane 2026-10-02).** Appka tylko pobiera kandydata z bazy,
   woła `app-job-offers` przez Feign i zwraca wynik, a mimo to zużywa dużo CPU na request. Zdjąć flamegraph pod
   obciążeniem (np. async-profiler / JFR) i znaleźć gorące miejsca.

6. **Odporność na power cycle — nadal wymaga ręcznej interwencji.** Stan na 2026-09-24:
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
   - **Power cycle 2026-10-15** (`observability-1` wisiał od 2026-10-11 bez pinga i SSH, Kafka/Tempo 4 dni w
     `Terminating`): klaster wstał sam, bez `failed to reserve container name`; lag `block-builder` tylko 52 (bez
     resetu offsetu). `tempo-block-builder` i `entity-operator` utknęły w backoffie po starcie przed brokerem
     Kafki — wystarczyło usunąć pody.
   - Po każdym restarcie: `kubectl get pods -A | grep -v Running`, i pamiętać o rozgrzewce (patrz „Obecny stan”) —
     nie mierzyć wtedy capacity.

7. **Niezbadany pad sieciowy `worker-2` z 2026-09-23** (`journalctl -b -1`). Niskopriorytetowe, nie powtórzył się.

8. **Skoki opóźnień co ~40 s przy ~1500rps (zgłoszone 2026-09-23) — najpewniej Mac na baterii.** Nie odtworzyły się
   w sondzie w klastrze (2026-09-24), a 2026-09-27 identyczny objaw (wszystkie repliki naraz, RPS na serwerze spada
   i nadrabia, cykl ~30 s z fazą zależną od startu Gatlinga) okazał się pracą generatora na baterii — po podłączeniu
   zasilania zniknął. Wstecz niezweryfikowane; jeśli wróci przy Macu na AC — procedura sondy
   w git history tego pliku (2026-09-24).

9. **Postgres job-offers jest sufitem — następny krok: `pg_stat_statements`.** Na rozgrzanej bazie zapytanie
   wyszukujące planuje się ~0,95 ms, a wykonuje ~0,58 ms; niezweryfikowane, czy pod obciążeniem prepared statementy
   (listy `IN` o zmiennej długości → różny tekst SQL) faktycznie omijają planowanie. `pg_stat_statements` da realny
   koszt i liczbę wywołań każdego zapytania zamiast próbkowania `pg_stat_activity`. Kandydaci do
   odciążenia: `findByIdIn` (entity graph + `IN`, ~26% próbek aktywnych), doładowania employment types (~6%).
   Niedokładne p99 w Grafanie (`README.md` Known issues) nadal otwarte; Keycloak/SSO świadomie za nimi, bez node'a.

10. **Brak zabezpieczenia przed rozjazdem `candidatesDataFile` vs. baza na klastrze** — od 2026-09-30 każdy
   `*load-data` (Compose/k8s/minikube) zawsze przeładowuje bazę, więc `data-generator/output/candidates/01-candidates.sql`
   po ostatnim uruchomieniu zawsze pasuje do bazy, na którą je załadowano. Ryzyko zostaje przy przełączaniu między
   środowiskami (plik z minikube vs. baza na RPi). Do rozważenia: krok w symulacji weryfikujący próbkę ID przed testem.

10a. **`CandidateGeneratorSpec` jest niestabilny** — nazwisko z apostrofem (np. `O'Hara`) generator zapisuje w mailu
   bez apostrofu, test tego nie uwzględnia. Losowo wywala `make generate-data`, a więc i `make k8s-load-data`
   (2026-09-28, jedno z pięciu przeładowań). Ponowne uruchomienie przechodzi.

11a. **Dashboardy Grafany na klastrze ładują się za wolno (zgłoszone 2026-10-04, problem „od dawna”).** Wykresy długo
   się wczytują. Nie badane — przyczyna nieznana. Na start: zmierzyć czas pojedynczych zapytań paneli (Query
   inspector w Grafanie albo `/api/ds/query` z pomiarem czasu) i porównać z obciążeniem Prometheusa/Loki
   (`prometheus-server`, `loki-0` — oba na `observability-2`, płytka D0).

11b. **Rozbudowa o nowe płytki i przetasowanie ról (dodane 2026-10-24).** Dwie nowe Pi 5 8 GB czekają poza
   klastrem (wyłączone, bez rezerwacji DHCP, nie w `inventory.ini`): obie **Rev 1.1 / D0** (`d04171`), Ubuntu 26.04.1,
   NVMe Lexar NM620 256 GB, `throttled=0x0`, SMART OK, passwordless sudo dodane ręcznie
   (`/etc/sudoers.d/90-aarczynski`, playbook tego nie robi). `98:fe:54:45:45:01` miała gorsze i dużo głośniejsze
   chłodzenie (przy 65 °C ~7300 rpm vs ~4500 rpm; ~3-4 °C cieplej na tym samym biegu) — user podmienia wentylator na
   fabryczny z obudowy, jak w `98:fe:54:45:3a:73`. User dokupuje trzecią płytkę; wtedy:
   - zlistować na nowo wszystkie płytki (rewizja, `bcm2712d0/c1-pinctrl`, MAC) i ułożyć role od zera,
   - `platform-1` (i coś jeszcze) przenieść na wolniejsze płytki D0, zwalniając C1 — Gateway jest na ścieżce
     każdego requestu, więc po przenosinach powtórzyć 10-min load test i sprawdzić, czy sufit 2100 rps się trzyma,
   - może spróbować SSO (Keycloak) na zwolnionym node'zie (dotąd odłożone „bez node'a”, patrz pkt 9),
   - nowe wartości taintów → tolerancje w pięciu DaemonSetach (`CLAUDE.md`), rezerwacje DHCP w Omadzie (wymagają
     restartu płytki), `known_hosts` po zmianie IP.

12. Dalekie / niepriorytetowe: HA Postgresa (CloudNativePG/Patroni — `local-path` trzyma PV na dysku node'a),
   GitOps (ArgoCD/Flux), rozszerzenie `HTTPRoute`, dashboard I/O dysku Postgresa, `postgres-exporter` na k8s
   (świadomie tylko w Compose, `a2f37c7`), panel "Running Pods" liczący fazę zamiast gotowości kontenera.

## Kluczowe pułapki / lekcje (żeby nie powtórzyć błędu)

### Automatyzacja / bootstrap
- **Operator Cilium 1.19.x nie startuje przy CRD Gateway API v1.6.x (2026-10-15)** — `TLSRoute` z kanału standard
  serwuje tylko `v1`, operator 1.19 szuka `v1alpha2` (`no matches for kind "TLSRoute" in version ... v1alpha2`,
  fatal). Pośredni krok 1.19.8 upadł na operatorze (agenty przeszły); pomogło od razu dokończenie na 1.20.2. Przy
  następnym takim skoku: CRD Gateway API podbijać dopiero razem z wersją Cilium, która je obsługuje.
- Alloy (DaemonSet na 12 node'ach) nie mieści się w `helm --wait --timeout 10m` — dać 20 min.
- **MinIO przestało publikować obrazy** (Docker Hub wrzesień 2026, quay.io 401 od 2026-10-03) — klaster jechał
  tylko na cache containerd. **2026-10-04 zastąpione RustFS** (`k8s-cluster/manifests/rustfs/`, `rustfs/rustfs:1.0.1`
  z Docker Hub, arm64). Bucket zakłada initContainer przez `mkdir` (RustFS traktuje katalog w `/data` jako bucket),
  bez `mc`. MinIO usunięte z klastra razem z PV i obrazami w cache (`observability-2`, `worker-3`, `worker-5`).
  Na węzłach nie ma `crictl` — obrazy usuwa się `ctr -n k8s.io images rm` (np. przez `kubectl debug node/...`).
  Tempo startujące przed RustFS restartuje kilka razy (`ListObjects` connection refused), potem wstaje samo.
- **`bootstrap.sh` instalował CRD Gateway API po Cilium** — `cilium-operator` sprawdza je raz przy starcie, więc na
  świeżym klastrze kontroler Gateway się nie włączał (`GatewayClass` `ACCEPTED=Unknown`, brak Service na `.100`).
  Wcześniejsze przebudowy pewnie trafiały na restart operatora przypadkiem. Naprawione 2026-10-03: CRD przed Cilium.
  Na żywym klastrze wystarcza `kubectl rollout restart deployment/cilium-operator -n kube-system`.
- **`prometheus-adapter` nie był w `bootstrap.sh`** (instalowany kiedyś ręcznie) — dopisany 2026-10-03.
- **Zmiana rezerwacji DHCP w Omadzie nie przestawia płytki, która już ma dzierżawę z puli dynamicznej** — po
  przetasowaniu 2026-10-03 trzy płytki wstały na `.201`-`.203`, `networkctl renew` tylko przedłużało dzierżawę;
  pomógł dopiero restart płytki. Weryfikacja: `arp -an` po pingu całej podsieci, porównanie MAC z `inventory.ini`.
  Po zamianie IP między płytkami odświeżyć `~/.ssh/known_hosts` (`ssh-keygen -R` + `ssh-keyscan`).
- **`GatewayClass "cilium"` nigdy nie był tworzony przez żaden skrypt** — istniał na klastrze tylko dzięki
  zapomnianemu ręcznemu krokowi sprzed pełnej automatyzacji. Ujawnione dopiero przy pełnym `kubeadm reset` +
  rebuild (2026-09-20): Gateway wisiał w `Pending`, MetalLB nie miał czego ogłaszać. Przyczyna: chart Cilium
  z `gatewayClass.create: auto` tworzy go tylko, gdy CRD Gateway API są widoczne przy instalacji. **Fix na stałe
  (2026-10-15)**: `gatewayClass.create: "true"` w `values-cilium.yaml` — chart zawsze go tworzy (wcześniej osobny
  `gatewayclass.yaml`, przez który `helm upgrade` do 1.19.8 odmówił przejęcia obiektu).
- **`podAntiAffinity` bez jawnego `namespaces` w `labelSelector` widzi tylko własny namespace** — cross-namespace
  reguła (np. `app-candidates` unikający `app-job-offers` w innym namespace) wygląda poprawnie skonfigurowana, ale
  cicho nigdy się nie odpala. Zawsze dopisywać jawną listę `namespaces`.
- **Twardy `podAntiAffinity` + ciasny budżet node'ów wymaga `maxSurge: 0`** w strategii rollout — domyślny
  `maxSurge: 25%` próbuje odpalić dodatkowy pod przy każdym rollout; z dokładnie jednym node'em na replikę nie ma
  dla niego miejsca i rollout się zakleszcza, aż ktoś ręcznie usunie stary pod.
- **`bootstrap.sh`: `kubectl apply -f <katalog>` na katalogu z `kustomization.yaml` w środku wybucha** (próbuje
  sparsować plik kustomize jako zwykły manifest) — wymieniać pliki po nazwie, nie aplikować całego katalogu.
- **`TRUNCATE ... CASCADE` w `load-data.sh` kasuje demo-seed Flywaya** — wiersze z migracji `V1_1`, której Flyway
  nigdy nie powtórzy (`flyway_schema_history` ma ją jako wykonaną). Dlatego skrypt po truncate odpala realny plik
  migracji (`V1_1__demo-data.sql`) przez `psql`, nie kopię jego treści. Schematu i `flyway_schema_history` nie rusza.
- **`load-data` bez przeładowania rozjeżdżał plik z bazą** (do 2026-09-28): skrypt generował nowe losowe UUID, a potem
  pomijał import niepustej bazy — load test szedł w 404. Teraz `*load-data` zawsze robi pełny przeładunek, a
  `*reload-data`/`--force` nie istnieją.
- **Lokalny `candidatesDataFile` łatwo rozjeżdża się cicho z bazą na klastrze** — Gatlingowy check
  (`status().in(200, 404)`) traktuje 404 jako sukces, więc test na nieaktualnych ID daje fałszywe "0% KO", nigdy
  faktycznie nie dotykając realnego matchingu. Zawsze weryfikować próbkę ID curl-em przed zaufaniem wynikom
  load-testu (patrz też pamięć `feedback_verify_loadtest_data`).
- **Eksperymentalne tagi obrazów budowane ręcznie (`docker build`/`push` z palca) nie przetrwają rebuildu
  rejestru** — commitować kod ZANIM się go długo testuje na klastrze, żeby `make k8s-deploy` zawsze był awaryjnym
  wyjściem po utracie rejestru.

- **`make k8s-load-data` sam generuje dane (`make generate-data`)** — bez `candidates=/jobOffers=/companies=`
  wraca do domyślnych 100k/50k/10k i cicho zmienia rozmiar zbioru na klastrze. Parametry przechodzą do
  zagnieżdżonego `make` przez `MAKEFLAGS`. Po przeładowaniu: `VACUUM ANALYZE` (osobnym `psql -c`, nie w jednym
  `-c` z innymi poleceniami — wtedy leci w transakcji i Postgres odmawia), i nowy plik ID do Gatlinga, bo
  kandydaci też dostają nowe UUID.

### minikube
- **Pełny dysk Docker Desktop wrócił 2026-10-22** (99%, Loki w Compose i na minikube „Ingester is shutting down”;
  Loki dławi WAL już od 90% zajętości dysku). Główny zjadacz: anonimowe wolumeny po starych `docker compose up`
  (~11,5 GB, `docker volume prune` — nazwane, w tym `minikube`, zostają). Po zwolnieniu miejsca Loki trzeba
  zrestartować (`docker compose restart loki` / `kubectl delete pod loki-0`), sam nie wraca. Lokalne obrazy
  `192.168.10.190:5000/*` celowo nieruszane (tagi bez SHA, np. `exp-*`, mogą być jedyną kopią).
- **Pełny dysk Docker Desktop zatrzymuje Loki po cichu (2026-10-04).** Dysk minikube (sterownik docker) to dysk VM
  Docker Desktop — przy 99% Loki odrzucał wszystkie pushe z Alloy komunikatem „Ingester is shutting down”, a
  wyglądał na zdrowy. Puste panele „by Pod”/dyskowe na minikube to z kolei stałe ograniczenie cAdvisora/node-exportera
  w minikube, nie błąd dashboardów (na klastrze mają dane). Szczegóły: `overlays/minikube/README.md`, „Known gaps”.
- **Trace → logi w Grafanie nie działały nigdzie (naprawione i wdrożone na klaster 2026-10-04).**
  `tracesToLogsV2` mapował `service.name` na nieistniejącą etykietę `job` i filtrował po treści linii, a `trace_id`
  jest w structured metadata. Teraz własne zapytanie `{service_name=…} | trace_id="…"` — zweryfikowane na compose i
  minikube i klastrze (logi dla wszystkich zachowanych trace'ów z requestów; health checki logów nie mają).
- **`make minikube-start` wznawia klaster na starych obrazach** — niczego nie przebudowuje. 2026-09-28 minikube
  chodził na obrazach z 2026-09-21 (sprzed fixu JVM CPU `60cdd0b`, natywnego SQL, samplingu trace'ów): dwie z pięciu
  instancji raportowały `jvm_cpu_recent_utilization_ratio` = 0, co na dashboardzie wyglądało jak "pracują 2 z 3 i 1
  z 2". Po zmianach w kodzie zawsze `make minikube-image` + `make minikube-deploy`.
- **Ruch na minikube przez `localhost:30080` rozkłada się równo** (zmierzone 2026-09-28 przy ~900rps: candidates
  290/318/288, job-offers 432/469 rps). NodePort to L4 per połączenie, ale przy takim ruchu to się uśrednia.
  Nierówny obraz obciążenia najpierw sprawdzać per-instancja RPS w Prometheusie, zanim się obwini load balancing.
  `--ports=30080:30080` istnieje, bo `kubectl port-forward` przypina cały ruch do jednego poda.
- Zmian w kodzie/konfigu appek pod ograniczenia minikube nie robimy — tylko overlay/skrypty minikube.

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
  (RustFS, wcześniej MinIO).
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

### Sprzęt: płytki D0 vs C1
- **Rev 1.1 (`d04171`) to BCM2712 D0, Rev 1.0 (`d04170`) to C1** — rozpoznanie po
  `sudo strings /sys/firmware/fdt | grep -o 'bcm2712[a-z0-9]*-pinctrl'`. Na D0 **zapis** do pamięci jest ~2×
  wolniejszy od L3 w górę (memset 1,5M: 10 vs 25-34 GB/s; DRAM: 6,9 vs 13,3 GB/s), odczyt prawie równy. JVM (zerowanie
  TLAB, kopiowanie w GC) jest zapisochłonny → ~10-15% więcej CPU/request na D0. To krzem, nie kernel ani firmware
  (bootloader `2026-09-25` z "Enable background refresh on 2712d0" nic nie zmienił). Dlatego od 2026-10-03 D0 obsługują
  tylko `master`/`observability-*`. Pomiary (`taskset -c 3`, `python3` + `ctypes.memset`/`memmove`, bufory 256K-64M,
  best of 3) porównywać na node'ach w tym samym stanie (pusty vs. z podem) — działająca appka zaszumia L2. Tabela
  wszystkich 12 node'ów: `k8s-cluster/RPS-SCALING.md` → "Known issue: not every Pi 5 is equally fast".
- Flash bootloadera: repo `rpi-eeprom` (paczka Ubuntu kończy się na 2024-11) i
  `sudo env PATH="$HOME/rpi-eeprom:$PATH" FIRMWARE_ROOT="$HOME/rpi-eeprom/firmware" ~/rpi-eeprom/rpi-eeprom-update -f <bin>`
  — bez `PATH` skrypt woła stary systemowy `rpi-eeprom-digest` bez `-c` i cicho nic nie przygotowuje; nowy skrypt
  flashuje od razu przez `flashrom`, restart tylko po to, żeby nowy bootloader zadziałał. SSH kluczem:
  `aarczynski@<IP z inventory.ini>`.

### Load-testing / metodologia wydajności
- **Testować jedną zmienną na raz pod realnym współbieżnym obciążeniem** — bundlowana zmiana dwóch rzeczy naraz
  potrafi zamaskować, że jedna z nich jest katastrofalna a druga to czysty zysk. Pojedynczy `EXPLAIN ANALYZE` czy request nie przewiduje zachowania pod współbieżnością.
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
- **Load test tylko z generatora w VLAN 10 (Cloud), bez routera (2026-10-18).** Ruch z 192.168.0.x przez router
  ER7406 po ~4-5 min obciążenia 2100 rps staje co ~33 s na 0,3-3 s → fala ~4k × 503 z Envoya. Mac: interfejs VLAN
  `vlan0` z **MTU 1496** (przy 1500 pełne ramki z tagiem giną). Przed testem `route -n get 192.168.10.100` → `vlan0`.
- **Gatling tylko z Maca na zasilaniu (`pmset -g batt` → "AC Power") (2026-09-27).** Na baterii generator wysyła ruch
  paczkami co ~30 s i daje p99 ~900 ms przy 2000rps, co wygląda jak regresja klastra (godzina diagnozy po złej stronie).
  Rozpoznanie: równość napływu `irate(http_server_request_duration_seconds_count{job="app-candidates"}[3s])` z krokiem
  1 s — zsynchronizowany spadek i przestrzał na wszystkich replikach = klient. 90 W z monitora wystarcza.
- **Po power cyclu 2026-09-27**: unattended-upgrades podniosły kernel na `6.8.0-1065` na 9 node'ach (`worker-1`/`-4`
  zostały na 1064, `worker-2` na 6.17), a nieczyste zamknięcie wyzerowało `pg_stat_user_tables` (statystyki planera w
  `pg_class`/`pg_stats` przetrwały). Na wyniki nie wpłynęło — ale kernel dryfuje sam, warto rozważyć przypięcie.
- **`max_parallel_workers_per_gather` na `postgres-job-offers` nie ma wpływu** (2026-10-03): przy domyślnym 2
  `pg_stat_database.parallel_workers_launched` = 0 po ~6 mln transakcji — planer nie wybiera planów równoległych.
  W repo zostaje 0.
- Masowy restart całego klastra zawsze daje kilkanaście-kilkadziesiąt minut degradacji (JIT warmup appek + Cilium/
  CNI reconciliation) — nie traktować pierwszego load testu po restarcie jako miarodajnego pomiaru capacity.
