# Artifact Storage

Artifact Storage is a feature that allows for embedded artifacts to be
persisted to some storage, eg the S3ArtifactStore. Spinnaker keeps a history,
which is called the pipeline context, that contains everything that an
execution had done. Pipeline contexts can be very large, especially with
complicated pipelines, and size can be further increased when users have large
artifacts. Spinnaker will duplicate
these artifacts whenever any stage uses any of those artifacts. Using an
artifact store reduces this overhead by providing a reference link of,
`ref://<spinnaker-application>/<content-hash>`. This reduces the context size
tremendously, but will vary depending on the size of the pipeline, as well as
how that artifact is used, but we've seen improvements of 80% for some
pipelines.

## Architecture

                                 +-----------+
                                 |           |
                                 |   Orca    |
                                 |           |
                                 +-----------+
                                   |        ^
                                   |        | (outgoing artifact compressed)
                                   |        +----------------------+
                                   | (bake request)                |
                                   +---------------------+         |
                                                         v         |
        +---------------+            (fetch)           +-------------+
        |               |<-----------------------------|             |
        |  Clouddriver  |                              |  Rosco      |
        |               |                              |             |
        |  s3 get       |   (full artifact returned)   |  s3 stores  |
        |  artifacts    |----------------------------->|  artifacts  |
        +---------------+                              +-------------+


Artifact storage is divided into two operations of get and store, and there are
primarily two services that utilize each of these operations. Further the
artifact storage system relies on Spring's (de)serializing to call these
operations to limit the amount of code changes needed within these services.

When bootstrapping Spring we add in custom bean serializers and deserializers to
handle storage or retrieval of an artifact.

Rosco is primarily used for baking artifacts which will generate something
deployable.  When Rosco responds to a bake request, the custom serializer
injected in Rosco at startup stores the artifact and returns a `remote/base64`
artifact instead of the usual `embedded/base64`.

Clouddriver, for this document, handles mostly deployment and has some endpoints
regarding artifacts. It does do a little more than this, but we only care about
these two particular operations. When any request comes in, Spring will use its
custom deserializers to expand any artifact in its payload since any request
with artifacts, probably wants to do some operation on those artifacts. Further
Clouddriver also allows for fetching of artifacts. Orca and Rosco both make
calls to the `/artifact/fetch` endpoint. Where Rosco uses it to fetch an
artifact to be baked, and Orca uses it primarily when dealing with deploy
manifests. When a request is sent to the fetch endpoint, Clouddriver will always
return the full `embedded/base64` artifact back to the service. It is up to the
service receiving the artifact to compress it. Luckily, for Orca, we don't have
to worry about compression, since this no longer becomes an artifact, but a
manifest instead.

Orca is a special case as it mostly does orchestration, but does cause some
artifacts to be duplicated when handling the expected artifact logic. We inject
some logic to handle the duplication along with ensuring that matching against
expected artifacts still works properly. So if a `embedded/base64` type needs to
match against a `remote/base64` type, Orca will use the artifact store to
retrieve that artifact, and do the comparison. In addition, Orca will store any
expected artifacts, to limit the context size.

Orca also handles SpEL evaluation which means our new `remote/base64` type
should be backwards compatible with existing pipelines. To ensure this, we
utilized the Spring converters, and injected our own custom converter that will
check if some `String` is a remote base64 URI, and if it is, retrieve it.

## Configuration

To enable artifact storage, simple add this to your `spinnaker-local.yml` file

```yaml
artifact-store:
  type: s3
  s3:
    enabled: true
    bucket: some-artifact-store-bucket
```

### Rosco and Helm

If any pipelines are passing artifact references to bake stages as a parameter,
enabling this field will allow those URIs to be expanded to the full
references:

```yaml
artifact-store:
  type: s3
  helm:
    expandOverrides: true
```

## Storage Options

### S3

[S3](https://aws.amazon.com/s3/) is an object store provided by AWS. The
current S3ArtifactStore implementation provides various ways to authenticate
against AWS.

```yaml
artifact-store:
  type: s3
  s3:
    enabled: true
    profile: dev # if you want to authenticate using a certain profile
    region: us-west-2 # allows for specified regions
    bucket: some-artifact-store-bucket
```

While the implementation is S3 specific, this does not limit usages of other S3
compatible storage engines. For example, something like
[SeaweedFS](https://github.com/seaweedfs/seaweedfs) can be used to test locally.
with

## Local Testing

To test the artifact store locally, we will use SeaweedFS. To start the storage simply run
`docker run -p 8333:8333 chrislusf/seaweedfs server -s3`

Next enable the configuration

```yaml
artifact-store:
  type: s3
  s3:
    enabled: true
    url: http://localhost:8333 # this URL will be used to make S3 API requests to
    bucket: some-artifact-store-bucket
```

Start Spinnaker, and you should see reference links in your pipeline contexts.
