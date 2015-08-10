FROM java:8

MAINTAINER clin@netflix.com

COPY . workdir/

WORKDIR workdir

RUN ./gradlew build -x test

CMD ["./gradlew", "bootRun"]
