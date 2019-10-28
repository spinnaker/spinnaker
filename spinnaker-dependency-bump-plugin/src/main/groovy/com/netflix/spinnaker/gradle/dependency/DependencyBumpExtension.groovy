/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.gradle.dependency

import org.gradle.api.Project

class DependencyBumpExtension {
  public static Map<String, List<String>> REPOS = [
    kork: [
      "clouddriver",
      "echo",
      "fiat",
      "front50",
      "gate",
      "halyard",
      "igor",
      "keel",
      "orca",
      "rosco",
      "swabbie"
    ],
    fiat: [
      "clouddriver",
      "echo",
      "igor",
      "keel",
      "orca",
      "front50",
      "gate"
    ]
  ]

  Project rootProject

  String artifactOverride
  String versionString

  List<String> repos = []

  List<String> getReposForArtifact() {
    return repos ?: REPOS[artifact] ?: []
  }

  String getArtifact() {
    artifactOverride ?: rootProject.name
  }

  String getVersionStringForArtifact() {
    return versionString ?: "${artifact}Version"
  }

  String getAutobumpBranchName() {
    return "auto-bump-$artifact"
  }

  String getAutobumpLabel() {
    return "autobump-$artifact"
  }
}
