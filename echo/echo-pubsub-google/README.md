# Google Pub/Sub authentication migration

Pub/Sub publishers, subscribers, and Cloud Build event subscriptions use the shared
[Kork Google authentication contract](../../kork/kork-google-auth/README.md).

```yaml
pubsub:
  google:
    subscriptions:
      - name: events
        project: workload-project
        subscriptionName: events
        auth:
          impersonateServiceAccount: subscriber@identity-project.iam.gserviceaccount.com
```

The same `auth` block is available under `pubsub.google.publishers[]` and `gcb.accounts[]`.
Omit it for ADC, set `credentialsFile` for file credentials, and optionally combine that file
with `impersonateServiceAccount` for file-backed impersonation. Echo propagates Cloud Build
account authentication into the generated Pub/Sub subscription.

## Breaking changes

| Removed field | Replacement |
| --- | --- |
| `pubsub.google.subscriptions[].jsonPath` | `pubsub.google.subscriptions[].auth.credentialsFile` |
| `pubsub.google.publishers[].jsonPath` | `pubsub.google.publishers[].auth.credentialsFile` |
| `gcb.accounts[].jsonKey` (formerly a file path) | `gcb.accounts[].auth.credentialsFile` |

Removed fields are rejected, not aliased, including when mixed with the new configuration.
The SDK's Pub/Sub service scopes are applied for all credential sources. Credential-loading
failures abort publisher/subscriber construction instead of leaving a null client or credentials.
No failure falls back to another identity.

Names, resource projects, topics, subscriptions, message formats, and batching settings are
unchanged. Configure IAM permissions separately from authentication.

## Validation

Run in the repository's existing Docker compilation environment:

```sh
./gradlew :echo:echo-pubsub-google:test :echo:echo-pubsub-google:spotlessCheck
```

Tests cover the shared-provider wiring, configuration binding, removed-field rejection,
credential failures, and propagation from Cloud Build accounts to generated subscriptions.
Live Pub/Sub access and Workload Identity/federation checks remain separate validation steps.
