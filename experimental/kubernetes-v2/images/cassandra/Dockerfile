FROM ubuntu:14.04

COPY cassandra.list /etc/apt/sources.list.d/cassandra.list

RUN gpg --keyserver pgp.mit.edu --recv-keys F758CE318D77295D
RUN gpg --export --armor F758CE318D77295D | apt-key add -

RUN gpg --keyserver pgp.mit.edu --recv-keys 2B5C1B00
RUN gpg --export --armor 2B5C1B00 | apt-key add -

RUN gpg --keyserver pgp.mit.edu --recv-keys 0353B12C
RUN gpg --export --armor 0353B12C | apt-key add -

RUN apt-get update
RUN apt-get install -y cassandra

ADD run.sh .
ADD keys keys

CMD ["./run.sh"]
ENTRYPOINT [ "bash", "-c" ]
