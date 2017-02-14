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
import lombok.Data;
import lombok.EqualsAndHashCode;

import static com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact.*;

@Data
public class SpinnakerEndpoints {
  Services services = new Services();

  public SpinnakerEndpoints(Security security) {
    services.clouddriver = new Service(security).setPort(7002).setArtifact(CLOUDDRIVER);
    services.deck = (PublicService) new PublicService(security)
        .setPublicAddress(security.getApiAddress())
        .setPort(9000)
        .setArtifact(DECK);
    services.echo = new Service(security).setPort(8089).setArtifact(ECHO);
    services.fiat = new Service(security).setPort(7003).setArtifact(FIAT);
    services.front50 = new Service(security).setPort(8080).setArtifact(FRONT50);
    services.gate = (PublicService) new PublicService(security)
        .setPublicAddress(security.getApiAddress())
        .setPort(8084)
        .setArtifact(GATE);
    services.igor = new Service(security).setPort(8088).setArtifact(IGOR);
    services.orca = new Service(security).setPort(8083).setArtifact(ORCA);
    services.rosco = new Service(security).setPort(8087).setArtifact(ROSCO);
    services.redis = new Service(security).setPort(6379).setArtifact(REDIS).setProtocol("redis");
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
  public class Service {
    int port;
    // Address is how the service is looked up.
    String address = "localhost";
    // Host is what's bound to by the service.
    String host = "localhost";
    String protocol = "http";

    @JsonIgnore
    SpinnakerArtifact artifact;

    public String getBaseUrl() {
      return protocol + "://" + address + ":" + port;
    }

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
  public class PublicService extends Service {
    String publicAddress;

    public String getPublicEndpoint() {
      return protocol + "://" + publicAddress + ":" + port;
    }

    PublicService(Security security) {
      super(security);
    }
  }
}
