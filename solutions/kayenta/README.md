# Automated canary analysis on Kubernetes Engine with Spinnaker

This directory contains the assets necessary for the
[Automated canary analysis on Kubernetes Engine with Spinnaker](https://cloud.google.com/solutions/automated-canary-analysis-kubernetes-engine-spinnaker)
tutorial.

* The `app` directory contains the source code for the small "Hello World"
application used. The Docker image is published as `gcr.io/spinnaker-marketplace/sampleapp:latest`.
* The `ci` directory contains the code necessary for the
[continuous integration](https://concourse.dev.vicnastea.io/teams/main/pipelines/kayenta-gke-stackdriver)
of the solution.
* The `pipelines` directory contains the pipelines used in the solution.

## Commands to create the pipelines

These commands create the three pipelines used in the
[Automated canary analysis on Kubernetes Engine with Spinnaker](https://cloud.google.com/solutions/automated-canary-analysis-kubernetes-engine-spinnaker)
tutorial. They need to be run in the context of this tutorial. In particular,
they assume that you have access to the `gate` component of Spinnaker on `localhost:8080/gate`, without authentication.

### Create the "Simple deploy" pipeline

```
wget https://raw.githubusercontent.com/spinnaker/spinnaker/master/solutions/kayenta/pipelines/simple-deploy.json
curl -d@simple-deploy.json -X POST \
    -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/pipelines
```

### Create the "Canary Deploy" pipeline

```
wget https://raw.githubusercontent.com/spinnaker/spinnaker/master/solutions/kayenta/pipelines/canary-deploy.json
export PIPELINE_ID=$(curl \
    localhost:8080/gate/applications/sampleapp/pipelineConfigs/Simple%20deploy \
    | jq -r '.id')
jq '(.stages[] | select(.refId == "9") | .pipeline) |= env.PIPELINE_ID | (.stages[] | select(.refId == "8") | .pipeline) |= env.PIPELINE_ID' canary-deploy.json | \
    curl -d@- -X POST \
    -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/pipelines
```

### Create the "Automated Canary Deploy" pipeline

#### Spinnaker 1.10

This command assumes that Kayenta is enabled and configured as instructed in the
tutorial.

```
wget https://raw.githubusercontent.com/spinnaker/spinnaker/master/solutions/kayenta/pipelines/automated-canary-1-10.json
export PIPELINE_ID=$(curl \
    localhost:8080/gate/applications/sampleapp/pipelineConfigs/Simple%20deploy \
    | jq -r '.id')
export CANARY_CONFIG_ID=$(curl \
    localhost:8080/gate/v2/canaryConfig | jq -r '.[0].id')
jq '(.stages[] | select(.refId == "9") | .pipeline) |= env.PIPELINE_ID | (.stages[] | select(.refId == "8") | .pipeline) |= env.PIPELINE_ID | (.stages[] | select(.refId == "16") | .canaryConfig.canaryConfigId) |= env.CANARY_CONFIG_ID' automated-canary-1-10.json | \
    curl -d@- -X POST \
    -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/pipelines
```

#### Spinnaker 1.9

This command assumes that Kayenta is enabled and configured as instructed in the
tutorial.

```
wget https://raw.githubusercontent.com/spinnaker/spinnaker/master/solutions/kayenta/pipelines/automated-canary-1-9.json
export PIPELINE_ID=$(curl \
    localhost:8080/gate/applications/sampleapp/pipelineConfigs/Simple%20deploy \
    | jq -r '.id')
export CANARY_CONFIG_ID=$(curl \
    localhost:8080/gate/v2/canaryConfig | jq -r '.[0].id')
jq '(.stages[] | select(.refId == "9") | .pipeline) |= env.PIPELINE_ID | (.stages[] | select(.refId == "8") | .pipeline) |= env.PIPELINE_ID | (.stages[] | select(.refId == "11") | .canaryConfig.canaryConfigId) |= env.CANARY_CONFIG_ID' automated-canary-1-9.json | \
    curl -d@- -X POST \
    -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/pipelines
```
