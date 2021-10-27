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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.templates;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;

/** An abstract definition for commands that accept TEMPLATE as a main parameter */
@Parameters(separators = "=")
public abstract class AbstractHasArtifactTemplateCommand extends AbstractConfigCommand {
  @Parameter(description = "The name of the artifact template to operate on.")
  String template = null;

  @Override
  public String getMainParameter() {
    return "template";
  }

  public String getArtifactTemplate(String defaultName) {
    try {
      return getArtifactTemplate();
    } catch (IllegalArgumentException e) {
      return defaultName;
    }
  }

  public String getArtifactTemplate() {
    if (template == null) {
      throw new IllegalArgumentException("No template supplied");
    }
    return template;
  }
}
