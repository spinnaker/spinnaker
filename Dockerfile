FROM openjdk:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN GRADLE_USER_HOME=cache ./gradlew -I gradle/init-publish.gradle buildDeb -x test && \
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

RUN curl -o aws-iam-authenticator https://amazon-eks.s3-us-west-2.amazonaws.com/1.11.5/2018-12-06/bin/linux/amd64/aws-iam-authenticator && \
  chmod +x ./aws-iam-authenticator && \
  mv ./aws-iam-authenticator /usr/local/bin/aws-iam-authenticator && \
  ln -sf /usr/local/bin/aws-iam-authenticator /usr/local/bin/heptio-authenticator-aws

RUN wget -O /tmp/get-pip.py https://bootstrap.pypa.io/get-pip.py && \
    python /tmp/get-pip.py && \
    pip install awscli --upgrade 

ENV PATH "$PATH:/usr/local/bin/"

CMD ["/opt/clouddriver/bin/clouddriver"]
