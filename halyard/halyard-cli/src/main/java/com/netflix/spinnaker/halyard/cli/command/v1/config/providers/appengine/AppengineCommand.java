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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.appengine;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractNamedProviderCommand;

@Parameters(separators = "=")
public class AppengineCommand extends AbstractNamedProviderCommand {
  protected String getProviderName() {
    return "appengine";
  }

  @Override
  protected String getLongDescription() {
    return String.join(
        "",
        "The App Engine provider is used to deploy resources to any number of App Engine applications. ",
        "To get started with App Engine, visit https://cloud.google.com/appengine/docs/. ",
        "For more information on how to configure individual accounts, please read the documentation ",
        "under `hal config provider appengine account -h`.");
  }

  public AppengineCommand() {
    super();
    registerSubcommand(new AppengineEditCommand());
    registerSubcommand(new AppengineAccountCommand());
  }
}
