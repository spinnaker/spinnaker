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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.endpoint.EndpointType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

import static com.netflix.spinnaker.halyard.deploy.spinnaker.v1.endpoint.EndpointType.*;

@Data
public class SpinnakerEndpoints {
  Services services = new Services();

  // For serialization
  public SpinnakerEndpoints() {}

  public SpinnakerEndpoints(Security security) {
    services.clouddriver = new Service(security)
        .setPort(7002)
        .setHttpHealth("/health")
        .setEndpointType(CLOUDDRIVER);
    services.deck = (PublicService) new PublicService(security)
        .setPublicAddress(security.getApiAddress())
        .setPort(9000)
        .setEndpointType(DECK);
    services.echo = new Service(security)
        .setPort(8089)
        .setHttpHealth("/health")
        .setEndpointType(ECHO);
    services.fiat = new Service(security)
        .setPort(7003)
        .setHttpHealth("/health")
        .setEndpointType(FIAT);
    services.front50 = new Service(security)
        .setPort(8080)
        .setHttpHealth("/health")
        .setEndpointType(FRONT50);
    services.gate = (PublicService) new PublicService(security)
        .setPublicAddress(security.getApiAddress())
        .setPort(8084)
        .setHttpHealth("/health")
        .setEndpointType(GATE);
    services.igor = new Service(security)
        .setPort(8088)
        .setHttpHealth("/health")
        .setEndpointType(IGOR);
    services.orca = new Service(security)
        .setPort(8083)
        .setHttpHealth("/health")
        .setEndpointType(ORCA);
    services.rosco = new Service(security)
        .setPort(8087)
        .setHttpHealth("/health")
        .setEndpointType(ROSCO);
    services.redis = new Service(security)
        .setPort(6379)
        .setEndpointType(REDIS)
        .setProtocol("redis");
  }

  public Service getService(String name) {
    Optional<Field> optionalField = Arrays.stream(this.getClass().getDeclaredFields()).filter(f -> f.getName().equals(name)).findFirst();

    Field serviceField = optionalField.orElseThrow(() -> {
      return new HalException(
        new ProblemBuilder(Problem.Severity.FATAL, "Service " + name + " is not a registered Spinnaker service.").build());
    });

    try {
      serviceField.setAccessible(true);
      return (Service) serviceField.get(this);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Failed to read field " + name, e);
    } finally {
      serviceField.setAccessible(false);
    }
  }

  @Data
  public class Services {
    Service clouddriver;
    PublicService deck;
    Service echo;
    Service fiat;
    Service front50;
    PublicService gate;
    Service igor;
    Service orca;
    Service rosco;
    Service redis;
  }

  @Data
  public static class Service {
    int port;
    // Address is how the service is looked up.
    String address = "localhost";
    // Host is what's bound to by the service.
    String host = "localhost";
    String protocol = "http";
    String httpHealth;

    @JsonIgnore
    EndpointType endpointType ;

    @JsonIgnore
    public SpinnakerArtifact getArtifact() {
      return endpointType.getArtifact();
    }

    public String getBaseUrl() {
      return protocol + "://" + address + ":" + port;
    }

    // For serialization
    public Service() {}

    Service(Security security) {
      if (security.getSsl().isEnabled()) {
        protocol = "https";
      }
    }
  }

  /**
   * Like a Service, but this has a publicly accessible endpoint (that is likely different from the one
   * assigned within the Spinnaker cluster).
   */
  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class PublicService extends Service {
    String publicAddress;

    public String getPublicEndpoint() {
      return protocol + "://" + publicAddress + ":" + port;
    }

    // For serialization
    public PublicService() {}

    PublicService(Security security) {
      super(security);
    }
  }
}
