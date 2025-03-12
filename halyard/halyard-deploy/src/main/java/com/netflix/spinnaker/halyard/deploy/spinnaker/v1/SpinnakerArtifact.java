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

import lombok.Getter;

/**
 * An artifact is something deployed as a part of Spinnaker. It can be run with a number of
 * Profiles, but ultimately refers to a compiled/distributable binary of some format.
 */
public enum SpinnakerArtifact {
  CLOUDDRIVER("clouddriver", true),
  DECK("deck", true),
  ECHO("echo", true),
  FIAT("fiat", true),
  FRONT50("front50", true),
  GATE("gate", true),
  IGOR("igor", true),
  KAYENTA("kayenta", true),
  ORCA("orca", true),
  ROSCO("rosco", true),
  SPINNAKER("spinnaker", true),
  SPINNAKER_MONITORING_DAEMON("monitoring-daemon", true),
  SPINNAKER_MONITORING_THIRD_PARTY("monitoring-third-party", true),
  // Non-spinnaker
  REDIS("redis", false),
  CONSUL("consul", false),
  VAULT("vault", false);

  @Getter final String name;
  @Getter final boolean spinnakerInternal;

  SpinnakerArtifact(String name, boolean spinnakerInternal) {
    this.name = name;
    this.spinnakerInternal = spinnakerInternal;
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
