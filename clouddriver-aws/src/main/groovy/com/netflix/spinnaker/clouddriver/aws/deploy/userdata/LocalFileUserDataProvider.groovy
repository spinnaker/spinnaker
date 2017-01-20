/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.aws.deploy.userdata

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.deploy.LaunchConfigurationBuilder
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import org.springframework.beans.factory.annotation.Autowired
import retrofit.RetrofitError

class LocalFileUserDataProvider implements UserDataProvider {
  private static final INSERTION_MARKER = '\nexport EC2_REGION='

  @Autowired
  LocalFileUserDataProperties localFileUserDataProperties

  @Autowired
  Front50Service front50Service

  boolean isLegacyUdf(String account, String applicationName) {
    Closure<Boolean> result = {
      try {
        Map application = front50Service.getApplication(applicationName)
        if (application.legacyUdf == null) {
          return localFileUserDataProperties.defaultLegacyUdf
        }
        return Boolean.valueOf(application.legacyUdf)
      } catch (RetrofitError re) {
        if (re.kind == RetrofitError.Kind.HTTP && re.response.status == 404) {
          return localFileUserDataProperties.defaultLegacyUdf
        }
        throw re
      }
    }

    final int maxRetry = 5
    final int retryBackoff = 500
    final Set<Integer> retryStatus = [429, 500]
    for (int i = 0; i < maxRetry; i++) {
      try {
        return result.call()
      } catch (RetrofitError re) {
        if (re.kind == RetrofitError.Kind.NETWORK || (re.kind == RetrofitError.Kind.HTTP && retryStatus.contains(re.response.status))) {
          Thread.sleep(retryBackoff)
        }
      }
    }
    throw new IllegalStateException("Failed to read legacyUdf preference from front50 for $account/$applicationName")
  }

  @Override
  String getUserData(String launchConfigName, LaunchConfigurationBuilder.LaunchConfigurationSettings settings, Boolean legacyUdf) {
    def names = Names.parseName(settings.baseName)
    boolean useLegacyUdf = legacyUdf != null ? legacyUdf : isLegacyUdf(settings.account, names.app)
    def rawUserData = assembleUserData(useLegacyUdf, names, settings.region, settings.account)
    replaceUserDataTokens useLegacyUdf, names, launchConfigName, settings.region, settings.account, settings.environment, settings.accountType, rawUserData
  }

  String assembleUserData(boolean legacyUdf, Names names, String region, String account) {
    def udfRoot = localFileUserDataProperties.udfRoot + (legacyUdf ? '/legacy' : '')

    String cluster = names.cluster
    String stack = names.stack

    // If app and group names are identical, only include their UDF file once.

    // LinkedHashSet ensures correct order and no duplicates when the app, cluster, and groupName are equal.
    Set<String> udfPaths = new LinkedHashSet<String>()
    udfPaths << "${udfRoot}/udf0"
    if (legacyUdf) {
      udfPaths << "${udfRoot}/udf-${account}"
      udfPaths << "${udfRoot}/udf-${region}-${account}"
      udfPaths << "${udfRoot}/udf1"
      udfPaths << "${udfRoot}/custom.d/${names.app}-${account}"
      udfPaths << "${udfRoot}/custom.d/${names.app}-${stack}-${account}"
      udfPaths << "${udfRoot}/custom.d/${cluster}-${account}"
      udfPaths << "${udfRoot}/custom.d/${names.group}-${account}"
      udfPaths << "${udfRoot}/custom.region.d/${region}/${names.app}-${account}"
      udfPaths << "${udfRoot}/custom.region.d/${region}/${names.app}-${stack}-${account}"
      udfPaths << "${udfRoot}/custom.region.d/${region}/${cluster}-${account}"
      udfPaths << "${udfRoot}/custom.region.d/${region}/${names.group}-${account}"
      udfPaths << "${udfRoot}/udf2"
    }

    // Concat all the Unix shell templates into one string
    udfPaths.collect { String path -> getContents(path) }.join('')
  }

  static String replaceUserDataTokens(boolean useAccountNameAsEnvironment, Names names, String launchConfigName, String region, String account, String environment, String accountType, String rawUserData) {
    String stack = names.stack ?: ''
    String cluster = names.cluster ?: ''
    String revision = names.revision ?: ''
    String countries = names.countries ?: ''
    String devPhase = names.devPhase ?: ''
    String hardware = names.hardware ?: ''
    String zone = names.zone ?: ''
    String detail = names.detail ?: ''

    // Replace the tokens & return the result
    String result = rawUserData
      .replace('%%account%%', account)
      .replace('%%accounttype%%', accountType)
      .replace('%%env%%', useAccountNameAsEnvironment ? account : environment)
      .replace('%%app%%', names.app)
      .replace('%%region%%', region)
      .replace('%%group%%', names.group)
      .replace('%%autogrp%%', names.group)
      .replace('%%revision%%', revision)
      .replace('%%countries%%', countries)
      .replace('%%devPhase%%', devPhase)
      .replace('%%hardware%%', hardware)
      .replace('%%zone%%', zone)
      .replace('%%cluster%%', cluster)
      .replace('%%stack%%', stack)
      .replace('%%detail%%', detail)
      .replace('%%launchconfig%%', launchConfigName)
      .replace('%%tier%%', '')

    List<String> additionalEnvVars = []
    additionalEnvVars << names.countries ? "NETFLIX_COUNTRIES=${names.countries}" : null
    additionalEnvVars << names.devPhase ? "NETFLIX_DEV_PHASE=${names.devPhase}" : null
    additionalEnvVars << names.hardware ? "NETFLIX_HARDWARE=${names.hardware}" : null
    additionalEnvVars << names.partners ? "NETFLIX_PARTNERS=${names.partners}" : null
    additionalEnvVars << names.revision ? "NETFLIX_REVISION=${names.revision}" : null
    additionalEnvVars << names.usedBy ? "NETFLIX_USED_BY=${names.usedBy}" : null
    additionalEnvVars << names.redBlackSwap ? "NETFLIX_RED_BLACK_SWAP=${names.redBlackSwap}" : null
    additionalEnvVars << names.zone ? "NETFLIX_ZONE=${names.zone}" : null
    additionalEnvVars.removeAll([null])

    if (additionalEnvVars) {
      String insertion = "\n${additionalEnvVars.join('\n')}"
      result = result.replace(INSERTION_MARKER, "\n${insertion}${INSERTION_MARKER}")
    }
    result
  }

  private String getContents(String filePath) {
    try {
      File file = new File(filePath)
      String contents = file.getText('UTF-8')
      if (contents.length() && !contents.endsWith("\n")) {
        contents = contents + '\n'
      }
      return contents
    } catch (IOException ignore) {
      // This normal case happens if the requested file is not found.
      return ''
    }
  }

}
