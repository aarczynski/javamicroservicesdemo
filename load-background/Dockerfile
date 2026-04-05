FROM grafana/k6:1.7.1

COPY entrypoint.sh /entrypoint.sh
COPY src/candidate-search.js /scripts/candidate-search.js

ENTRYPOINT ["/bin/sh", "/entrypoint.sh"]
