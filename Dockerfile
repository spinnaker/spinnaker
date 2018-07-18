FROM openjdk:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN GRADLE_USER_HOME=cache ./gradlew buildDeb -x test && \
  dpkg -i ./clouddriver-web/build/distributions/*.deb && \
  cd .. && \
  rm -rf workdir && \
  apt-get -y update && \
  apt-get -y install apt-transport-https && \
  echo "deb https://packages.cloud.google.com/apt cloud-sdk-trusty main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list && \
  wget https://packages.cloud.google.com/apt/doc/apt-key.gpg && \
  apt-key add apt-key.gpg && \
  apt-get -y update && \
  apt-get -y install python2.7 unzip ca-certificates google-cloud-sdk && \
  apt-get clean

RUN curl -LO https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl && \
  chmod +x kubectl && \
  mv ./kubectl /usr/local/bin/kubectl

RUN curl -o heptio-authenticator-aws https://amazon-eks.s3-us-west-2.amazonaws.com/1.10.3/2018-06-05/bin/linux/amd64/heptio-authenticator-aws && \
  chmod +x ./heptio-authenticator-aws && \
  mv ./heptio-authenticator-aws /usr/local/bin/heptio-authenticator-aws

ENV PATH "$PATH:/usr/local/bin/heptio-authenticator-aws"

CMD ["/opt/clouddriver/bin/clouddriver"]
