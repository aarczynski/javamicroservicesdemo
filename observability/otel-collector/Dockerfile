FROM golang:1.24-alpine AS http-probe
WORKDIR /build
RUN go mod init probe
RUN printf 'package main\nimport("net/http";"os")\nfunc main(){r,e:=http.Get("http://localhost:13133/");if e!=nil||r.StatusCode!=200{os.Exit(1)}}' \
    > main.go && CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o http-probe .

FROM otel/opentelemetry-collector-contrib:0.149.0
COPY --from=http-probe /build/http-probe /http-probe
