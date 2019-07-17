#
# Builder Image
#
FROM gradle:5.4-jdk8 AS builder

# Prep build environment
ENV GRADLE_USER_HOME=cache
COPY . /tmp/workdir
WORKDIR /tmp/workdir

# Build kayenta
RUN gradle build

# Unpack so release image can copy folder and be smaller
RUN tar -xf /tmp/workdir/kayenta-web/build/distributions/kayenta.tar -C /opt

#
# Release Image
#
FROM openjdk:8-alpine

MAINTAINER delivery-engineering@netflix.com

# Set where to look for config from
ENV KAYENTA_OPTS=-Dspring.config.location=file:/opt/kayenta/config/kayenta.yml

# Copy from builder image
COPY --from=builder /opt/kayenta /opt/kayenta

CMD ["/opt/kayenta/bin/kayenta"]
