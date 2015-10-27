FROM java:8

MAINTAINER clin@netflix.com

COPY . workdir/

WORKDIR workdir

RUN ./gradlew --no-daemon build -x test

CMD ["./gradlew", "bootRun"]
