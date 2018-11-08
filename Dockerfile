FROM openjdk:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN GRADLE_USER_HOME=cache ./gradlew buildDeb -x test && \
  dpkg -i ./rosco-web/build/distributions/*.deb && \
  mkdir /packer && \
  cd /packer && \
  wget https://releases.hashicorp.com/packer/1.3.1/packer_1.3.1_linux_amd64.zip && \
  apt-get install unzip -y && \
  unzip packer_1.3.1_linux_amd64.zip && \
  rm -rf /workdir

RUN wget https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get && \
  chmod +x get && \
  ./get && \
  rm get

ENV PATH "/packer:$PATH"

CMD ["/opt/rosco/bin/rosco"]
