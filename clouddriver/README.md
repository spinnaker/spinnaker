Spinnaker Cloud Provider Service
------------------------------------
[![Build Status](https://github.com/spinnaker/spinnaker/actions/workflows/clouddriver.yml/badge.svg)](https://github.com/spinnaker/spinnaker/actions/workflows/clouddriver.yml)

This service is the main integration point for Spinnaker cloud providers like AWS, GCE, CloudFoundry, Azure etc.
It lives in this monorepo at `clouddriver/`; commands below are run from the monorepo root, not
from within this directory — see [CLAUDE.md](../CLAUDE.md) for the full set of build/test commands.

### Kubernetes CLI versions

Both `Dockerfile.slim` and `Dockerfile.ubuntu` bundle one pinned kubectl binary per minor version,
at `/usr/local/bin/kubectl-1.<minor>`, for Linux amd64 and arm64. The unversioned `kubectl` symlink
uses the default selected in [kubectl-versions.json](kubectl-versions.json), currently minor
**1.36**, replacing the previous 1.30 default.

The packaged versions cover all minors in [Amazon EKS standard or extended support](https://docs.aws.amazon.com/eks/latest/userguide/kubernetes-versions.html)
as of October 9, 2026. Version 1.30 is retained as an explicit compatibility option for existing deployments.

The shared JSON file drives both image builds and the Kubernetes integration tests:

- `defaultMinor` selects the unversioned image binary and the default local test client and cluster.
- `versions` maps each minor to its pinned `kubectl` patch release. Every entry is packaged.
- `kubernetesImage` optionally selects a digest-pinned kind node image for integration testing.
  Entries without it, such as the retained 1.30 client, are packaged without adding CI jobs.
- `kindVersion` pins kind for the integration-test harness. A version entry can override it
  when its node image was published for an older kind release.
- `testDefaultAgainstAll` adds the default client against every configured test cluster.
  Matching clients always run, and duplicate combinations are omitted. With the current seven
  clusters, `true` produces 13 integration jobs and `false` produces seven.

Default-client tests against distant cluster versions exercise compatibility beyond Kubernetes'
supported one-minor skew; passing them does not extend the upstream support policy.

Kubernetes [supports kubectl within one minor version of the API server](https://kubernetes.io/releases/version-skew-policy/#kubectl).
The default 1.36 client covers servers at 1.35–1.37; select a matching bundled client for older clusters.
Clouddriver does not automatically select kubectl based on the cluster version. Set
`kubernetes.kubectl.executable` globally or `kubectlExecutable` on an individual account, which takes
precedence over the global setting:

```yaml
kubernetes:
  kubectl:
    executable: /usr/local/bin/kubectl-1.36
  accounts:
    - name: eks-1-31
      kubectlExecutable: /usr/local/bin/kubectl-1.31
      # Add the account's existing context, credentials, and other settings here.
```

Image builds verify each download against Kubernetes' published SHA-512 checksum and stop on a
download or checksum failure. Update `kubectl-versions.json` when refreshing client patches,
the supported minor-version range, or the integration-test coverage. For local test commands
and overrides, see the [integration-test README](clouddriver-kubernetes/src/integration/README.md).

### Developing with Intellij

To configure this repo as an Intellij project, run `./gradlew idea` from the monorepo root.

Some of the modules make use of [Lombok](https://projectlombok.org/), which will compile correctly on its own. However, for Intellij to make sense of the Lombok annotations, you'll need to install the [Lombok plugin](https://plugins.jetbrains.com/plugin/6317-lombok-plugin) as well as [check 'enable' under annotation processing](https://www.jetbrains.com/help/idea/configuring-annotation-processing.html#3).

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true` (run from the monorepo root):
```
./gradlew clouddriver -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 7102.  The JVM will _not_ wait for
the debugger to be attached before starting Clouddriver; the relevant JVM arguments can be seen and
modified as needed in `build.gradle`.
