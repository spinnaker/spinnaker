FROM python:2.7

WORKDIR /usr/src/app

RUN git clone https://github.com/google/citest.git && cd citest && pip install -r requirements.txt

ADD requirements.txt .
RUN pip install -r requirements.txt

ADD https://downloads.dcos.io/binaries/cli/linux/x86-64/dcos-1.8/dcos /usr/src/app/bin/dcos
RUN chmod +x /usr/src/app/bin/dcos

ENV PYTHONPATH=.:spinnaker
ENV PATH="${PATH}:/usr/src/app/bin"

ENTRYPOINT ["python"]
