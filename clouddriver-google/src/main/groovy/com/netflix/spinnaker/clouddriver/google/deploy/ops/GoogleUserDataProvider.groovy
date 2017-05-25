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
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
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

  /**
   * Returns the user data as a Map.
   */
  Map getUserData(final String serverGroupName, final String instanceTemplateName,
                  BasicGoogleDeployDescription description,
                  GoogleNamedAccountCredentials credentials, String customUserData) {
    String userDataFile = credentials.getUserDataFile()
    String rawUserData = getFileContents(userDataFile)
    String commonUserData = replaceTokens(rawUserData, description, serverGroupName, instanceTemplateName)

    Map userDataMap = stringToUserDataMap(commonUserData)

    if (customUserData) {
      def customUserDataMap = ["customUserData": customUserData] << stringToUserDataMap(customUserData)
      userDataMap << customUserDataMap
    }
    return userDataMap
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
      def rawContentsList = file.readLines()
      def contentsList = []
      for (line in rawContentsList) {
        if (!line.startsWith('#')) {
          contentsList = contentsList << line
        }
      }
      return contentsList.join(',')
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
   * %%cluster%% 	    the name of the cluster
   * %%stack%% 	      the stack component of the cluster name
   * %%detail%% 	    the detail component of the cluster name
   * %%launchconfig%% the name of the instance template
   */
  private String replaceTokens(String rawUserData, BasicGoogleDeployDescription description,
                               String serverGroupName, String instanceTemplateName) {
    if (!rawUserData) {
      return ''
    }
    Names names = Names.parseName(serverGroupName)
    // Replace the tokens & return the result.
    String result = rawUserData
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

    return result
  }

  /**
   * Takes the user data as a String and returns it as a Map<String, String>.
   */
  private Map<String, String> stringToUserDataMap(String rawUserData){
    if (rawUserData) {
      def userDataMap = rawUserData.split('\n|,').collectEntries {
        def pair = it.split('=')
        [(pair.first()):pair.last()]
      }
      return userDataMap
    } else {
      return [:]
    }
  }
}
