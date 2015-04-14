# Docker in docker.
FROM jpetazzo/dind
# There is a known issue with later versions of docker requiring us to stick with this version for now: https://github.com/mitchellh/packer/issues/1752
RUN sudo apt-get install -q -y lxc-docker-1.3.3
# The REPLACE_THIS_WITH_DOCKER_REGISTRY_HOST token on the next line should be replaced prior to running docker build.
RUN echo 'DOCKER_OPTS="$DOCKER_OPTS --insecure-registry REPLACE_THIS_WITH_DOCKER_REGISTRY_HOST:5000"' >> /etc/default/docker
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
