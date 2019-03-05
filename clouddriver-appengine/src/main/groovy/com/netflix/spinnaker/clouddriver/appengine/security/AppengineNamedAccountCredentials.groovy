/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.appengine.security

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.api.services.appengine.v1.Appengine
import com.netflix.spinnaker.clouddriver.appengine.AppengineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.gitClient.AppengineGitCredentialType
import com.netflix.spinnaker.clouddriver.appengine.gitClient.AppengineGitCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.fiat.model.resources.Permissions
import groovy.transform.TupleConstructor

import static com.netflix.spinnaker.clouddriver.appengine.config.AppengineConfigurationProperties.ManagedAccount.GcloudReleaseTrack

@TupleConstructor
class AppengineNamedAccountCredentials implements AccountCredentials<AppengineCredentials> {
  final String name
  final String environment
  final String accountType
  final String project
  final String cloudProvider = AppengineCloudProvider.ID
  final String region
  final List<String> regions
  final List<String> requiredGroupMembership
  final Permissions permissions

  @JsonIgnore
  final String jsonPath
  final AppengineCredentials credentials
  final String applicationName
  @JsonIgnore
  final Appengine appengine
  @JsonIgnore
  final String serviceAccountEmail
  @JsonIgnore
  final String localRepositoryDirectory
  @JsonIgnore
  final AppengineGitCredentials gitCredentials
  final GcloudReleaseTrack gcloudReleaseTrack
  final List<AppengineGitCredentialType> supportedGitCredentialTypes

  final List<String> services
  final List<String> versions
  final List<String> omitServices
  final List<String> omitVersions

  final Long cachingIntervalSeconds

  static class Builder {
    String name
    String environment
    String accountType
    String project
    String region
    List<String> requiredGroupMembership
    Permissions permissions = Permissions.EMPTY
    AppengineCredentials credentials

    String jsonKey
    String jsonPath
    String applicationName
    Appengine appengine
    String serviceAccountEmail
    String localRepositoryDirectory
    String gitHttpsUsername
    String gitHttpsPassword
    String githubOAuthAccessToken
    String sshPrivateKeyFilePath
    String sshPrivateKeyPassphrase
    String sshKnownHostsFilePath
    boolean sshTrustUnknownHosts
    GcloudReleaseTrack gcloudReleaseTrack
    AppengineGitCredentials gitCredentials
    List<String> services
    List<String> versions
    List<String> omitServices
    List<String> omitVersions
    Long cachingIntervalSeconds

    /*
    * If true, the builder will overwrite region with a value from the platform.
    * */
    Boolean liveLookupsEnabled = true

    Builder name(String name) {
      this.name = name
      return this
    }

    Builder environment(String environment) {
      this.environment = environment
      return this
    }

    Builder accountType(String accountType) {
      this.accountType = accountType
      return this
    }

    Builder project(String project) {
      this.project = project
      return this
    }

    Builder region(String region) {
      this.region = region
      this.liveLookupsEnabled = false
      return this
    }

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership
      return this
    }

    Builder permissions(Permissions permissions) {
      if (permissions.isRestricted()) {
        this.requiredGroupMembership = []
        this.permissions = permissions
      }
      return this
    }

    Builder jsonPath(String jsonPath) {
      this.jsonPath = jsonPath
      return this
    }

    Builder jsonKey(String jsonKey) {
      this.jsonKey = jsonKey
      return this
    }

    Builder applicationName(String applicationName) {
      this.applicationName = applicationName
      return this
    }

    Builder credentials(AppengineCredentials credentials) {
      this.credentials = credentials
      return this
    }

    Builder appengine(Appengine appengine) {
      this.appengine = appengine
      return this
    }

    Builder serviceAccountEmail(String serviceAccountEmail) {
      this.serviceAccountEmail = serviceAccountEmail
      return this
    }

    Builder localRepositoryDirectory(String localRepositoryDirectory) {
      this.localRepositoryDirectory = localRepositoryDirectory
      return this
    }

    Builder gitHttpsUsername(String gitHttpsUsername) {
      this.gitHttpsUsername = gitHttpsUsername
      return this
    }

    Builder gitHttpsPassword(String gitHttpsPassword) {
      this.gitHttpsPassword = gitHttpsPassword
      return this
    }

    Builder githubOAuthAccessToken(String githubOAuthAccessToken) {
      this.githubOAuthAccessToken = githubOAuthAccessToken
      return this
    }

    Builder sshPrivateKeyFilePath(String sshPrivateKeyFilePath) {
      this.sshPrivateKeyFilePath = sshPrivateKeyFilePath
      return this
    }

    Builder sshPrivateKeyPassphrase(String sshPrivateKeyPassphrase) {
      this.sshPrivateKeyPassphrase = sshPrivateKeyPassphrase
      return this
    }

    Builder sshKnownHostsFilePath(String sshKnownHostsFilePath) {
      this.sshKnownHostsFilePath = sshKnownHostsFilePath
      return this
    }

    Builder sshTrustUnknownHosts(boolean sshTrustUnknownHosts) {
      this.sshTrustUnknownHosts = sshTrustUnknownHosts
      return this
    }

    Builder gitCredentials(AppengineGitCredentials gitCredentials) {
      this.gitCredentials = gitCredentials
      return this
    }

    Builder gcloudReleaseTrack(GcloudReleaseTrack gcloudReleaseTrack) {
      this.gcloudReleaseTrack = gcloudReleaseTrack
      return this
    }

    Builder services(List<String> serviceNames) {
      this.services = serviceNames
      return this
    }

    Builder versions(List<String> versionNames) {
      this.versions = versionNames
      return this
    }

    Builder omitServices(List<String> serviceNames) {
      this.omitServices = serviceNames
      return this
    }

    Builder omitVersions(List<String> versionNames) {
      this.omitVersions = versionNames
      return this
    }

    Builder cachingIntervalSeconds(Long interval) {
      this.cachingIntervalSeconds = interval
      return this
    }

    AppengineNamedAccountCredentials build() {
      credentials = credentials ?:
        jsonKey ?
        new AppengineJsonCredentials(project, jsonKey) :
        new AppengineCredentials(project)

      appengine = appengine ?: credentials.getAppengine(applicationName)

      if (liveLookupsEnabled) {
        region = appengine.apps().get(project).execute().getLocationId()
      }

      gitCredentials = gitCredentials ?: new AppengineGitCredentials(
        gitHttpsUsername,
        gitHttpsPassword,
        githubOAuthAccessToken,
        sshPrivateKeyFilePath,
        sshPrivateKeyPassphrase,
        sshKnownHostsFilePath,
        sshTrustUnknownHosts
      )

      return new AppengineNamedAccountCredentials(name,
                                                  environment,
                                                  accountType,
                                                  project,
                                                  AppengineCloudProvider.ID,
                                                  region,
                                                  [region],
                                                  requiredGroupMembership,
                                                  permissions,
                                                  jsonPath,
                                                  credentials,
                                                  applicationName,
                                                  appengine,
                                                  serviceAccountEmail,
                                                  localRepositoryDirectory,
                                                  gitCredentials,
                                                  gcloudReleaseTrack,
                                                  gitCredentials.getSupportedCredentialTypes(),
                                                  services,
                                                  versions,
                                                  omitServices,
                                                  omitVersions,
                                                  cachingIntervalSeconds)
    }
  }
}
