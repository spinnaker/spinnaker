### Kubernetes provider integration tests

#### Run

From the command line
```shell
./gradlew :clouddriver-kubernetes:integrationTest
```

From Intellij: Individual tests can be run or debugged by clicking the corresponding icon next to the test name within the IDE.


#### How they work

The tests use spring test framework to start clouddriver on a random port, reading configuration from the `clouddriver.yml` config file in the resources folder. They use testcontainers framework for starting a real mysql server in a docker container, and use [kind](https://kind.sigs.k8s.io) for starting a real kubernetes cluster where deployments will happen.

Kind and kubectl binaries are downloaded to `clouddriver-kubernetes/build/it` folder, and also the `kubeconfig` file for connecting to the test cluster is generated there, which runs as a docker container started by kind.

