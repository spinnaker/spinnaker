/*
 * Copyright 2019 New Relic Corporation. All rights reserved.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.newrelic.account;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account.AbstractCanaryAccountCommand;

/** Interact with the New Relic service integration */
@Parameters(separators = "=")
public class NewRelicCanaryAccountCommand extends AbstractCanaryAccountCommand {

  @Override
  protected String getServiceIntegration() {
    return "newrelic";
  }

  public NewRelicCanaryAccountCommand() {
    registerSubcommand(new NewRelicAddCanaryAccountCommand());
    registerSubcommand(new NewRelicEditCanaryAccountCommand());
  }
}
