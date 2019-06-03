/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.deploy.ha;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.config.model.v1.ha.ClouddriverHaService;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaService;

@Parameters(separators = "=")
public class ClouddriverHaServiceEditCommand
    extends AbstractHaServiceEditCommand<ClouddriverHaService> {
  @Parameter(
      names = "--redis-master-endpoint",
      description =
          "Set external Redis endpoint for clouddriver-rw and clouddriver-caching. "
              + "The Redis URI schema is described here: https://www.iana.org/assignments/uri-schemes/prov/redis. "
              + "clouddriver-rw and clouddriver-caching are configured to use the shared Redis, by default.")
  private String redisMasterEndpoint;

  @Parameter(
      names = "--redis-slave-endpoint",
      description =
          "Set external Redis endpoint for clouddriver-ro. "
              + "The Redis URI schema is described here: https://www.iana.org/assignments/uri-schemes/prov/redis. "
              + "clouddriver-ro is configured to use the shared Redis, by default.")
  private String redisSlaveEndpoint;

  @Parameter(
      names = "--redis-slave-deck-endpoint",
      description =
          "Set external Redis endpoint for clouddriver-ro-deck. "
              + "The Redis URI schema is described here: https://www.iana.org/assignments/uri-schemes/prov/redis. "
              + "clouddriver-ro-deck is configured to use the shared Redis, by default.")
  private String redisSlaveDeckEndpoint;

  @Override
  protected HaService editHaService(ClouddriverHaService haService) {
    haService.setRedisMasterEndpoint(
        isSet(redisMasterEndpoint) ? redisMasterEndpoint : haService.getRedisMasterEndpoint());
    haService.setRedisSlaveEndpoint(
        isSet(redisSlaveEndpoint) ? redisSlaveEndpoint : haService.getRedisSlaveEndpoint());
    haService.setRedisSlaveDeckEndpoint(
        isSet(redisSlaveDeckEndpoint)
            ? redisSlaveDeckEndpoint
            : haService.getRedisSlaveDeckEndpoint());

    // TODO(joonlim): Add flag to enable/disable clouddriver-ro-deck using this command.

    return haService;
  }

  @Override
  protected String getServiceName() {
    return "clouddriver";
  }
}
