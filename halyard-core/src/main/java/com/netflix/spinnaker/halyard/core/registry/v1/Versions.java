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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class Versions {
  @Data
  public static class Version {
    String version;
    String alias;
    String changelog;
    Date lastUpdate;

    @Override
    public String toString() {
      return String.format("%s (%s):\n   Changelog: %s\n   Published: %s", version, alias, changelog, lastUpdate);
    }
  }

  @Data
  // A version explicitly not supported by Halyard
  public static class IllegalVersion {
    String version;
    String reason; // Why is this version illegal
  }

  @Deprecated
  String latest;
  String latestHalyard;
  String latestSpinnaker;
  List<Version> versions = new ArrayList<>();
  List<IllegalVersion> illegalVersions = new ArrayList<>();

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
}
