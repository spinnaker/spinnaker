# Cloud Build authentication migration

Cloud Build accounts use the shared [Kork Google authentication contract](../../kork/kork-google-auth/README.md).

```yaml
gcb:
  accounts:
    - name: builds
      project: workload-project
      auth:
        impersonateServiceAccount: builder@identity-project.iam.gserviceaccount.com
```

Omit `auth` for ADC, or set `auth.credentialsFile` to select a credential file. Both fields can
be configured to request file-backed impersonation. Resource project, region, account permissions,
and client behavior remain independent of authentication. Cloud Build API scopes are applied
regardless of credential source.

## Breaking change

`gcb.accounts[].jsonKey` is removed. Despite its old name, it was a file path, not inline JSON.
Move that path to `gcb.accounts[].auth.credentialsFile`. The old field is rejected, not aliased,
including when supplied alongside the new authentication block.

This migration concerns Igor's Cloud Build API clients. Echo's Cloud Build event subscriptions
are migrated with Echo's Pub/Sub adapter. Jenkins authentication is unchanged.

## Validation

Run in the repository's existing Docker compilation environment:

```sh
./gradlew :igor:igor-web:test --tests 'com.netflix.spinnaker.igor.gcb.*'
./gradlew :igor:igor-web:spotlessCheck
```

The Cloud Build integration tests need Testcontainers access to a Docker daemon and its
published ports. The account-factory tests can run without that additional access:

```sh
./gradlew :igor:igor-web:test \
  --tests com.netflix.spinnaker.igor.gcb.GoogleCloudBuildAccountFactoryTest
```
