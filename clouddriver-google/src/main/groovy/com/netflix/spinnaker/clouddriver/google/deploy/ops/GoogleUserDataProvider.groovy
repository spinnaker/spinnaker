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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.netflix.frigga.Names

import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.kork.configserver.ConfigFileService
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Provides the common user data from a local file to be applied to all Google deployments.
 *
 * Any custom user data specified for each deployment will be appended to common user data, allowing custom user data
 * to override the common user data.
 */
@Slf4j
@Component
public class GoogleUserDataProvider {
  @Autowired
  private ConfigFileService configFileService

  /**
   * Returns the user data as a Map.
   */
  Map getUserData(final String serverGroupName, final String instanceTemplateName,
                  BasicGoogleDeployDescription description,
                  GoogleNamedAccountCredentials credentials, String customUserData) {
    String userDataFile = credentials.getUserDataFile()
    List<String> rawUserData = getFileContents(userDataFile)
    List<String> cleanedUserData = cleanUpUserData(rawUserData)
    List<String> commonUserData = replaceTokens(cleanedUserData, description, serverGroupName, instanceTemplateName)

    Map<String, String> userDataMap = convertToMap(commonUserData)

    if (customUserData) {
      def customUserDataMap = ["customUserData": customUserData] << stringToUserDataMap(customUserData)
      userDataMap << customUserDataMap
    }
    return userDataMap
  }

  /**
   * Returns the contents of a file or an empty list if the file doesn't exist.
   */
  @PackageScope
  List<String> getFileContents(String filename) {
    if (!filename) {
      return []
    }
    try {
      return configFileService.getContents(filename).readLines()
    } catch (Exception e) {
      log.warn("Failed to read user data file ${filename}; ${e.message}")
      return []
    }
  }

  private Map<String, String> stringToUserDataMap(String userData) {
    List<String> lines = userData.split("\n|,")
    return convertToMap(lines)
  }

  /**
   * Filters out comments and blank lines.
   * @return new list without comments and blank lines
   */
  private List<String> cleanUpUserData(List<String> userData) {
      return userData.findAll {
        !it.startsWith('#') && !it.isAllWhitespace()
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
   * %%cluster%% 	    the name of the cluster
   * %%stack%% 	      the stack component of the cluster name
   * %%detail%% 	    the detail component of the cluster name
   * %%launchconfig%% the name of the instance template
   */
  private List<String> replaceTokens(List<String> rawUserData, BasicGoogleDeployDescription description,
                               String serverGroupName, String instanceTemplateName) {
    if (!rawUserData) {
      return []
    }
    Names names = Names.parseName(serverGroupName)
    // Replace the tokens & return the result.
    return rawUserData.collect{userData ->
      return userData
        .replace('%%account%%', description.accountName)
        .replace('%%accounttype%%', description.credentials.accountType ?: '')
        .replace('%%app%%', names.app ?: '')
        .replace('%%env%%', description.credentials.environment ?: '')
        .replace('%%region%%', description.region ?: '')
        .replace('%%stack%%', description.stack ?: '')
        .replace('%%group%%', names.group ?: '')
        .replace('%%cluster%%', names.cluster ?: '')
        .replace('%%detail%%', names.detail ?: '')
        .replace('%%launchconfig%%', instanceTemplateName ?: '')
    }
  }

  /**
   * Takes the user data as a String and returns it as a Map<String, String>.
   */
  private Map<String, String> convertToMap(List<String> userData) {
    if (!userData) {
      return [:]
    }

    // Convert the user data from a list to a map
    return userData.collectEntries {
      def parts = it.split('=', 2)
      if (parts.length == 0) {
        return [:]
      } else if (parts.length == 1) {
        return [(parts.first()): '']
      } else {
        return [(parts.first()): parts.last()]
      }
    }
  }
}
