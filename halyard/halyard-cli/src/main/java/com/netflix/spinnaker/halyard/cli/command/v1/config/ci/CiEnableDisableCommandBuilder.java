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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.CommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class CiEnableDisableCommandBuilder implements CommandBuilder {
  @Setter String ciName;

  @Setter boolean enable;

  @Override
  public NestableCommand build() {
    return new CiEnableDisableCommand(ciName, enable);
  }

  @Parameters(separators = "=")
  private static class CiEnableDisableCommand extends AbstractCiEnableDisableCommand {
    private CiEnableDisableCommand(String ciName, boolean enable) {
      this.ciName = ciName;
      this.enable = enable;
    }

    @Getter(AccessLevel.PROTECTED)
    boolean enable;

    @Getter(AccessLevel.PROTECTED)
    private String ciName;
  }
}
