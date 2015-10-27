FROM java:8

MAINTAINER clin@netflix.com

COPY . workdir/code

WORKDIR workdir/code

RUN ./gradlew installApp

RUN mv /workdir/code/igor-web/build/install /install

RUN rm -rf /workdir/code

CMD ["/install/igor/bin/igor"]
