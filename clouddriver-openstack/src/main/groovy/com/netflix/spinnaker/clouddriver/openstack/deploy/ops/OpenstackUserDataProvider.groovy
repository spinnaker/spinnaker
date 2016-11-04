/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

// TODO (jshimek) Refactor the LocalFileUserDataProvider the AWS driver uses to be more driver agnostic
// See https://github.com/spinnaker/spinnaker/issues/1274
/**
 * Provides the common user data from a local file to be applied to all OpenStack deployments.
 *
 * Any custom user data specified for each deployment will be appended to common user data, allowing custom user data
 * to override the common user data.
 */
@Slf4j
public class OpenstackUserDataProvider {

  final OpenstackNamedAccountCredentials credentials

  OpenstackUserDataProvider(OpenstackNamedAccountCredentials credentials) {
    this.credentials = credentials
  }

  /**
   * Returns the custom user data or the empty string if non is found.
   */
  String getUserData(final String serverGroupName, final String region, final String customUserData) {

    String userDataFile = credentials.getUserDataFile()
    String rawUserData = getFileContents(userDataFile)
    String commonUserData = replaceTokens(rawUserData, serverGroupName, region)


    StringBuilder userData = new StringBuilder();
    if (commonUserData) {
      userData.append(commonUserData)
      userData.append('\n')
    }
    if (customUserData) {
      userData.append(customUserData)
    }

    userData.toString()
  }

  /**
   * Returns the contents of a file or an empty string if the file doesn't exist.
   */
  @PackageScope
  String getFileContents(String filename) {

    if (!filename) {
      return ''
    }

    try {
      File file = new File(filename)
      String contents = file.getText('UTF-8')
      if (contents.length() && !contents.endsWith("\n")) {
        contents = contents + '\n'
      }
      return contents
    } catch (IOException e) {
      log.warn("Failed to read user data file ${filename}; ${e.message}")
      return ''
    }
  }
  /**
   * Returns the user data with the tokens replaced.
   *
   * Currently supports the following tokens:
   *
   * %%account%% 	    the name of the account
   * %%accounttype%% 	the accountType of the account
   * %%env%%        	the environment of the account
   * %%app%%          the name of the app
   * %%region%% 	    the deployment region
   * %%group%% 	      the name of the server group
   * %%autogrp%% 	    the name of the server group
   * %%cluster%% 	    the name of the cluster
   * %%stack%% 	      the stack component of the cluster name
   * %%detail%% 	    the detail component of the cluster name
   * %%launchconfig%% the name of the launch configuration (server group name)
   */
  private String replaceTokens(String rawUserData, String serverGroupName, String region) {

    if (!rawUserData) {
      return ''
    }

    Names names = Names.parseName(serverGroupName)

    // Replace the tokens & return the result
    String result = rawUserData
      .replace('%%account%%', credentials.name ?: '')
      .replace('%%accounttype%%', credentials.accountType ?: '')
      .replace('%%env%%', credentials.environment ?: '')
      .replace('%%app%%', names.app ?: '')
      .replace('%%region%%', region ?: '')
      .replace('%%group%%', names.group ?: '')
      .replace('%%autogrp%%', names.group ?: '')
      .replace('%%cluster%%', names.cluster ?: '')
      .replace('%%stack%%', names.stack ?: '')
      .replace('%%detail%%', names.detail ?: '')
      .replace('%%launchconfig%%', serverGroupName ?: '')

    result
  }
}
