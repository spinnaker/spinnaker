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

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
public class Versions {
  public final static String BRANCH_PREFIX = "branch:";

  @Data
  public static class Version {
    String version;
    String alias;
    String changelog;
    String minimumHalyardVersion;
    Date lastUpdate;

    @Override
    public String toString() {
      String result = String.format("%s (%s):\n   Changelog: %s\n   Published: %s", version, alias, changelog, lastUpdate);
      if (!StringUtils.isEmpty(minimumHalyardVersion)) {
        result += String.format("\n   (Requires Halyard >= %s)", minimumHalyardVersion);
      }

      return result;
    }
  }

  public static boolean isBranch(String version) {
    return version.startsWith(BRANCH_PREFIX);
  }

  public static String fromBranch(String version) {
    return version.substring(BRANCH_PREFIX.length());
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
    for (Version version : versions) {
      result.append(String.format(" - %s\n", version.toString()));
    }

    return result.toString();
  }

  public static String toMajorMinor(String fullVersion) {
    int lastDot = fullVersion.lastIndexOf(".");
    if (lastDot < 0) {
      return null;
    }

    return fullVersion.substring(0, lastDot);
  }

  public static String toMajorMinorPatch(String fullVersion) {
    int lastDash = fullVersion.indexOf("-");
    if (lastDash < 0) {
      return fullVersion;
    }

    return fullVersion.substring(0, lastDash);
  }

  public static boolean lessThan(String v1, String v2) {
    v1 = toMajorMinorPatch(v1);
    v2 = toMajorMinorPatch(v2);

    List<Integer> split1 = Arrays.stream(v1.split("\\.")).map(Integer::valueOf).collect(Collectors.toList());
    List<Integer> split2 = Arrays.stream(v2.split("\\.")).map(Integer::valueOf).collect(Collectors.toList());

    if (split1.size() != split2.size() || split1.size() != 3) {
      throw new IllegalArgumentException("Both versions must satisfy the X.Y.Z naming convention");
    }

    for (int i = 0; i < split1.size(); i++) {
      if (split1.get(i) == split2.get(i)) {
        continue;
      } else if (split1.get(i) < split2.get(i)) {
        return true;
      } else if (split1.get(i) > split2.get(i)) {
        return false;
      }
    }

    // all 3 points are equal
    return false;
  }
}
