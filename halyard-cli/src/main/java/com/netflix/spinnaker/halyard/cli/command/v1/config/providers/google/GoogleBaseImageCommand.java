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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractBaseImageCommand;

/** Interact with the google provider's bakery */
@Parameters(separators = "=")
public class GoogleBaseImageCommand extends AbstractBaseImageCommand {
  protected String getProviderName() {
    return "google";
  }

  public GoogleBaseImageCommand() {
    super();
    registerSubcommand(new GoogleAddBaseImageCommand());
    registerSubcommand(new GoogleEditBaseImageCommand());
  }
}
