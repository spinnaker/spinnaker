package com.netflix.rocket.api.artifact.internal.debian;

import static com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.RELEASE;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.apache.commons.lang3.StringUtils.substringBetween;

import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public class DebianArtifactParser {
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(DebianArtifactParser.class);

  public ArtifactStatus parseStatus(String raw) {
    String version = parseVersion(raw);
    if (version == null) {
      // we got passed the version
      version = raw;
    }
    String[] parts = version.split("-");
    if (parts.length < 2) {
      return ArtifactStatus.UNKNOWN;
    }
    String status = parts[0];
    if (status.matches("\\S+dev\\.\\d+[+.0-9a-z]*$")) {
      return ArtifactStatus.SNAPSHOT;
    }
    if (status.matches("\\S+~rc\\.\\d+$")) {
      return ArtifactStatus.CANDIDATE;
    }
    if (status.matches("\\d+\\.\\d+\\.\\d+$") || status.matches("\\d+\\.\\d+$")) {
      return RELEASE;
    }
    if (status.matches("\\d+\\.\\d+\\.\\d+[r]+\\d+$")) {
      return ArtifactStatus.RELEASE;
    }

    return ArtifactStatus.UNKNOWN;
  }

  public String parseName(String raw) {
    String last = substringAfterLast(raw, "/");
    return substringBefore(last, "_");
  }

  public String parseVersion(String raw) {
    String last = substringAfterLast(raw, "/");
    return substringBetween(last, "_");
  }

  public String parseArchitecture(String raw) {
    String last = substringAfterLast(raw, "/");
    String debianWithoutExtenstion = StringUtils.removeEnd(last, ".deb");
    return StringUtils.substringAfterLast(debianWithoutExtenstion, "_");
  }
}
