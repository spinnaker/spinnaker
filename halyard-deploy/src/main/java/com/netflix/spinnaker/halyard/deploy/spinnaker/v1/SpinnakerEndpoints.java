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
import lombok.Data;

import static com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact.*;

@Data
public class SpinnakerEndpoints {
  Services services = new Services();

  @Data
  public class Services {
    Service clouddriver = new Service().setPort(7002).setArtifact(CLOUDDRIVER);
    Service deck = new Service().setPort(9000).setArtifact(DECK);
    Service echo = new Service().setPort(8089).setArtifact(ECHO);
    Service fiat = new Service().setPort(7003).setArtifact(FIAT);
    Service front50 = new Service().setPort(8080).setArtifact(FRONT50);
    Service gate = new Service().setPort(8084).setArtifact(GATE);
    Service igor = new Service().setPort(8088).setArtifact(IGOR);
    Service orca = new Service().setPort(8083).setArtifact(ORCA);
    Service rosco = new Service().setPort(8087).setArtifact(ROSCO);
    Service redis = new Service().setPort(6379).setArtifact(REDIS).setProtocol("redis");
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
  }
}
