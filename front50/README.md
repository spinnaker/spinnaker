# Front50

[![Build Status](https://github.com/spinnaker/spinnaker/actions/workflows/front50.yml/badge.svg)](https://github.com/spinnaker/spinnaker/actions/workflows/front50.yml)

Front50 is the system of record for all Spinnaker metadata, including: application, pipeline and service account configurations.

All metadata is durably stored and served out of an in-memory cache.

## Internals

### Persistence

Front50 persists metadata in [SQL](https://github.com/spinnaker/spinnaker/blob/main/front50/front50-sql/src/main/kotlin/com/netflix/spinnaker/front50/model/SqlStorageService.kt), the only supported
storage backend. The Amazon S3, Google Cloud Storage, Redis, Azure Blob, Oracle Object Storage and
OpenStack Swift metadata backends have been removed. See the migration guide:

https://spinnaker.io/docs/setup/productionize/persistence/front50-sql/

`SQL` is a cloud agnostic storage backend that offers strong read-after-write consistency and metadata versioning.

> **Note:** S3 *plugin-binary* storage (`spinnaker.s3.plugin-storage`) is separate from
> Front50 metadata storage and is still supported. Orca's artifact store
> also continues to support S3.

### Metadata

The following types are represented in Front50 ([data models](https://github.com/spinnaker/spinnaker/tree/main/front50/front50-core/src/main/groovy/com/netflix/spinnaker/front50/model)):

|         *Type*         |                                                                 *Description*                                                                  |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Application            | Defines a set of commonly named resources managed by Spinnaker (metadata includes name, ownership, description, source code repository, etc.). |
| Application Permission | Defines the group memberships required to read/write any application resource.                                                                 |
| Entity Tags            | Provides a general purpose and cloud agnostic tagging mechanism.                                                                               |
| Notification           | Defines application-wide notification schemes (email, slack and sms).                                                                          |
| Pipeline               | Defines a reusable delivery workflow (exists within the context of a specific application).                                                    |
| Pipeline Strategy      | Defines a custom deployment strategy (exists within the context of a specific application).                                                    |
| Project                | Provides a (many-to-many) grouping mechanism for multiple applications.                                                                        |
| Service Account        | Defines a system identity (with group memberships) that can be associated with one or more pipeline triggers.                                  |

### Domain

We strive to make it easy to introduce additional metadata attributes; models are simple objects and serialized to `JSON` at persistence time.

Migrators for non-trivial attribute changes are supported via implementations of the `Migration` interface.

The `StorageServiceSupport` class maintains an in-memory cache for each metadata type and delegates read/write operations to a storage backend-specific `StorageService` implementation.

### Relevant Metrics

The following metrics are relevant to overall `Front50` health:

|              *Metric*              |   *Description*    |                   *Grouping*                    |
| ---------------------------------- | ------------------ | ----------------------------------------------- |
| `controller.invocations` (count)   | Invocation counts. | `controller`                                    |
| `controller.invocations` (average) | Invocation times.  | `controller`, `statusCode` and `method`         |
| `controller.invocations` (count)   | All 5xx responses. | `controller`, `statusCode` and `status` = `5xx` |

## Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true` (run from the monorepo root):
```
./gradlew front50 -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8180.  The JVM will _not_ wait for
the debugger to be attached before starting Front50; the relevant JVM arguments can be seen and
modified as needed in `build.gradle`.

[0]:http://projects.spring.io/spring-boot/
