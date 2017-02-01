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

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An artifact is something deployed as a part of Spinnaker. It can be run with a number
 * of Profiles, but ultimately refers to a compiled/distributable binary of some format.
 *
 * @see SpinnakerProfile
 */
public enum SpinnakerArtifact {
  CLOUDDRIVER("clouddriver", new String[]{"spinnaker\\.yml", "clouddriver.*\\.yml"}),
  DECK("deck", new String[]{"settings\\.js"}),
  ECHO("echo", new String[]{"spinnaker\\.yml", "echo.*\\.yml"}),
  FIAT("fiat", new String[]{"spinnaker\\.yml", "fiat.*\\.yml"}),
  FRONT50("front50", new String[]{"spinnaker\\.yml", "front50.*\\.yml"}),
  GATE("gate", new String[]{"spinnaker\\.yml", "gate.*\\.yml"}),
  IGOR("igor", new String[]{"spinnaker\\.yml", "igor.*\\.yml"}),
  ORCA("orca", new String[]{"spinnaker\\.yml", "orca.*\\.yml"}),
  ROSCO("rosco", new String[]{"spinnaker\\.yml", "rosco.*\\.yml"});

  final String name;
  final List<Pattern> profilePatterns;

  SpinnakerArtifact(String name, String[] profilePatterns) {
    this.name = name;
    this.profilePatterns = Arrays.stream(profilePatterns)
        .map(Pattern::compile)
        .collect(Collectors.toList());
  }

  public List<String> profilePaths(File[] allProfiles) {
    return Arrays.stream(allProfiles)
        .filter(f -> profilePatterns
            .stream()
            .filter(p -> p.matcher(f.getName()).find()).count() > 0)
        .map(File::getAbsolutePath)
        .collect(Collectors.toList());
  }
}
