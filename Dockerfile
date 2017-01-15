FROM java:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN GRADLE_USER_HOME=cache ./gradlew buildDeb -x test

RUN dpkg -i ./rosco-web/build/distributions/*.deb

RUN mkdir /packer

WORKDIR /packer

RUN wget https://releases.hashicorp.com/packer/0.12.1/packer_0.12.1_linux_amd64.zip

RUN apt-get install unzip -y

RUN unzip packer_0.12.1_linux_amd64.zip

ENV PATH "/packer:$PATH"

CMD ["/opt/rosco/bin/rosco"]
