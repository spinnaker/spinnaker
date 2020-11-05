package com.netflix.rocket.api.artifact.internal.debian;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus;
import org.junit.jupiter.api.Test;

public class DebianArtifactParserTests {
  private DebianArtifactParser parser = new DebianArtifactParser();

  @Test
  void shouldParseStandardVersionString() {
    String rawVersion =
        "debian-local:pool/o/mypackage-server/mypackage-server_0.1.2-h2.afc245_all.deb";

    assertThat(parser.parseStatus(rawVersion)).isEqualTo(ArtifactStatus.RELEASE);
  }

  @Test
  void shouldParseLegacyVersionString() {
    String rawVersion = "debian-local:pool/t/test-java-legacy_1.1-h1.9e74f98_all.deb";

    assertThat(parser.parseStatus(rawVersion)).isEqualTo(ArtifactStatus.RELEASE);
  }

  @Test
  void shouldReturnReleaseForLocalReleaseBuild() {
    String rawVersion =
        "debian-local:pool/o/mypackage-server/mypackage-server_1.100.5-LOCAL_all.deb";

    assertThat(parser.parseStatus(rawVersion)).isEqualTo(ArtifactStatus.RELEASE);
  }

  @Test
  void shouldParseCandidateVersionString() {
    String rawVersion =
        "debian-local:pool/o/mypackage-server/mypackage-server_0.1.2~rc.11-h2.afc245_all.deb";

    assertThat(parser.parseStatus(rawVersion)).isEqualTo(ArtifactStatus.CANDIDATE);
  }

  @Test
  void shouldReturnCandidateForLocalFinalBuild() {
    String rawVersion =
        "debian-local:pool/o/mypackage-server/mypackage-server_1.100.5~rc.23-LOCAL_all.deb";

    assertThat(parser.parseStatus(rawVersion)).isEqualTo(ArtifactStatus.CANDIDATE);
  }

  @Test
  void shouldParseSnapshotVersionString() {
    String rawVersion =
        "debian-local:pool/o/mypackage-server/mypackage-server_0.0.1176~dev.34267+2345af-h1886.4246bc3_all.deb";

    assertThat(parser.parseStatus(rawVersion)).isEqualTo(ArtifactStatus.SNAPSHOT);
  }

  @Test
  void shouldParseLocallyPublishedSnapshot() {
    String raw =
        "debian-local:pool/g/bottle-netflix-web/bottle-netflix-web_4.1.0~dev.294.uncommitted-LOCAL_all.deb";

    ArtifactStatus status = parser.parseStatus(raw);

    assertThat(status).isEqualTo(ArtifactStatus.SNAPSHOT);
  }

  @Test
  void shouldParseSnapshotAfterCandidateVersionString() {
    String rawVersion =
        "debian-local:pool/g/bottle-netflix-web/bottle-netflix-web_4.0.1~rc.1.dev.1-h250.1c1dacc_all.deb";

    assertThat(parser.parseStatus(rawVersion)).isEqualTo(ArtifactStatus.SNAPSHOT);
  }

  @Test
  void shouldParseImmutableSnapshotVersionStringWithoutTimestamp() {
    String rawVersion =
        "debian-local:pool/g/gps-assembler/gps-assembler_573.1.0~snapshot-h13858.edfc25e_all.deb";

    assertThat(parser.parseStatus(rawVersion)).isEqualTo(ArtifactStatus.SNAPSHOT);
  }

  @Test
  void shouldParseTopcoatReleases() {
    String rawVersion =
        "debian-local:pool/n/nflx-metadata:astrid/topcoat_6.0.4r3-1~bionic-LOCAL_amd64.deb";

    assertThat(parser.parseStatus(rawVersion)).isEqualTo(ArtifactStatus.RELEASE);
  }

  @Test
  void shouldReturnUnknownForUnparseableString() {
    String rawVersion = "debian-local:pool/o/mypackage-server/mypackage-server_0.0.1176_all.deb";

    assertThat(parser.parseStatus(rawVersion)).isEqualTo(ArtifactStatus.UNKNOWN);
  }

  @Test
  void shouldParseArtifactoryDebianNameString() {
    String raw =
        "debian-local:pool/o/mypackage-server/mypackage-server_0.0.1176-h1886.4246bc3_all.deb";

    assertThat(parser.parseName(raw)).isEqualTo("mypackage-server");
  }

  @Test
  void shouldParseArtifactoryDebianVersionString() {
    String raw =
        "debian-local:pool/o/mypackage-server/mypackage-server_0.0.1176-h1886.4246bc3_all.deb";

    assertThat(parser.parseVersion(raw)).isEqualTo("0.0.1176-h1886.4246bc3");
  }

  @Test
  void shouldParseArtifactoryDebianArchitectureString() {
    String raw =
        "debian-local:pool/o/mypackage-server/mypackage-server_0.0.1176-h1886.4246bc3_all.deb";

    assertThat(parser.parseArchitecture(raw)).isEqualTo("all");
  }

  @Test
  void shouldParseArtifactoryDebianArchitectureStringForAmd64() {
    String raw =
        "debian-local:pool/o/mypackage-server/mypackage-server_0.0.1176-h1886.4246bc3_amd64.deb";

    assertThat(parser.parseArchitecture(raw)).isEqualTo("amd64");
  }

  @Test
  void shouldDefaultToUnknownForUnrecognizedVersion() {
    String rawVersion =
        "debian-local:pool/o/mypackage-server/mypackage-server_1.100.HOTFIX-LOCAL_all.deb";

    assertThat(parser.parseStatus(rawVersion)).isEqualTo(ArtifactStatus.UNKNOWN);
  }
}
