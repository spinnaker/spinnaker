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
  -Pkubectl-version=1.31.14 \
  -Pkubernetes-image='kindest/node:v1.31.14@sha256:6f86cf509dbb42767b6e79debc3f2c32e4ee01386f0489b3b2be24b0a55aac2b'
```

`-Pkind-version=<version>` overrides the shared kind pin. A configured cluster image uses its
entry's kind pin when present, otherwise the top-level pin. An unlisted cluster image uses the
top-level pin unless overridden. Changing just the client does not change the selected cluster.

CI reads the same JSON file to run a matching client for every entry with a `kubernetesImage`.
Set `testDefaultAgainstAll` to `true` to also run the shared default client against each cluster,
or `false` to run only matching clients. The default/matching combination runs once.
Entries without a cluster image are packaged in Clouddriver but are not included in the matrix.
Tests outside Kubernetes' supported one-minor client/server skew are additional compatibility
checks and do not imply upstream support for those combinations.

From IntelliJ, run or debug the Gradle `integrationTest` task to use the shared configuration.
Direct JUnit runs need the same `IT_BUILD_HOME`, `IMAGE`, `KUBECTL_VERSION`, and `KIND_VERSION`
environment variables that the Gradle task supplies.


#### How they work

The tests use spring test framework to start clouddriver on a random port, reading configuration from the `clouddriver.yml` config file in the resources folder. They use testcontainers framework for starting a real mysql server in a docker container, and use [kind](https://kind.sigs.k8s.io) for starting a real kubernetes cluster where deployments will happen.

Kind and kubectl binaries are downloaded to the `clouddriver-kubernetes/build/it` folder as
`kind-<version>` and `kubectl-<version>`, so changing versions does not reuse a different release
from a previous run. The `kubecfg.yml` file for connecting to the test cluster is generated there.
The cluster runs as a Docker container started by kind.
