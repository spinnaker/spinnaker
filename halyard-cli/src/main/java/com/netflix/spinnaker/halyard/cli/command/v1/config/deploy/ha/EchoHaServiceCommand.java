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
public class EchoHaServiceCommand extends AbstractNamedHaServiceCommand {
  @Override
  protected String getServiceName() {
    return "echo";
  }

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      String.join(
          " ",
          getShortDescription(),
          "Manage and view Spinnaker configuration for the echo high availability service.",
          "When echo high availability is enabled, Halyard will deploy echo as two",
          "separate services in order to increase availability: echo-scheduler and echo-worker.",
          "The echo-scheduler service only handles Spinnaker cron-jobs and is isolated from",
          "the rest of Spinnaker. The echo-worker handles everything else.");
}
