PROJECT=spinnaker-marketplace
IMAGE=gcr.io/${PROJECT}/spinbot

HASH := $(shell git rev-parse HEAD)

init:
	python3 -m pip install -r requirements.txt

docker:
	gcloud builds submit . \
		--project ${PROJECT} \
		-t ${IMAGE}:${HASH} \
		-t ${IMAGE}:latest

.PHONY: init docker
