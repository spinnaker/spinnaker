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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import lombok.Data;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
abstract public class SpinnakerService<T> implements HasServiceSettings<T> {
  @Autowired
  String spinnakerStagingPath;

  @Autowired
  ObjectMapper objectMapper;

  @Override
  public SpinnakerService<T> getService() {
    return this;
  }

  @Override
  public String getName() {
    return getType().toString();
  }

  public String getCanonicalName() {
    return getType().getCanonicalName();
  }

  abstract public Type getType();
  abstract public Class<T> getEndpointClass();
  abstract public List<Profile> getProfiles(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints);
  abstract public boolean isSafeToUpdate();
  abstract public boolean isMonitored();

  public enum Type {
    CLOUDDRIVER("spin-clouddriver", "clouddriver"),
    CLOUDDRIVER_BOOTSTRAP("spin-clouddriver-bootstrap", "clouddriver-bootstrap"),
    DECK("spin-deck", "deck"),
    ECHO("spin-echo", "echo"),
    FIAT("spin-fiat", "fiat"),
    FRONT50("spin-front50", "front50"),
    GATE("spin-gate", "gate"),
    IGOR("spin-igor", "igor"),
    ORCA("spin-orca", "orca"),
    ORCA_BOOTSTRAP("spin-orca-bootstrap", "orca-bootstrap"),
    REDIS("spin-redis", "redis"),
    REDIS_BOOTSTRAP("spin-redis-bootstrap", "redis-bootstrap"),
    ROSCO("spin-rosco", "rosco"),
    MONITORING_DAEMON("spin-monitoring-daemon", "monitoring-daemon");

    final String serviceName;
    @Getter
    final String canonicalName;

    Type(String serviceName, String canonicalName) {
      this.serviceName = serviceName;
      this.canonicalName = canonicalName;
    }

    @Override
    public String toString() {
      return serviceName;
    }
  }
}
