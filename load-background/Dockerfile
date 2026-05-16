FROM grafana/k6:2.0.0

COPY entrypoint.sh /entrypoint.sh
COPY src/candidate-search.js /scripts/candidate-search.js

ENTRYPOINT ["/bin/sh", "/entrypoint.sh"]
