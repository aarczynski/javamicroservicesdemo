FROM ghcr.io/grpc-ecosystem/grpc-health-probe:v0.4.47 AS grpc-health-probe
FROM grafana/tempo:2.10.3
COPY --from=grpc-health-probe /ko-app/grpc-health-probe /grpc-health-probe
