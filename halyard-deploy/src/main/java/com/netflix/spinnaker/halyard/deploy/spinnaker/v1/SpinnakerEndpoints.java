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

import lombok.Data;

@Data
public class SpinnakerEndpoints {
  Services services = new Services();

  @Data
  public class Services {
    Service clouddriver = new Service().setPort(7002);
    Service deck = new Service().setPort(9000);
    Service echo = new Service().setPort(8089);
    Service fiat = new Service().setPort(7003);
    Service front50 = new Service().setPort(8080);
    Service gate = new Service().setPort(8084);
    Service igor = new Service().setPort(8088);
    Service orca = new Service().setPort(8083);
    Service rosco = new Service().setPort(8087);
  }

  @Data
  public class Service {
    int port;
    String address = "localhost";
  }
}
