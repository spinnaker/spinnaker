FROM golang

ADD . /go/src/spinnaker.io/demo/k8s-demo

RUN go get -u cloud.google.com/go/compute/metadata

RUN go get -u golang.org/x/oauth2/google

RUN go get -u google.golang.org/api/monitoring/v3

RUN go install spinnaker.io/demo/k8s-demo

ENTRYPOINT /go/bin/k8s-demo
