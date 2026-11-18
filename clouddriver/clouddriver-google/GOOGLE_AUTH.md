# Google Compute authentication migration

The Google Compute provider uses the shared [Kork Google authentication contract](../../kork/kork-google-auth/README.md).

- `google.accounts[].jsonPath` becomes `google.accounts[].auth.credentialsFile`.
- `serviceAccountId` and `serviceAccountProject` become a full service-account email in
  `google.accounts[].auth.impersonateServiceAccount`.
- Removed fields are rejected in static configuration and dynamic account definitions.
- Both new fields mean file-backed impersonation, unlike the old file-wins precedence.
  To preserve the old effective identity when both old settings were supplied, migrate only
  the file setting.

Omit `auth` for ADC. Resource projects and account permissions remain independent. Credential
files retain the existing `ConfigFileService` resolution. Compute scope is used for every source;
previously file credentials used Compute scope while ADC/impersonation used cloud-platform.

The external Terraform snapshot-restore path reads the renamed file field but explicitly rejects
impersonation until its provider adapter is validated. Existing snapshot-restore limitations for
non-file credentials remain. TODO: track and implement that adapter follow-up; no tracking issue
has been assigned yet.

Cloud Run, App Engine, and GCS artifact authentication are not migrated by this change.

Run `./gradlew :clouddriver:clouddriver-google:test :clouddriver:clouddriver-google:spotlessCheck`
in the repository's existing Docker compilation environment. Live Google authentication and
broader downstream validation remain separate checks.
