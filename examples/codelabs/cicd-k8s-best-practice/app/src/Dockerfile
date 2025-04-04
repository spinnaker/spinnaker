FROM golang

ADD . /go/src/spinnaker.io/demo/demo

RUN go install spinnaker.io/demo/demo

ADD ./content /content

ENTRYPOINT /go/bin/demo
