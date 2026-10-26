# Publishing to Maven Central

As of 3/2021, Spinnaker projects push jars to [Maven
Central](https://repo.maven.apache.org/maven2/io/spinnaker/).

Spinnaker uploads through Sonatype's OSSRH staging compatibility API. Closing a
staging repository transfers it to the Central Publisher Portal for validation
and publication. Publication and Maven Central visibility are separate from the
initial artifact upload and may take additional time.

The jars are signed with a PGP key owned by `toc@spinnaker.io` with the
fingerprint `9C88 1F6B 9595 3116 4FE3  CCD2 6A3E 0DDE A960 2C12`.

The [Central Publisher Portal](https://central.sonatype.com/publishing/deployments)
shows transferred deployments. Open staging repositories can be inspected with
the [OSSRH staging API](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/).

## Secrets

There are four org-level GitHub secrets available to publish to Nexus:
`NEXUS_USERNAME`, `NEXUS_PASSWORD`, `NEXUS_PGP_SIGNING_KEY`, and
`NEXUS_PGP_SIGNING_PASSWORD`.

Ask the Spinnaker TOC for access to the publishing account. API operations use
the Central user-token username/password stored in the existing Nexus secrets.

## Publishing with Gradle

You can publish to Nexus without releasing to Maven Central:

```shell
./gradlew -P nexusPublishEnabled=true publishToNexus
```

This stages a release in an `open` state. It becomes visible in the Central
Publisher Portal after transfer from the compatibility service. See the recovery
procedure below if closing or releasing fails.

You can also publish and close a release programmatically:

```shell
./gradlew -P nexusPublishEnabled=true publishToNexus closeAndReleaseNexusStagingRepository
```

## Recovering a partially published release

An artifact upload and a Maven Central publication are separate operations.
The OSSRH compatibility service transfers a staging repository to the
[Central Publisher Portal](https://central.sonatype.com/publishing/deployments)
when it closes. An HTTP 524 can leave the client uncertain whether that
transfer completed. Other services in the same Gradle invocation may already
have published successfully. Published Maven coordinates are immutable.

Use the **Recover Spinnaker Libraries** GitHub Actions workflow to finish
existing repositories. The workflow invokes the local
`.github/actions/recover-nexus-publication` composite action, with its recovery
implementation and tests kept alongside the other publishing actions. It never
runs Gradle, rebuilds libraries, drops repositories, or uploads individual Maven
artifacts again.

1. Record the original version and the failed staging repository IDs from the
   library job's `Created staging repository` or `findNexusStagingRepository`
   output. Keep the original release commit/tag for reference. Confirm that
   artifact uploads finished successfully; this workflow cannot repair an
   interrupted artifact upload.
2. Open **Actions → Recover Spinnaker Libraries → Run workflow** on a branch
   containing the recovery workflow. Supply the original `version` and only
   the affected `staging-repository-ids`, separated by spaces or commas.
   An upstream maintainer with access to the existing `NEXUS_USERNAME` and
   `NEXUS_PASSWORD` secrets must run it in `spinnaker/spinnaker`.
3. If a staging record is gone but its Portal deployment is present, supply
   `portal-deployment-ids` instead. Inspect those deployments in the Portal
   and confirm they belong to the original version before running recovery.
   A missing staging record alone is not evidence of successful publication.
4. Check the recovery job summary. Success requires **every selected deployment
   to reach `PUBLISHED`**. Verify the affected coordinates on
   [Maven Central](https://repo.maven.apache.org/maven2/io/spinnaker/), allowing
   for propagation after publication.
5. If recovery times out, dispatch it again with the same `version` and the
   previous recovery's `previous-run-id`. The workflow restores the
   `nexus-recovery-state` artifact, including existing deployment IDs and any
   uncertain transfer requests. Leave the ID inputs empty to resume the
   checkpoint's complete selection. Already published deployments are verified
   and skipped. Checkpoints are retained for 30 days; retain them locally if
   recovery must continue beyond that period.

The same helper can run locally from a reviewed checkout before the workflow is
available upstream. Supply `NEXUS_USERNAME` and `NEXUS_PASSWORD` through your
credentials manager, and set `STAGING_REPOSITORY_IDS` or `PORTAL_DEPLOYMENT_IDS`
to the selected IDs:

```shell
python3 .github/actions/recover-nexus-publication/recover_nexus_publication.py \
  --version 2026.3.2 \
  --checkpoint /path/to/retained/nexus-recovery-state.json
```

Keep that checkpoint file and use the same path on subsequent attempts. Recovery
uses the token credentials only; it does not need the PGP signing secrets.

The recovery workflow uses Sonatype's documented
[`publishing_type=portal_api`](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#post-to-manualuploadrepositoryrepository-key)
mode to transfer an open repository without waiting for validation inside a
single close request. It then uses the
[Portal status/publish APIs](https://central.sonatype.org/publish/publish-portal-api/)
to wait through `PENDING`, `VALIDATING`, and `PUBLISHING`, and publish a
`VALIDATED` deployment. A `PUBLISHED` deployment needs no further mutation.
Repository lookup uses `ip=any` so recovery works on a different GitHub runner.

Polling and transient HTTP/network failures use exponential backoff starting at
10 seconds and capped at 120 seconds, with jitter and numeric `Retry-After`
support. Each run has a 30-minute recovery deadline and remains failed if any
selected deployment is unfinished. A publish retry rechecks the same deployment's
status first. Validation errors (`FAILED`) and authentication errors stop that
target immediately; successful targets are retained.

If a transfer request receives a 524 or loses its connection, the checkpoint
records that it may have been accepted. Recovery polls for its deployment ID
without repeating the transfer. If no ID appears before the deadline, inspect
both the compatibility service and the Portal, and ask Sonatype support to
resolve the transfer. After confirming that no deployment was created and the
original repository remains open, a fresh run without `previous-run-id` can
retry that transfer. Do not discard the checkpoint merely to force retries.

If Portal validation fails, inspect the `errors` in the summary/checkpoint and
fix the reported metadata, signatures, or other validation problem. Preserve
failed deployments while investigating with Sonatype support. If neither the
original staging contents nor a Portal deployment can be recovered, retrying
cannot recreate them: publish only confirmed missing modules from the original
release commit and version, or issue a corrective release. Do not run the full
monorepo upload over Maven coordinates that already exist.

Recovery finishes Maven libraries and leaves the original workflow conclusion
unchanged. For 2026.3.2 the full release and metadata already completed, so
recovering the missing libraries completes this remediation. For future releases
that stop before release metadata is written, a release maintainer must resume
that work separately and preserve the original build's SBOM artifacts; this
workflow does not rerun the full release.

### 2026.3.2 recovery example

The [original library job](https://github.com/spinnaker/spinnaker/actions/runs/40009011613/job/120684269247)
reported these failed staging repositories:

| Service | Repository ID |
| --- | --- |
| Clouddriver | `b6b58aed9c738--583423d4-f0d9-4201-b4f0-71bd852f0eeb` |
| Kork | `b6b58aed9c738--e98c5da2-2fb9-418a-9c7a-446f61b48210` |
| Orca | `b6b58aed9c738--9c3c6616-9d70-4a16-8d3c-42f748458f17` |

For the first recovery attempt, set `version` to `2026.3.2` and supply these
three IDs as `staging-repository-ids`. Use their existing Portal deployment IDs
instead if the staging records are no longer available. The workflow must be
merged or backported to a trusted upstream branch before maintainers can dispatch
it; a fork PR cannot access upstream publishing secrets.
