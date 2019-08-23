# Front50

[![Build Status](https://api.travis-ci.org/spinnaker/front50.svg?branch=master)](https://travis-ci.org/spinnaker/front50)

Front50 is the system of record for all Spinnaker metadata, including: application, pipeline and service account configurations.

All metadata is durably stored and served out of an in-memory cache.

## Internals

### Persistence

The following storage backends are supported:

- Amazon S3
- Google Cloud Storage
- Redis
- [SQL](https://github.com/spinnaker/front50/blob/master/front50-sql/src/main/kotlin/com/netflix/spinnaker/front50/model/SqlStorageService.kt) - _recommended_

`SQL` is a cloud agnostic storage backend that offers strong read-after-write consistency and metadata versioning.


### Metadata

The following types are represented in Front50 ([data models](https://github.com/spinnaker/front50/tree/master/front50-core/src/main/groovy/com/netflix/spinnaker/front50/model)):

| *Type* | *Description* |
| ------ | ------------- |
| Application | Defines a set of commonly named resources managed by Spinnaker (metadata includes name, ownership, description, source code repository, etc.). |
| Application Permission | Defines the group memberships required to read/write any application resource. |
| Entity Tags | Provides a general purpose and cloud agnostic tagging mechanism. |
| Notification | Defines application-wide notification schemes (email, slack and sms). |
| Pipeline | Defines a reusable delivery workflow (exists within the context of a specific application). |
| Pipeline Strategy | Defines a custom deployment strategy (exists within the context of a specific application). |
| Project | Provides a (many-to-many) grouping mechanism for multiple applications. |
| Service Account | Defines a system identity (with group memberships) that can be associated with one or more pipeline triggers. |


### Domain

We strive to make it easy to introduce additional metadata attributes; models are simple objects and serialized to `JSON` at persistence time.

Migrators for non-trivial attribute changes are supported via implementations of the `Migration` interface.

The `StorageServiceSupport` class maintains an in-memory cache for each metadata type and delegates read/write operations to a storage backend-specific `StorageService` implementation.


### Relevant Metrics

The following metrics are relevant to overall `Front50` health:

| *Metric* | *Description* | *Grouping* |
| `controller.invocations` (count) | Invocation counts. | `controller` |
| `controller.invocations` (average) | Invocation times. | `controller`, `statusCode` and `method` |
| `controller.invocations` (count) | All 5xx responses. | `controller`, `statusCode` and `status` = `5xx` |

## Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:
```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8180.  The JVM will _not_ wait for
the debugger to be attached before starting Front50; the relevant JVM arguments can be seen and
modified as needed in `build.gradle`.

[0]:http://projects.spring.io/spring-boot/


### Modular builds

By default, Front50 is built with all storage providers included. To build only a subset of
providers, use the `includeProviders` flag:

```
./gradlew -PincludeProviders=s3,gcs clean build
```

You can view the list of all providers in `gradle.properties`.

### Working Locally

The tests are setup to only run if needed services are available.

#### S3
S3 TCK only run if there is a s3 proxy available at 127.0.0.1:9999

This can be provided with the following command:
```bash
docker run -d -p9999:80 \
  --env S3PROXY_AUTHORIZATION="none" \
  --env JCLOUDS_PROVIDER="filesystem" \
  --env JCLOUDS_IDENTITY="remote-identity" \
  --env JCLOUDS_CREDENTIAL="remote-credential" \
  andrewgaul/s3proxy
```

When running the S3 TCK via an IDE make sure to have env `AWS_ACCESS_KEY_ID` and `AWS_SECRET_KEY` set to `null` otherwise the tests will timeout, the gradle test task is already configured this way.
