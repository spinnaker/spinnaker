/*
 * Copyright 2017 Schibsted ASA.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.travis;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.master.AbstractAddMasterCommand;
import com.netflix.spinnaker.halyard.config.model.v1.ci.travis.TravisMaster;
import com.netflix.spinnaker.halyard.config.model.v1.node.CIAccount;
import java.util.ArrayList;
import java.util.List;

@Parameters(separators = "=")
public class TravisAddMasterCommand extends AbstractAddMasterCommand {
  protected String getCiName() {
    return "travis";
  }

  @Parameter(
      names = "--address",
      required = true,
      description = TravisCommandProperties.ADDRESS_DESCRIPTION)
  private String address;

  @Parameter(
      names = "--base-url",
      required = true,
      description = TravisCommandProperties.BASE_URL_DESCRIPTION)
  public String baseUrl;

  @Parameter(
      names = "--github-token",
      password = true,
      description = TravisCommandProperties.GITHUB_TOKEN_DESCRIPTION)
  public String githubToken;

  @Parameter(
      names = "--number-of-repositories",
      description = TravisCommandProperties.NUMBER_OF_REPOSITORIES_DESCRIPTION)
  public Integer numberOfRepositories;

  @Parameter(
      names = "--number-of-jobs",
      description = TravisCommandProperties.NUMBER_OF_JOBS_DESCRIPTION)
  public Integer numberOfJobs;

  @Parameter(
      names = "--build-result-limit",
      description = TravisCommandProperties.BUILD_RESULT_LIMIT_DESCRIPTION)
  public Integer buildResultLimit;

  @Parameter(
      names = "--filtered-repositories",
      description = TravisCommandProperties.FILTERED_REPOSITORIES_DESCRIPTION)
  public List<String> filteredRepositories = new ArrayList<>();

  @Override
  protected CIAccount buildMaster(String masterName) {
    TravisMaster master = (TravisMaster) new TravisMaster().setName(masterName);
    master
        .setAddress(address)
        .setBaseUrl(baseUrl)
        .setGithubToken(githubToken)
        .setNumberOfRepositories(numberOfRepositories)
        .setNumberOfJobs(numberOfJobs)
        .setBuildResultLimit(buildResultLimit)
        .setFilteredRepositories(filteredRepositories);

    return master;
  }
}
