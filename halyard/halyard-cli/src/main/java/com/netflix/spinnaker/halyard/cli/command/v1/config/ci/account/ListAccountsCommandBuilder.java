/*
 * Copyright 2020 Amazon.com, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.account;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.CommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class ListAccountsCommandBuilder implements CommandBuilder {
  @Setter String ciName;
  @Setter String ciFullName;

  @Override
  public NestableCommand build() {
    return new ListAccountsCommand(ciName, ciFullName);
  }

  @Parameters(separators = "=")
  private static class ListAccountsCommand extends AbstractListAccountsCommand {
    private ListAccountsCommand(String ciName, String ciFullName) {
      this.ciName = ciName;
      this.ciFullName = ciFullName;
    }

    @Getter(AccessLevel.PROTECTED)
    private String ciName;

    @Getter(AccessLevel.PROTECTED)
    private String ciFullName;
  }
}
