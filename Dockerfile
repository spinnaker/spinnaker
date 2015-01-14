FROM ubuntu
RUN echo 'debconf debconf/frontend select Noninteractive' | debconf-set-selections
RUN sudo apt-get update -q -y
RUN sudo apt-get install -q -y wget
RUN sudo apt-get install -q -y unzip
WORKDIR /tmp
RUN sudo wget -q https://dl.bintray.com/mitchellh/packer/packer_0.7.5_linux_amd64.zip
RUN sudo mkdir /usr/local/packer
WORKDIR /usr/local/packer
RUN sudo unzip /tmp/packer_0.7.5_linux_amd64.zip
ENV PATH /usr/local/packer/:$PATH
RUN mkdir /usr/local/rosco
COPY *.json /usr/local/rosco/
WORKDIR /usr/local/rosco
