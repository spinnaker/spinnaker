#!/usr/bin/env bash

gcloud builds submit -q --tag gcr.io/{%PROJECT_ID%}/backend .
