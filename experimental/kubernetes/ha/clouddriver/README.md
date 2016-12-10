# Clouddriver

[Clouddriver](https://github.com/spinnaker/clouddriver) is the main integration
point with each cloud provider. This is where most of the config lives that's
required to get clouddriver talking to your cloud provider of choice.

For best scaling, it is broken up by functionality into mutate, read-only,
and caching groups. The functionality is designated by config read at startup,
and is described in more detail in each subfolder.
