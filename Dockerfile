FROM java:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN GRADLE_USER_HOME=cache ./gradlew installDist -x test -Prelease.useLastTag=true && \
  cp -r ./halyard-web/build/install/halyard /opt && \
  cd .. && \
  rm -rf workdir

RUN echo '#!/usr/bin/env bash' | tee /usr/local/bin/hal > /dev/null && \
  echo '/opt/halyard/bin/hal "$@"' | tee /usr/local/bin/hal > /dev/null

RUN chmod +x /usr/local/bin/hal

CMD "/opt/halyard/bin/halyard"
