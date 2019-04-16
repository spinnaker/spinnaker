FROM openjdk:8

MAINTAINER delivery-engineering@netflix.com

ENV KUBECTL_RELEASE=1.12.7
ENV AWS_BINARY_RELEASE_DATE=2019-03-27

COPY . workdir/

WORKDIR workdir

RUN cp -r ./halyard-web/build/install/halyard /opt && \
  cd .. && \
  rm -rf workdir

RUN echo '#!/usr/bin/env bash' | tee /usr/local/bin/hal > /dev/null && \
  echo '/opt/halyard/bin/hal "$@"' | tee /usr/local/bin/hal > /dev/null

RUN chmod +x /usr/local/bin/hal

RUN curl -LO https://storage.googleapis.com/kubernetes-release/release/v${KUBECTL_RELEASE}/bin/linux/amd64/kubectl && \
    chmod +x ./kubectl && \
    mv ./kubectl /usr/local/bin/kubectl

RUN curl -o aws-iam-authenticator https://amazon-eks.s3-us-west-2.amazonaws.com/${KUBECTL_RELEASE}/${AWS_BINARY_RELEASE_DATE}/bin/linux/amd64/aws-iam-authenticator && \
  chmod +x ./aws-iam-authenticator && \
  mv ./aws-iam-authenticator /usr/local/bin/aws-iam-authenticator

ENV PATH "$PATH:/usr/local/bin/aws-iam-authenticator"

RUN useradd -m spinnaker

USER spinnaker

CMD "/opt/halyard/bin/halyard"
