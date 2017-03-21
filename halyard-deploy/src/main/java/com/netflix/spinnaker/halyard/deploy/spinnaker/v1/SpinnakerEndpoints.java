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

import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.*;
import lombok.Data;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
public class SpinnakerEndpoints {
  Services services = new Services();

  // For serialization
  public SpinnakerEndpoints() {}

  public SpinnakerEndpoints(Security security) {
    services.clouddriver = new ClouddriverService();
    services.clouddriverBootstrap = new ClouddriverBootstrapService();
    services.deck = new DeckService(security, security.getUiDomain());
    services.echo = new EchoService();
    services.fiat = new FiatService();
    services.front50 = new Front50Service();
    services.gate = new GateService(security, security.getApiDomain());
    services.igor = new IgorService();
    services.orca = new OrcaService();
    services.orcaBootstrap = new OrcaBootstrapService();
    services.rosco = new RoscoService();
    services.redis = new RedisService();
    services.redisBootstrap = new RedisBootstrapService();
    services.monitoringDaemon = new SpinnakerMonitoringDaemonService();
  }

  @Data
  public class Services {
    ClouddriverService clouddriver;
    ClouddriverBootstrapService clouddriverBootstrap;
    DeckService deck;
    EchoService echo;
    FiatService fiat;
    Front50Service front50;
    GateService gate;
    IgorService igor;
    OrcaService orca;
    OrcaBootstrapService orcaBootstrap;
    RoscoService rosco;
    RedisService redis;
    RedisBootstrapService redisBootstrap;
    SpinnakerMonitoringDaemonService monitoringDaemon;

    public List<SpinnakerService> allServices() {
      return Arrays.stream(Services.class.getDeclaredFields())
          .filter(f -> SpinnakerService.class.isAssignableFrom(f.getType()))
          .map(f -> {
            f.setAccessible(true);
            try {
              return (SpinnakerService) f.get(this);
            } catch (IllegalAccessException e) {
              throw new RuntimeException("Failed to read field value for " + f.getName() + " in spinnaker endpoints");
            } finally {
              f.setAccessible(false);
            }
          }).collect(Collectors.toList());
    }
  }


  public SpinnakerService getService(String name) {
    Optional<Field> matchingFields = Arrays.stream(Services.class.getDeclaredFields())
        .filter(f -> f.getName().equalsIgnoreCase(name))
        .findFirst();

    Field serviceField = matchingFields.orElseThrow(() -> new HalException(
        new ProblemBuilder(Problem.Severity.FATAL, "Unknown service " + name).build())
    );

    serviceField.setAccessible(true);
    try {
      return (SpinnakerService) serviceField.get(services);
    } catch (IllegalAccessException e) {
      throw new HalException(
          new ProblemBuilder(Problem.Severity.FATAL, "Can't access service field for " + name + ": " + e.getMessage()).build()
      );
    } finally {
      serviceField.setAccessible(false);
    }
  }
}
