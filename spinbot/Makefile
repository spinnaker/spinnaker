PROJECT=spinnaker-marketplace
IMAGE=gcr.io/${PROJECT}/spinbot

BRANCH := $(shell git rev-parse --abbrev-ref HEAD)

init:
	python3 -m pip install -r requirements.txt

docker:
	# Can only use 1 --tag (-t) tag via 'gcloud builds submit', otherwise last one wins.
	gcloud builds submit . \
		--project ${PROJECT} \
		-t ${IMAGE}:${BRANCH}-latest

.PHONY: init docker
