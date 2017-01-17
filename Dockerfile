FROM java:8

COPY . deck/

WORKDIR deck

RUN docker/setup-apache2.sh

RUN ./gradlew build -PskipTests

RUN mkdir -p /opt/deck/html/

RUN cp build/webpack/* /opt/deck/html/

CMD docker/run-apache2.sh
