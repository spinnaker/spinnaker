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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dockerRegistry;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryAccount;
import java.util.ArrayList;
import java.util.List;

@Parameters(separators = "=")
public class DockerRegistryEditAccountCommand
    extends AbstractEditAccountCommand<DockerRegistryAccount> {
  protected String getProviderName() {
    return "dockerRegistry";
  }

  @Parameter(names = "--address", description = DockerRegistryCommandProperties.ADDRESS_DESCRIPTION)
  private String address;

  @Parameter(
      names = "--repositories",
      variableArity = true,
      description = DockerRegistryCommandProperties.REPOSITORIES_DESCRIPTION)
  private List<String> repositories = new ArrayList<>();

  @Parameter(
      names = "--add-repository",
      description = "Add this repository to the list of repositories to cache images from.")
  private String addRepository;

  @Parameter(
      names = "--remove-repository",
      description = "Remove this repository to the list of repositories to cache images from.")
  private String removeRepository;

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
  private String email;

  @Parameter(
      names = "--cache-interval-seconds",
      description = DockerRegistryCommandProperties.CACHE_INTERVAL_SECONDS_DESCRIPTION)
  private Long cacheIntervalSeconds;

  @Parameter(
      names = "--client-timeout-millis",
      description = DockerRegistryCommandProperties.CLIENT_TIMEOUT_MILLIS_DESCRIPTION)
  private Long clientTimeoutMillis;

  @Parameter(
      names = "--cache-threads",
      description = DockerRegistryCommandProperties.CACHE_THREADS_DESCRIPTION)
  private Integer cacheThreads;

  @Parameter(
      names = "--insecure-registry",
      description = DockerRegistryCommandProperties.INSECURE_REGISTRY_DESCRIPTION,
      arity = 1)
  private Boolean insecureRegistry;

  @Parameter(
      names = "--paginate-size",
      description = DockerRegistryCommandProperties.PAGINATE_SIZE_DESCRIPTION)
  private Integer paginateSize;

  @Parameter(
      names = "--sort-tags-by-date",
      arity = 1,
      description = DockerRegistryCommandProperties.SORT_TAGS_BY_DATE_DESCRIPTION)
  private Boolean sortTagsByDate;

  @Parameter(
      names = "--track-digests",
      arity = 1,
      description = DockerRegistryCommandProperties.TRACK_DIGESTS_DESCRIPTION)
  private Boolean trackDigests;

  @Parameter(
      names = "--repositories-regex",
      description = DockerRegistryCommandProperties.REPOSITORIES_REGEX_DESCRIPTION)
  private String repositoriesRegex;

  @Override
  protected Account editAccount(DockerRegistryAccount account) {
    account.setAddress(isSet(address) ? address : account.getAddress());

    try {
      account.setRepositories(
          updateStringList(
              account.getRepositories(), repositories, addRepository, removeRepository));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Set either --repositories or --[add/remove]-repository");
    }

    boolean passwordSet = isSet(password);
    boolean passwordFileSet = isSet(passwordFile);
    boolean passwordCommandSet = isSet(passwordCommand);

    if (passwordSet && passwordFileSet
        || passwordSet && passwordCommandSet
        || passwordCommandSet && passwordFileSet) {
      throw new IllegalArgumentException(
          "Set either --password or --password-command or --password-file");
    } else if (passwordSet) {
      account.setPassword(password);
      account.setPasswordCommand(null);
      account.setPasswordFile(null);
    } else if (passwordFileSet) {
      account.setPassword(null);
      account.setPasswordCommand(null);
      account.setPasswordFile(passwordFile);
    } else if (passwordCommandSet) {
      account.setPassword(null);
      account.setPasswordCommand(passwordCommand);
      account.setPasswordFile(null);
    }

    account.setUsername(isSet(username) ? username : account.getUsername());
    account.setEmail(isSet(email) ? email : account.getEmail());
    account.setCacheIntervalSeconds(
        isSet(cacheIntervalSeconds) ? cacheIntervalSeconds : account.getCacheIntervalSeconds());
    account.setClientTimeoutMillis(
        isSet(clientTimeoutMillis) ? clientTimeoutMillis : account.getClientTimeoutMillis());
    account.setCacheThreads(isSet(cacheThreads) ? cacheThreads : account.getCacheThreads());
    account.setPaginateSize(isSet(paginateSize) ? paginateSize : account.getPaginateSize());
    account.setSortTagsByDate(isSet(sortTagsByDate) ? sortTagsByDate : account.getSortTagsByDate());
    account.setTrackDigests(isSet(trackDigests) ? trackDigests : account.getTrackDigests());
    account.setInsecureRegistry(
        isSet(insecureRegistry) ? insecureRegistry : account.getInsecureRegistry());
    account.setRepositoriesRegex(repositoriesRegex);

    return account;
  }
}
