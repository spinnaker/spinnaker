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
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1;

import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.SpinnakerProfile;
import lombok.Getter;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An artifact is something deployed as a part of Spinnaker. It can be run with a number
 * of Profiles, but ultimately refers to a compiled/distributable binary of some format.
 *
 * @see SpinnakerProfile
 */
public enum SpinnakerArtifact {
  CLOUDDRIVER("clouddriver", new String[]{"spinnaker\\.yml",  "clouddriver.*\\.yml"}, true),
  DECK("deck", new String[]{"settings\\.js", "apache2/*"}, true),
  ECHO("echo", new String[]{"spinnaker\\.yml", "echo.*\\.yml"}, true),
  FIAT("fiat", new String[]{"spinnaker\\.yml", "fiat.*\\.yml"}, true),
  FRONT50("front50", new String[]{"spinnaker\\.yml", "front50.*\\.yml"}, true),
  GATE("gate", new String[]{"spinnaker\\.yml", "gate.*\\.yml"}, true),
  IGOR("igor", new String[]{"spinnaker\\.yml", "igor.*\\.yml"}, true),
  ORCA("orca", new String[]{"spinnaker\\.yml", "orca.*\\.yml"}, true),
  ROSCO("rosco", new String[]{"spinnaker\\.yml", "rosco.*\\.yml"}, true),
  SPINNAKER_MONITORING_DAEMON("monitoring-daemon", new String[]{}, true),
  SPINNAKER_MONITORING_THIRD_PARTY("monitoring-third-party", new String[]{}, false),
  // Non-spinnaker
  REDIS("redis", new String[]{}, false);

  @Getter final String name;
  @Getter final List<Pattern> profilePatterns;
  @Getter final boolean spinnakerInternal;

  SpinnakerArtifact(String name, String[] profilePatterns, boolean spinnakerInternal) {
    this.name = name;
    this.profilePatterns = Arrays.stream(profilePatterns)
        .map(Pattern::compile)
        .collect(Collectors.toList());
    this.spinnakerInternal = spinnakerInternal;
  }

  public Set<String> profilePaths(File[] allProfiles) {
    return Arrays.stream(allProfiles)
        .filter(f -> profilePatterns
            .stream()
            .filter(p -> p.matcher(f.getName()).find()).count() > 0)
        .filter(File::isFile)
        .map(File::getAbsolutePath)
        .collect(Collectors.toSet());
  }

  public static SpinnakerArtifact fromString(String name) {
    for (SpinnakerArtifact artifact : values()) {
      if (artifact.getName().equals(name.toLowerCase())) {
        return artifact;
      }
    }

    throw new RuntimeException(name + " is not a valid spinnaker artifact");
  }
}
