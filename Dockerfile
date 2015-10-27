FROM phusion/baseimage:0.9.17

MAINTAINER delivery-engineering@netflix.com

RUN apt-get install -y software-properties-common
RUN add-apt-repository -y ppa:webupd8team/java
RUN apt-get update

# Auto-accept the Oracle JDK license
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections

RUN apt-get install -y oracle-java8-installer

COPY . workdir/

WORKDIR workdir

RUN ./gradlew buildDeb -x test

RUN dpkg -i ./front50-web/build/distributions/*.deb

CMD ["/opt/front50/bin/front50"]
