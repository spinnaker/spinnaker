FROM openjdk:8 as build

LABEL maintainer="delivery-engineering@netflix.com"

COPY . workdir/

WORKDIR workdir

RUN GRADLE_USER_HOME=cache ./gradlew installDist -x test -Prelease.useLastTag=true

FROM openjdk:8

COPY --from=build /workdir/halyard-web/build/install/halyard /opt/halyard

RUN echo '#!/usr/bin/env bash' | tee /usr/local/bin/hal > /dev/null && \
    echo '/opt/halyard/bin/hal "$@"' | tee /usr/local/bin/hal > /dev/null

RUN chmod +x /usr/local/bin/hal

ENV KUBECTL_RELEASE=1.12.7
ENV AWS_BINARY_RELEASE_DATE=2019-03-27

RUN curl -LO https://storage.googleapis.com/kubernetes-release/release/v${KUBECTL_RELEASE}/bin/linux/amd64/kubectl && \
    chmod +x ./kubectl && \
    mv ./kubectl /usr/local/bin/kubectl && \
    /usr/local/bin/kubectl version --client

RUN curl -o aws-iam-authenticator https://amazon-eks.s3-us-west-2.amazonaws.com/${KUBECTL_RELEASE}/${AWS_BINARY_RELEASE_DATE}/bin/linux/amd64/aws-iam-authenticator && \
    chmod +x ./aws-iam-authenticator && \
    mv ./aws-iam-authenticator /usr/local/bin/aws-iam-authenticator

ENV PATH "$PATH:/usr/local/bin/aws-iam-authenticator"

RUN wget -O /tmp/get-pip.py https://bootstrap.pypa.io/get-pip.py && \
    python /tmp/get-pip.py && \
    pip install awscli --upgrade

RUN useradd -m spinnaker

USER spinnaker

CMD "/opt/halyard/bin/halyard"
