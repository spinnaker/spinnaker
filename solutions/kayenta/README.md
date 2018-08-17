# Automated canary analysis on Kubernetes Engine with Spinnaker

This directory contains the assets necessary for the
[Automated canary analysis on Kubernetes Engine with Spinnaker](https://cloud.google.com/solutions/automated-canary-analysis-kubernetes-engine-spinnaker)
solution.

* The `app` directory contains the source code for the small "Hello World"
application used. The Docker image is published as `gcr.io/spinnaker-marketplace/sampleapp:latest`.
* The `ci` directory contains the code necessary for the
[continuous integration](https://concourse.dev.vicnastea.io/teams/main/pipelines/kayenta-gke-stackdriver)
of the solution.
* The `pipelines` directory contains the pipelines used in the solution.
