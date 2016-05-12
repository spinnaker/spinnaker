/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.config

import groovy.transform.ToString

class GoogleConfigurationProperties {
  public static final int POLLING_INTERVAL_SECONDS_DEFAULT = 60
  public static final int ASYNC_OPERATION_TIMEOUT_SECONDS_DEFAULT = 300
  public static final int ASYNC_OPERATION_MAX_POLLING_INTERVAL_SECONDS = 8

  @ToString(includeNames = true)
  static class ManagedAccount {
    String name
    String environment
    String accountType
    String project
    boolean alphaListed
    String jsonPath
    List<String> imageProjects
    List<String> requiredGroupMembership

    public InputStream getInputStream() {
      if (jsonPath) {
        if (jsonPath.startsWith("classpath:")) {
          getClass().getResourceAsStream(jsonPath.replace("classpath:", ""))
        } else {
          new FileInputStream(new File(jsonPath))
        }
      } else {
        null
      }
    }
  }

  List<ManagedAccount> accounts = []
  int pollingIntervalSeconds = POLLING_INTERVAL_SECONDS_DEFAULT
  int asyncOperationTimeoutSecondsDefault = ASYNC_OPERATION_TIMEOUT_SECONDS_DEFAULT
  int asyncOperationMaxPollingIntervalSeconds = ASYNC_OPERATION_MAX_POLLING_INTERVAL_SECONDS
  List<String> baseImageProjects
}
