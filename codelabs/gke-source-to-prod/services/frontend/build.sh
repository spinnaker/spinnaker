#!/usr/bin/env bash

gcloud container builds submit -q --tag gcr.io/{%PROJECT_ID%}/frontend .
