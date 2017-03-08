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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.endpoint;

import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints.Service;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints.Services;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

public enum EndpointType {
  CLOUDDRIVER(Clouddriver.class, "clouddriver", SpinnakerArtifact.CLOUDDRIVER),
  DECK(null, "deck", SpinnakerArtifact.DECK),
  ECHO(null, "echo", SpinnakerArtifact.ECHO),
  FIAT(null, "fiat", SpinnakerArtifact.FIAT),
  FRONT50(null, "front50", SpinnakerArtifact.FRONT50),
  GATE(null, "gate", SpinnakerArtifact.GATE),
  IGOR(null, "igor", SpinnakerArtifact.IGOR),
  ORCA(null, "orca", SpinnakerArtifact.ORCA),
  ROSCO(null, "rosco", SpinnakerArtifact.ROSCO),
  // Non-spinnaker
  REDIS(null, "redis", SpinnakerArtifact.REDIS);

  @Getter
  Class serviceClass;

  @Getter
  final String name;

  @Getter
  final SpinnakerArtifact artifact;

  public static EndpointType fromString(String name) {
    for (EndpointType v : values()) {
      if (v.getName().equals(name)) {
        return v;
      }
    }

    throw new HalException(
        new ProblemBuilder(Problem.Severity.FATAL, "No endpoint with name \"" + name + "\" defined").build()
    );
  }

  public Service getService(SpinnakerEndpoints endpoints) {
    Services services = endpoints.getServices();
    Optional<Field> oField = Arrays.stream(services.getClass().getDeclaredFields())
        .filter(f -> f.getName().equals(name))
        .findFirst();

    if (oField.isPresent()) {
      Field field = null;
      try {
        field = oField.get();
        field.setAccessible(true);
        return (Service) field.get(services);
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Unable to get service for " + name, e);
      } finally {
        if (field != null) {
          field.setAccessible(false);
        }
      }
    } else {
      throw new RuntimeException("No service declared for + " + name);
    }
  }

  EndpointType(Class serviceClass, String name, SpinnakerArtifact artifact) {
    this.serviceClass = serviceClass;
    this.name = name;
    this.artifact = artifact;
  }
}
