#
# Builder Image
#
FROM gradle:6.5-jdk11 AS builder

# Prep build environment
ENV GRADLE_USER_HOME=cache
COPY . /tmp/workdir
WORKDIR /tmp/workdir

# Build kayenta
# Doesn't run build because the integration tests try to spin up a Docker
# container to talk to a Kayenta instance, which requires some Docker shenanigans.
RUN gradle assemble

# Unpack so release image can copy folder and be smaller
RUN tar -xf /tmp/workdir/kayenta-web/build/distributions/kayenta.tar -C /opt

#
# Release Image
#
FROM alpine:3.11

LABEL maintainer="delivery-engineering@netflix.com"

RUN apk --no-cache add --update openjdk11-jre

# Set where to look for config from
ENV KAYENTA_OPTS=-Dspring.config.location=file:/opt/kayenta/config/kayenta.yml

RUN mkdir -p /opt/spinnaker/plugins

# Copy from builder image
COPY --from=builder /opt/kayenta /opt/kayenta

CMD ["/opt/kayenta/bin/kayenta"]
