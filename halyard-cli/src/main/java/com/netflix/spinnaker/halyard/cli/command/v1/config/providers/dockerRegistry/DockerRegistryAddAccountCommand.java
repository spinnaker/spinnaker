/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dockerRegistry;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryAccount;
import java.util.ArrayList;
import java.util.List;

@Parameters(separators = "=")
class DockerRegistryAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return "dockerRegistry";
  }

  @Parameter(
      names = "--address",
      required = true,
      description = DockerRegistryCommandProperties.ADDRESS_DESCRIPTION)
  private String address = "gcr.io";

  @Parameter(
      names = "--repositories",
      variableArity = true,
      description = DockerRegistryCommandProperties.REPOSITORIES_DESCRIPTION)
  private List<String> repositories = new ArrayList<>();

  @Parameter(
      names = "--password",
      password = true,
      description = DockerRegistryCommandProperties.PASSWORD_DESCRIPTION)
  private String password;

  @Parameter(
      names = "--password-command",
      description = DockerRegistryCommandProperties.PASSWORD_COMMAND_DESCRIPTION)
  private String passwordCommand;

  @Parameter(
      names = "--password-file",
      converter = LocalFileConverter.class,
      description = DockerRegistryCommandProperties.PASSWORD_FILE_DESCRIPTION)
  private String passwordFile;

  @Parameter(
      names = "--username",
      description = DockerRegistryCommandProperties.USERNAME_DESCRIPTION)
  private String username;

  @Parameter(names = "--email", description = DockerRegistryCommandProperties.EMAIL_DESCRIPTION)
  private String email = "fake.email@spinnaker.io";

  @Parameter(
      names = "--cache-interval-seconds",
      description = DockerRegistryCommandProperties.CACHE_INTERVAL_SECONDS_DESCRIPTION)
  private Long cacheIntervalSeconds = 30L;

  @Parameter(
      names = "--client-timeout-millis",
      description = DockerRegistryCommandProperties.CLIENT_TIMEOUT_MILLIS_DESCRIPTION)
  private Long clientTimeoutMillis = 60_000L;

  @Parameter(
      names = "--cache-threads",
      description = DockerRegistryCommandProperties.CACHE_THREADS_DESCRIPTION)
  private int cacheThreads = 1;

  @Parameter(
      names = "--insecure-registry",
      description = DockerRegistryCommandProperties.INSECURE_REGISTRY_DESCRIPTION,
      arity = 1)
  private Boolean insecureRegistry = false;

  @Parameter(
      names = "--paginate-size",
      description = DockerRegistryCommandProperties.PAGINATE_SIZE_DESCRIPTION)
  private int paginateSize = 100;

  @Parameter(
      names = "--sort-tags-by-date",
      arity = 1,
      description = DockerRegistryCommandProperties.SORT_TAGS_BY_DATE_DESCRIPTION)
  private Boolean sortTagsByDate = false;

  @Parameter(
      names = "--track-digests",
      arity = 1,
      description = DockerRegistryCommandProperties.TRACK_DIGESTS_DESCRIPTION)
  private Boolean trackDigests = false;

  @Parameter(
      names = "--repositories-regex",
      description = DockerRegistryCommandProperties.REPOSITORIES_REGEX_DESCRIPTION)
  private String repositoriesRegex;

  @Override
  protected Account buildAccount(String accountName) {
    DockerRegistryAccount account =
        (DockerRegistryAccount) new DockerRegistryAccount().setName(accountName);

    account
        .setAddress(address)
        .setRepositories(repositories)
        .setPassword(password)
        .setPasswordCommand(passwordCommand)
        .setPasswordFile(passwordFile)
        .setUsername(username)
        .setEmail(email)
        .setCacheIntervalSeconds(cacheIntervalSeconds)
        .setClientTimeoutMillis(clientTimeoutMillis)
        .setCacheThreads(cacheThreads)
        .setPaginateSize(paginateSize)
        .setSortTagsByDate(sortTagsByDate)
        .setTrackDigests(trackDigests)
        .setRepositoriesRegex(repositoriesRegex)
        .setInsecureRegistry(insecureRegistry);

    return account;
  }

  @Override
  protected Account emptyAccount() {
    return new DockerRegistryAccount();
  }
}
