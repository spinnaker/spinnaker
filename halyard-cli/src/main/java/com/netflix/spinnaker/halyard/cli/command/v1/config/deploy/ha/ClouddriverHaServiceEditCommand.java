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
import java.net.URI;

@Parameters(separators = "=")
public class ClouddriverHaServiceEditCommand extends AbstractHaServiceEditCommand<ClouddriverHaService> {
  @Parameter(
      names = "--redis-ro-endpoint",
      description = "Set external Redis endpoint for clouddriver-ro. If this is not supplied, clouddriver-ro is configured to use the shared Redis."
  )
  private String redisRoEndpoint;

  @Parameter(
      names = "--redis-rw-endpoint",
      description = "Set external Redis endpoint for clouddriver-rw and clouddriver-caching. If this is not supplied, clouddriver-rw and clouddriver-caching are configured to use the shared Redis."
  )
  private String redisRwEndpoint;

  @Override
  protected HaService editHaService(ClouddriverHaService haService) {
    haService.setRedisRoEndpoint(isSet(redisRoEndpoint) ? redisRoEndpoint : haService.getRedisRoEndpoint());
    haService.setRedisRwEndpoint(isSet(redisRwEndpoint) ? redisRwEndpoint : haService.getRedisRwEndpoint());

    return haService;
  }

  @Override
  protected String getServiceName() {
    return "clouddriver";
  }
}
