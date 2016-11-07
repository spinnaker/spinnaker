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
import com.netflix.spinnaker.clouddriver.appengine.AppEngineCloudProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import groovy.transform.TupleConstructor

@TupleConstructor
class AppEngineNamedAccountCredentials implements AccountCredentials<AppEngineCredentials> {
  final String name
  final String environment
  final String accountType
  final String project
  final String cloudProvider = AppEngineCloudProvider.ID
  final List<String> requiredGroupMembership

  @JsonIgnore
  final AppEngineCredentials credentials
  final String applicationName
  final Appengine appengine

  static class Builder {
    String name
    String environment
    String accountType
    String project
    List<String> requiredGroupMembership
    AppEngineCredentials credentials

    String jsonKey
    String applicationName
    Appengine appengine

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

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership
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

    AppEngineNamedAccountCredentials build() {
      credentials = jsonKey ?
        new AppEngineJsonCredentials(project, jsonKey) :
        new AppEngineCredentials(project)

      appengine = credentials.getAppEngine(applicationName)

      return new AppEngineNamedAccountCredentials(name,
                                                  environment,
                                                  accountType,
                                                  project,
                                                  AppEngineCloudProvider.ID,
                                                  requiredGroupMembership,
                                                  credentials,
                                                  applicationName,
                                                  appengine)
    }
  }
}
