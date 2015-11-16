FROM java:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN ./gradlew buildDeb -x test

RUN dpkg -i ./rosco-web/build/distributions/*.deb

CMD ["/opt/rosco/bin/rosco"]
