# Blue-Green Deployments on Google Kubernetes Engine with Spinnaker 

This directory contains the assets necessary to configure a simple blue-green pipeline
with Spinnaker on GKE.  

**Note**: this sample uses features only available in [Spinnaker 1.11](https://www.spinnaker.io/guides/user/kubernetes-v2/traffic-management/).  

* The `app` directory contains the source code for the small "Hello World"
  application used. The Docker image is available at
  `us-docker.pkg.dev/spinnaker-community/codelabs/helloworld:v1`.

* The `manifests` directory contains the Kubernetes that will be deployed to GKE
  before the pipeline runs for the first time. 

* The `pipelines` directory contains the blue-green pipeline JSON.