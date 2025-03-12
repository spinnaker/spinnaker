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

import com.beust.jcommander.Parameters;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class ClouddriverHaServiceCommand extends AbstractNamedHaServiceCommand {
  @Override
  protected String getServiceName() {
    return "clouddriver";
  }

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      String.join(
          " ",
          getShortDescription(),
          "Manage and view Spinnaker configuration for the clouddriver high availability service.",
          "When clouddriver high availability is enabled, Halyard will deploy clouddriver",
          "as three separate services in order to increase availability: clouddriver-rw,",
          "clouddriver-ro, and clouddriver-caching. The clouddriver-rw service handles mutation",
          "operations sent via orca. The clouddriver-ro service handles read queries and does not",
          "perform write operations to redis. The clouddriver-caching service handles the periodic",
          "caching of cloud provider data, and is isolated from the rest of Spinnaker. The three",
          "services are configured to use the shared redis provisioned by Halyard, by default.",
          "To achieve more scale, a redis master endpoint and a redis slave endpoint can be supplied.",
          "The clouddriver-rw and clouddriver-caching services will use the redis master and the",
          "clouddriver-ro service will use the redis slave.");

  public ClouddriverHaServiceCommand() {
    super();
    registerSubcommand(new ClouddriverHaServiceEditCommand());
  }
}
