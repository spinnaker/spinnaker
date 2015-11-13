FROM java:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN ./gradlew buildDeb -x test

RUN dpkg -i ./igor-web/build/distributions/*.deb

CMD ["/opt/igor/bin/igor"]
