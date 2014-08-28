Account Management of Spinnaker (AMOS)
---

Amos provides constructs for building configuration objects of cloud provider accounts. Credentials configuration objects are also responsible for delegating API-specific credential objects (like `AWSCredentials`) to consumers.

Additionally, the project provides support classes (see `YamlAccountCredentialsFactory`) for resolving `AccountCredentials` objects from sources of configuration. These objects can be retrieved through an `AccountCredentialsProvider` implementation, such as the provided `InMemoryAccountCredentialsProvider`.

License
---
ASL 2.0
