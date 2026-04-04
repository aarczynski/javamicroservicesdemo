FROM alpine:3.21 AS dirs
RUN mkdir -p /tmp/tempo && touch /tmp/tempo/.keep && chown -R 10001:10001 /tmp/tempo

FROM ghcr.io/grpc-ecosystem/grpc-health-probe:v0.4.47 AS grpc-health-probe
FROM grafana/tempo:2.10.3
COPY --from=grpc-health-probe /ko-app/grpc-health-probe /grpc-health-probe
COPY --chown=10001:10001 --from=dirs /tmp/tempo /tmp/tempo
VOLUME /tmp/tempo
