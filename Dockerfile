FROM dockerfile/java:oracle-java7

MAINTAINER clin@netflix.com

COPY . workdir

WORKDIR workdir

RUN ./gradlew build -x test

CMD ["./gradlew", "bootRun"]
