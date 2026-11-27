# Google Groups authentication migration

This integration uses the shared [Kork Google authentication contract](../../kork/kork-google-auth/README.md).

```yaml
auth:
  group-membership:
    google:
      domain: example.com
      auth:
        impersonateServiceAccount: group-reader@identity-project.iam.gserviceaccount.com
```

Omit the inner `auth` block for direct ADC. Set `auth.credentialsFile` inside the Google
configuration to select an explicit credential file; optionally combine it with impersonation.
The provider retains credentials across group lookups so the SDK can cache and refresh tokens.

## Breaking changes

- `credentialPath` is removed; use the inner `auth.credentialsFile`.
- `adminUsername` is removed. Provision direct Workspace role assignments before upgrading;
  domain-wide delegation is no longer supported. Neither ADC nor IAM impersonation grants
  Workspace permissions automatically.
- Removed fields are rejected, including when mixed with the new configuration.
- Legacy Fiat ignored `credentialPath` without `adminUsername`. The new file setting is always
  honored, so that configuration requires an explicit identity-selection decision during migration.

`domain`, role-source settings, concurrency, and indirect-group expansion remain separate from
authentication. Directory group-read scope is always requested; Cloud Identity group-read scope
is also requested when indirect-group expansion is enabled. That mode uses Cloud Identity's
transitive-group lookup.

## Validation

Run with the repository's existing Docker compilation environment:

```sh
./gradlew :fiat:fiat-google-groups:test \
  --tests com.netflix.spinnaker.fiat.roles.google.GoogleAuthenticationTest
./gradlew :fiat:fiat-google-groups:spotlessCheck
```

Also run the complete module suite and downstream checks according to `.github/dependencies.yml`.
Live Workspace role-assignment and IAM permission-denial checks are separate from unit tests.
