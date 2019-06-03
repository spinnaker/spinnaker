/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.concourse;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.master.AbstractAddMasterCommand;
import com.netflix.spinnaker.halyard.config.model.v1.ci.concourse.ConcourseMaster;
import com.netflix.spinnaker.halyard.config.model.v1.node.CIAccount;

@Parameters(separators = "=")
public class ConcourseAddMasterCommand extends AbstractAddMasterCommand {
  protected String getCiName() {
    return "concourse";
  }

  @Parameter(
      names = "--url",
      required = true,
      description = ConcourseCommandProperties.URL_DESCRIPTION)
  private String url;

  @Parameter(
      names = "--username",
      required = true,
      description = ConcourseCommandProperties.USERNAME_DESCRIPTION)
  public String username;

  @Parameter(
      names = "--password",
      required = true,
      password = true,
      description = ConcourseCommandProperties.PASSWORD_DESCRIPTION)
  public String password;

  @Override
  protected CIAccount buildMaster(String masterName) {
    ConcourseMaster master = (ConcourseMaster) new ConcourseMaster().setName(masterName);
    master.setUrl(url).setPassword(password).setUsername(username);

    return master;
  }
}
