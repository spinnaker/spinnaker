/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.halyard.core.registry.v1;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class Versions {
  public static final String BRANCH_PREFIX = "branch:";
  public static final String LOCAL_PREFIX = "local:";

  @Data
  public static class Version {
    String version;
    String alias;
    String changelog;
    String minimumHalyardVersion;
    Date lastUpdate;

    @Override
    public String toString() {
      String result =
          String.format(
              "%s (%s):\n   Changelog: %s\n   Published: %s",
              version, alias, changelog, lastUpdate);
      if (!StringUtils.isEmpty(minimumHalyardVersion)) {
        result += String.format("\n   (Requires Halyard >= %s)", minimumHalyardVersion);
      }

      return result;
    }

    public static Comparator<Version> comparator() {
      return Comparator.comparing(Version::getVersion, orderBySemVer());
    }
  }

  @Data
  @AllArgsConstructor
  public static class SemVer {
    int major;
    int minor;
    int patch;

    private static String truncateToHyphen(String version) {
      int firstHyphen = version.indexOf("-");
      if (firstHyphen < 0) {
        return version;
      }
      return version.substring(0, firstHyphen);
    }

    static SemVer fromString(String version) {
      String versionExpression = version;
      if (isBranch(version)) {
        return null;
      } else if (isLocal(version)) {
        versionExpression = fromLocal(version);
      }

      String[] parts = truncateToHyphen(versionExpression).split(Pattern.quote("."));
      if (parts.length != 3) {
        throw new IllegalArgumentException("Versions must satisfy the X.Y.Z naming convention");
      }

      List<Integer> intParts =
          Arrays.stream(parts).map(Integer::parseInt).collect(Collectors.toList());
      return new SemVer(intParts.get(0), intParts.get(1), intParts.get(2));
    }

    static Comparator<SemVer> comparator() {
      return Comparator.comparing(SemVer::getMajor)
          .thenComparing(SemVer::getMinor)
          .thenComparing(SemVer::getPatch);
    }
  }

  public static boolean isBranch(String version) {
    return version.startsWith(BRANCH_PREFIX);
  }

  public static String fromBranch(String version) {
    return version.substring(BRANCH_PREFIX.length());
  }

  public static boolean isLocal(String version) {
    return version.startsWith(LOCAL_PREFIX);
  }

  public static String fromLocal(String version) {
    return version.substring(LOCAL_PREFIX.length());
  }

  @Data
  // A version explicitly not supported by Halyard
  public static class IllegalVersion {
    String version;
    String reason; // Why is this version illegal
  }

  String latestHalyard;
  String latestSpinnaker;
  List<Version> versions = new ArrayList<>();
  List<IllegalVersion> illegalVersions = new ArrayList<>();

  public Optional<Version> getVersion(String version) {
    return versions.stream().filter(v -> v.getVersion().equals(version)).findFirst();
  }

  @Override
  public String toString() {
    if (versions.isEmpty()) {
      return "No stable versions published at this time.";
    }

    StringBuilder result = new StringBuilder();
    versions.stream()
        .sorted(Version.comparator())
        .forEach(version -> result.append(String.format(" - %s\n", version.toString())));

    return result.toString();
  }

  public static String toMajorMinor(String fullVersion) {
    String version = removePrefixes(fullVersion);
    int lastDot = version.lastIndexOf(".");
    if (lastDot < 0) {
      return null;
    }

    return version.substring(0, lastDot);
  }

  public static String toMajorMinorPatch(String fullVersion) {
    String version = removePrefixes(fullVersion);
    int lastDash = version.indexOf("-");
    if (lastDash < 0) {
      return version;
    }

    return version.substring(0, lastDash);
  }

  private static String removePrefixes(String fullVersion) {
    if (isBranch(fullVersion)) {
      return fromBranch(fullVersion);
    } else if (isLocal(fullVersion)) {
      return fromLocal(fullVersion);
    }
    return fullVersion;
  }

  public static Comparator<String> orderBySemVer() {
    Comparator<SemVer> comparator = Comparator.nullsLast(SemVer.comparator());
    return Comparator.comparing(SemVer::fromString, comparator)
        .thenComparing(Versions::toMajorMinorPatch, Comparator.naturalOrder());
  }

  public static boolean lessThan(String v1, String v2) {
    return orderBySemVer().compare(v1, v2) < 0;
  }

  public static boolean greaterThanEqual(String v1, String v2) {
    return orderBySemVer().compare(v1, v2) >= 0;
  }
}
