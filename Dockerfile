FROM openjdk:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN GRADLE_USER_HOME=cache ./gradlew -PenablePublishing=true buildDeb \
       -x test && \
  dpkg -i ./echo-web/build/distributions/*.deb && \
  cd .. && \
  rm -rf workdir

CMD ["/opt/echo/bin/echo"]
