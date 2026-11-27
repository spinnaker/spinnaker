# Shared Google authentication

`kork-google-auth` owns credential loading, API scoping, and optional IAM service-account
impersonation. It has no Spring lifecycle or secret-engine dependency. Integrations expose
`GoogleAuthProperties` as `auth` and call `GoogleCredentialsFactory.create(auth, scopes)`.
Retain the returned credentials in the API client; the Google authentication library owns
access-token caching and refresh. Keep client construction, retries, timeouts, and API scopes
in the integration.

## Configuration standard

```yaml
auth:
  credentialsFile: /mounted/google-credentials.json
  impersonateServiceAccount: caller@identity-project.iam.gserviceaccount.com
```

Both fields are optional. There is no `source`, enablement, or Workspace-delegation flag.

| Settings | Identity selection |
| --- | --- |
| Neither (including an omitted `auth` block) | Application Default Credentials (ADC) |
| `credentialsFile` | Credentials loaded from that file |
| `impersonateServiceAccount` | ADC impersonates the target service account |
| Both | File credentials impersonate the target service account |

ADC retains Google's discovery rules, including `GOOGLE_APPLICATION_CREDENTIALS`, local
ADC configuration, and workload-provided identities. Omitting the file setting does not by
itself guarantee keyless authentication. GKE Workload Identity and federation are configured
outside Spinnaker, not through a separate flag.

An explicitly configured blank file or invalid target is rejected. A failed source load or
failed impersonation never falls back to another identity. The impersonation source is scoped
for IAM (`cloud-platform`); the target gets the caller's API scopes and a 3600-second token
lifetime. Resource projects remain separate from the impersonated identity's project.

Credential files must be trusted operator configuration. They are parsed by the Google auth
library and can include supported federation configurations, not just service-account keys.
Do not accept arbitrary uploaded credential configurations or log their contents, tokens, or
credential objects. Integrations with existing secret-aware file resolution can supply a
`CredentialFileOpener`; the shared factory owns and closes the returned stream.

## Consumer adoption

Adding this module does not change any service configuration by itself. Service migrations
are separate changes, with their own configuration binding, client wiring, tests, and migration
instructions. Do not use the new block in a service until that service's adapter is migrated.

Migrated consumers must reject removed authentication fields rather than silently ignoring
or aliasing them. Preserve account isolation, resource-project selection, file-resolution hooks,
and API client behavior. Apply the consumer's required scopes regardless of credential source.
Workspace permissions and IAM impersonation permissions are distinct; this module implements
neither Workspace role assignment nor domain-wide delegation.

When migrating a consumer that previously gave file credentials precedence over impersonation,
do not blindly copy both old settings: both fields in the new contract explicitly request
file-backed impersonation. Preserving the old effective identity requires migrating only the
file setting.

## Validation

Use the repository's Gradle wrapper. For Docker validation, build the existing
`fiat/Dockerfile.compile` without modifying it and mount the repository at `/workspace`:

```sh
docker build -t spinnaker-google-auth-compile - < fiat/Dockerfile.compile
docker run --rm -v "$PWD:/workspace" -w /workspace spinnaker-google-auth-compile \
  ./gradlew --no-daemon --max-workers=2 \
  :kork:kork-google-auth:test :kork:kork-google-auth:spotlessCheck
```

Kork changes fan out to backend consumers according to `.github/dependencies.yml`. Targeted
checks are the first validation step, not a substitute for downstream CI or live tests of
Workload Identity, federation, IAM permission denial, and Workspace role assignments.
