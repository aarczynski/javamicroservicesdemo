FROM ghcr.io/grpc-ecosystem/grpc-health-probe:v0.4.49 AS grpc-health-probe
FROM grafana/loki:3.7.2
COPY --from=grpc-health-probe /ko-app/grpc-health-probe /grpc-health-probe
VOLUME /loki
