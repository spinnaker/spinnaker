/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeployCloudFoundryServiceDescription extends AbstractCloudFoundryServiceDescription {
  private String serviceType = "service";

  @JsonIgnore
  private Artifact artifact;

  @JsonIgnore
  private ArtifactCredentials artifactCredentials;

  @JsonIgnore
  private ServiceAttributes serviceAttributes;

  @JsonIgnore
  private UserProvidedServiceAttributes userProvidedServiceAttributes;

  @Data
  public static class ServiceAttributes {
    String service;
    String serviceName;
    String servicePlan;

    @Nullable
    Set<String> tags;

    @Nullable
    Map<String, Object> parameterMap;
  }

  @Data
  public static class UserProvidedServiceAttributes {
    String serviceName;

    @Nullable
    Set<String> tags;

    @Nullable
    String syslogDrainUrl;

    @Nullable
    Map<String, Object> credentialsMap;

    @Nullable
    String routeServiceUrl;
  }
}
