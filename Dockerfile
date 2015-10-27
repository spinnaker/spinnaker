FROM java:8

MAINTAINER clin@netflix.com

COPY . workdir/

WORKDIR workdir

RUN ./gradlew buildDeb -x test

RUN dpkg -i ./clouddriver-web/build/distributions/*.deb

CMD ["/opt/clouddriver/bin/clouddriver"]
