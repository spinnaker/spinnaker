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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.consul;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.ProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Data
public class ConsulServiceProfileFactoryBuilder {
  @Autowired protected ArtifactService artifactService;

  @Autowired protected ObjectMapper objectMapper;

  public ProfileFactory build(SpinnakerService.Type type, ServiceSettings settings) {
    return new ProfileFactory() {
      @Override
      protected ArtifactService getArtifactService() {
        return artifactService;
      }

      @Override
      protected void setProfile(
          Profile profile,
          DeploymentConfiguration deploymentConfiguration,
          SpinnakerRuntimeSettings endpoints) {
        ConsulCheck check = new ConsulCheck().setId("default-hal-check").setInterval("30s");

        if (settings.getHealthEndpoint() != null) {
          check.setHttp(
              new URIBuilder()
                  .setScheme(settings.getScheme())
                  .setHost("localhost")
                  .setPort(settings.getPort())
                  .setPath(settings.getHealthEndpoint())
                  .toString());
        } else {
          check.setTcp("localhost:" + settings.getPort());
        }

        ConsulService consulService =
            new ConsulService()
                .setName(type.getCanonicalName())
                .setPort(settings.getPort())
                .setChecks(Collections.singletonList(check));

        ServiceWrapper serviceWrapper = new ServiceWrapper().setService(consulService);

        try {
          profile.appendContents(objectMapper.writeValueAsString(serviceWrapper));
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      protected Profile getBaseProfile(String name, String version, String outputFile) {
        return new Profile(name, version, outputFile, "");
      }

      @Override
      public SpinnakerArtifact getArtifact() {
        return SpinnakerArtifact.CONSUL;
      }

      @Override
      protected String commentPrefix() {
        return "// ";
      }

      @Override
      protected boolean showEditWarning() {
        return false;
      }
    };
  }

  @Data
  static class ServiceWrapper {
    ConsulService service;
  }

  @Data
  static class ConsulService {
    String name;
    int port;
    List<ConsulCheck> checks = new ArrayList<>();
  }

  @Data
  static class ConsulCheck {
    String id;
    String interval;
    String tcp;
    String http;
  }
}
