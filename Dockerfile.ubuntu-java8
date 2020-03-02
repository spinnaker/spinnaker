FROM ubuntu:bionic
MAINTAINER sig-platform@spinnaker.io
COPY gate-web/build/install/gate /opt/gate
RUN apt-get update && apt-get -y install openjdk-8-jre-headless wget
RUN adduser --disabled-login --system spinnaker
RUN mkdir -p /opt/gate/plugins && chown -R spinnaker:nogroup /opt/gate/plugins
USER spinnaker
CMD ["/opt/gate/bin/gate"]
