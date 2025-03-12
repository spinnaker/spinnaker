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

import static com.netflix.spinnaker.halyard.config.model.v1.providers.appengine.AppengineAccount.GcloudReleaseTrack;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google.CommonGoogleCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.appengine.AppengineAccount;
import java.util.List;

@Parameters(separators = "=")
public class AppengineEditAccountCommand extends AbstractEditAccountCommand<AppengineAccount> {
  @Override
  protected String getProviderName() {
    return "appengine";
  }

  @Parameter(names = "--project", description = CommonGoogleCommandProperties.PROJECT_DESCRIPTION)
  private String project;

  @Parameter(
      names = "--json-path",
      converter = LocalFileConverter.class,
      description = CommonGoogleCommandProperties.JSON_PATH_DESCRIPTION)
  private String jsonPath;

  @Parameter(
      names = "--local-repository-directory",
      description = AppengineCommandProperties.LOCAL_REPOSITORY_DIRECTORY_DESCRIPTION)
  private String localRepositoryDirectory;

  @Parameter(
      names = "--git-https-username",
      description = AppengineCommandProperties.GIT_HTTPS_USERNAME_DESCRIPTION)
  private String gitHttpsUsername;

  @Parameter(
      names = "--git-https-password",
      description = AppengineCommandProperties.GIT_HTTPS_PASSWORD_DESCRIPTION,
      password = true)
  private String gitHttpsPassword;

  @Parameter(
      names = "--github-oauth-access-token",
      description = AppengineCommandProperties.GITHUB_OAUTH_ACCESS_TOKEN_DESCRIPTION,
      password = true)
  private String githubOAuthAccessToken;

  @Parameter(
      names = "--ssh-private-key-file-path",
      converter = LocalFileConverter.class,
      description = AppengineCommandProperties.SSH_PRIVATE_KEY_FILE_PATH)
  private String sshPrivateKeyFilePath;

  @Parameter(
      names = "--ssh-private-key-passphrase",
      description = AppengineCommandProperties.SSH_PRIVATE_KEY_PASSPHRASE,
      password = true)
  private String sshPrivateKeyPassphrase;

  @Parameter(
      names = "--ssh-known-hosts-file-path",
      converter = LocalFileConverter.class,
      description = AppengineCommandProperties.SSH_KNOWN_HOSTS_FILE_PATH)
  private String sshKnownHostsFilePath;

  @Parameter(
      names = "--ssh-trust-unknown-hosts",
      description = AppengineCommandProperties.SSH_TRUST_UNKNOWN_HOSTS,
      arity = 1)
  private Boolean sshTrustUnknownHosts = null;

  @Parameter(
      names = "--gcloud-release-track",
      description = AppengineCommandProperties.GCLOUD_RELEASE_TRACK)
  private GcloudReleaseTrack gcloudReleaseTrack;

  @Parameter(
      names = "--services",
      description = AppengineCommandProperties.SERVICES,
      variableArity = true)
  private List<String> services;

  @Parameter(
      names = "--versions",
      description = AppengineCommandProperties.VERSIONS,
      variableArity = true)
  private List<String> versions;

  @Parameter(
      names = "--omit-services",
      description = AppengineCommandProperties.OMIT_SERVICES,
      variableArity = true)
  private List<String> omitServices;

  @Parameter(
      names = "--omit-versions",
      description = AppengineCommandProperties.OMIT_VERSIONS,
      variableArity = true)
  private List<String> omitVersions;

  @Parameter(
      names = "--caching-interval-seconds",
      description = AppengineCommandProperties.CACHING_INTERVAL_SECONDS)
  private Long cachingIntervalSeconds;

  @Override
  protected Account editAccount(AppengineAccount account) {
    account.setJsonPath(isSet(jsonPath) ? jsonPath : account.getJsonPath());
    account.setProject(isSet(project) ? project : account.getProject());
    account.setLocalRepositoryDirectory(
        isSet(localRepositoryDirectory)
            ? localRepositoryDirectory
            : account.getLocalRepositoryDirectory());
    account.setGitHttpsUsername(
        isSet(gitHttpsUsername) ? gitHttpsUsername : account.getGitHttpsUsername());
    account.setGitHttpsPassword(
        isSet(gitHttpsPassword) ? gitHttpsPassword : account.getGitHttpsPassword());
    account.setGithubOAuthAccessToken(
        isSet(githubOAuthAccessToken)
            ? githubOAuthAccessToken
            : account.getGithubOAuthAccessToken());
    account.setSshPrivateKeyFilePath(
        isSet(sshPrivateKeyFilePath) ? sshPrivateKeyFilePath : account.getSshPrivateKeyFilePath());
    account.setSshPrivateKeyPassphrase(
        isSet(sshPrivateKeyPassphrase)
            ? sshPrivateKeyPassphrase
            : account.getSshPrivateKeyPassphrase());
    account.setSshKnownHostsFilePath(
        isSet(sshKnownHostsFilePath) ? sshKnownHostsFilePath : account.getSshKnownHostsFilePath());
    account.setSshTrustUnknownHosts(
        sshTrustUnknownHosts != null ? sshTrustUnknownHosts : account.isSshTrustUnknownHosts());
    account.setGcloudReleaseTrack(
        gcloudReleaseTrack != null ? gcloudReleaseTrack : account.getGcloudReleaseTrack());
    account.setServices(services != null ? services : account.getServices());
    account.setVersions(versions != null ? versions : account.getVersions());
    account.setOmitServices(omitServices != null ? omitServices : account.getOmitServices());
    account.setOmitVersions(omitVersions != null ? omitVersions : account.getOmitVersions());
    account.setCachingIntervalSeconds(
        cachingIntervalSeconds != null
            ? cachingIntervalSeconds
            : account.getCachingIntervalSeconds());

    return account;
  }
}
