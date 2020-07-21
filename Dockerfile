FROM golang:1.12 as build
ARG VERSION

WORKDIR /app
COPY ./ ./

RUN ./build.sh --go-os linux --go-arch amd64 --version "${VERSION:-}"

FROM alpine

RUN apk update \
    && apk add ca-certificates \
    && rm -rf /var/cache/apk/*

COPY --from=build /app/spin /usr/local/bin

CMD ["/bin/sh"]