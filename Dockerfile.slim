FROM openjdk:8-jre-alpine

MAINTAINER delivery-engineering@netflix.com

COPY ./rosco-web/build/install/rosco /opt/rosco
COPY ./rosco-web/config /opt/rosco
COPY ./rosco-web/config/packer /opt/rosco/config/packer

WORKDIR /packer

RUN apk --no-cache add --update bash wget curl openssl && \
  wget https://releases.hashicorp.com/packer/1.3.1/packer_1.3.1_linux_amd64.zip && \
  unzip packer_1.3.1_linux_amd64.zip && \
  rm packer_1.3.1_linux_amd64.zip

ENV PATH "/packer:$PATH"

RUN wget https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get && \
  chmod +x get && \
  ./get && \
  rm get

RUN adduser -D -S spinnaker

USER spinnaker

CMD ["/opt/rosco/bin/rosco"]
