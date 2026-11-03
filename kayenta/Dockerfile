#
# Builder Image
#
FROM gradle:9.6.1-jdk17 AS builder

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
FROM alpine:3.24

LABEL maintainer="delivery-engineering@netflix.com"

<<<<<<< HEAD
RUN apk --no-cache add --update openjdk17-jre
# Pick up the latest CA bundle and fail the build if the JVM still trusts emSign ECC Root CA - G3.
RUN apk --no-cache upgrade && ! keytool -list -cacerts -storepass changeit | grep -qiE 'emsign.*ecc.*g3'
=======
RUN apk --no-cache add --update 'openjdk25-jre>=25.0.5.0.3_p1-r0'
# Refresh the CA bundle, remove G1, and reject images trusting compromised roots.
RUN apk --no-cache upgrade && \
    sed -i 's@^mozilla/emSign_Root_CA_-_G1.crt$@!mozilla/emSign_Root_CA_-_G1.crt@' /etc/ca-certificates.conf && \
    update-ca-certificates && \
    ! keytool -list -cacerts -storepass changeit | grep -qiE 'emsign.*root.*g[123]|comodo.*aaa|aaa.*certificate.*services'
>>>>>>> 1a24274 (fix(security): remove compromised eMudhra roots from JRE images (#8325))

# Set where to look for config from
ENV KAYENTA_OPTS=-Dspring.config.location=file:/opt/kayenta/config/kayenta.yml

RUN mkdir -p /opt/spinnaker/plugins

# Copy from builder image
COPY --from=builder /opt/kayenta /opt/kayenta

CMD ["/opt/kayenta/bin/kayenta"]
