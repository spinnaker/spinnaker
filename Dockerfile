FROM golang:1.15 as build
ARG VERSION

WORKDIR /app
COPY ./ ./

ENV LD_VERSION="-X github.com/spinnaker/spin/version.Version=${VERSION}"

RUN GOARCH=amd64 GOOS=linux CGO_ENABLED=0 go build \
    -ldflags "${LD_VERSION}" .

FROM alpine

RUN apk update \
    && apk add ca-certificates \
    && rm -rf /var/cache/apk/*

COPY --from=build /app/spin /usr/local/bin

CMD ["/bin/sh"]
