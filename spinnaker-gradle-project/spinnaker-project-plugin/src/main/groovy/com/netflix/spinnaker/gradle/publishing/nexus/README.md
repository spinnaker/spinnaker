# Publishing to Maven Central

As of 3/2021, Spinnaker projects push jars to [Maven
Central](https://repo.maven.apache.org/maven2/io/spinnaker/). 

Sonatype hosts instances of Nexus for OSS projects. Releases published through
Nexus are synced with Maven Central. The sync takes about 10 minutes to
complete.

The jars are signed with a PGP key owned by `toc@spinnaker.io` with the
fingerprint `9C88 1F6B 9595 3116 4FE3  CCD2 6A3E 0DDE A960 2C12`.

Our Nexus instance is hosted at [s01.oss.sonatype.org](https://s01.oss.sonatype.org).

## Secrets

There are four org-level GitHub secrets available to publish to Nexus:
`NEXUS_USERNAME`, `NEXUS_PASSWORD`, `NEXUS_PGP_SIGNING_KEY`, and
`NEXUS_PGP_SIGNING_PASSWORD`.

If you need to sign in to the Nexus UI, ask the Spinnaker TOC for the root
username and password credentials.

## Publishing with Gradle

You can publish to Nexus without releasing to Maven Central:

```shell
./gradlew -P nexusPublishEnabled=true publishToNexus
```

This stages a release in an `Open` state. The Nexus UI provides a staging URL
for staged artifacts if you need to test them out. 

From the UI, you can either `Close` a release, which will start the sync 
process, or `Drop` it, which will delete it.

You can also publish and close a release programmatically:

```shell
./gradlew -P nexusPublishEnabled=true publishToNexus closeAndReleaseNexusStagingRepository
```
