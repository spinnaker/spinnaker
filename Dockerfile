FROM java:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN ./gradlew buildDeb -x test

RUN dpkg -i ./clouddriver-web/build/distributions/*.deb

CMD ["/opt/clouddriver/bin/clouddriver"]
