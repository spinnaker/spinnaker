# GKE base codelab

This folder provides shared scripts for creating codelabs on GKE + Spinnaker.
Developers of codelabs are provided hooks to install & configure extra
infrastructure, Spinnaker pipelines, and executables depending on the needs of
their codelab.

## Developing a codelab

The codelab author needs to provide the following:

```
codelab-folder/
  publish.sh            # an executable to publish the overrides needed by this
                        # codelab. example below.
  overrides/
    properties          # a script to be run to set the environment variables.
    predeploy.sh        # a script to be run before spinnaker is deployed.
    postdeploy.sh       # a script to be run after spinnaker is deployed.
    precleanup.sh       # a script to cleanup resources before spinnaker is
                        # deleted.
```

## `publish.sh` example

Given a codelab named `$CODELAB`:

```bash
#!/usr/bin/env bash

tar -cvf overrides.tar -C overrides .

gsutil cp overrides.tar gs://gke-spinnaker-codelab/$CODELAB/overrides.tar

rm overrides.tar
```
