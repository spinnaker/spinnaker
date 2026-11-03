### Kubernetes provider integration tests

#### Run

From the monorepo root:

```shell
./gradlew :clouddriver:clouddriver-kubernetes:integrationTest
```

This runs the default client against its matching cluster, using
[kubectl-versions.json](../../../kubectl-versions.json). Override any selection with Gradle
properties:

```shell
./gradlew :clouddriver:clouddriver-kubernetes:integrationTest \
  -Pkubernetes-image='kindest/node:v1.35.8@sha256:07b2536e30b803ed61d1677a79df6115f798ce64c80f9e22f6ed45afd09323c0'
```

`-Pkind-version=<version>` overrides the shared kind pin. A configured cluster image uses its
entry's kind pin when present, otherwise the top-level pin. An unlisted cluster image uses the
top-level pin unless overridden. Changing just the client does not change the selected cluster.
`-Pkubectl-version=<version>` overrides the default client.

CI reads the same JSON file and runs only the default kubectl client against the matching server
minor and the two adjacent minors, following Kubernetes' supported one-minor client/server skew.
The current matrix is kubectl 1.36.5 against Kubernetes 1.35, 1.36, and 1.37. All three server
minors must have a `kubernetesImage`; a missing image fails matrix preparation. Other entries
remain available for image packaging and local overrides without adding CI jobs.

From IntelliJ, run or debug the Gradle `integrationTest` task to use the shared configuration.
Direct JUnit runs need the same `IT_BUILD_HOME`, `IMAGE`, `KUBECTL_VERSION`, and `KIND_VERSION`
environment variables that the Gradle task supplies.


#### How they work

The tests use spring test framework to start clouddriver on a random port, reading configuration from the `clouddriver.yml` config file in the resources folder. They use testcontainers framework for starting a real mysql server in a docker container, and use [kind](https://kind.sigs.k8s.io) for starting a real kubernetes cluster where deployments will happen.

Kind and kubectl binaries are downloaded to the `clouddriver-kubernetes/build/it` folder as
`kind-<version>` and `kubectl-<version>`, so changing versions does not reuse a different release
from a previous run. The `kubecfg.yml` file for connecting to the test cluster is generated there.
The cluster runs as a Docker container started by kind.
