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


package com.netflix.spinnaker.kato.deploy.aws.userdata

import com.netflix.frigga.Names
import com.netflix.spinnaker.kato.config.PerforceProperties
import com.perforce.p4java.core.file.FileSpecBuilder
import com.perforce.p4java.core.file.IFileSpec
import com.perforce.p4java.exception.P4JavaError
import com.perforce.p4java.option.server.GetDepotFilesOptions
import com.perforce.p4java.server.IOptionsServer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

class PerforceUserDataProvider implements UserDataProvider {
  private static final INSERTION_MARKER = '\nexport EC2_REGION='

  @Autowired
  PerforceProperties perforceProperties

  @Autowired
  IOptionsServer p4server

  @Override
  String getUserData(String asgName, String launchConfigName, String region, String environment) {
    def names = Names.parseName(asgName)
    def rawUserData = assembleUserData(names, region, environment)
    replaceUserDataTokens names, launchConfigName, region, environment, rawUserData
  }

  String assembleUserData(Names names, String region, String environment) {
    def udfRoot = perforceProperties.udfRoot

    String cluster = names.cluster
    String stack = names.stack

    // If no Ruby file then get the component Unix shell template files into string lists including custom files for
    // the app and/or auto scaling group.
    // If app and group names are identical, only include their UDF file once.

    // LinkedHashSet ensures correct order and no duplicates when the app, cluster, and groupName are equal.
    Set<String> udfPaths = new LinkedHashSet<String>()
    udfPaths << "${udfRoot}/udf0"
    udfPaths << "${udfRoot}/udf-${environment}"
    udfPaths << "${udfRoot}/udf-${region}-${environment}"
    udfPaths << "${udfRoot}/udf1"
    udfPaths << "${udfRoot}/custom.d/${names.app}-${environment}"
    udfPaths << "${udfRoot}/custom.d/${names.app}-${stack}-${environment}"
    udfPaths << "${udfRoot}/custom.d/${cluster}-${environment}"
    udfPaths << "${udfRoot}/custom.d/${names.group}-${environment}"
    udfPaths << "${udfRoot}/custom.region.d/${region}/${names.app}-${environment}"
    udfPaths << "${udfRoot}/custom.region.d/${region}/${names.app}-${stack}-${environment}"
    udfPaths << "${udfRoot}/custom.region.d/${region}/${cluster}-${environment}"
    udfPaths << "${udfRoot}/custom.region.d/${region}/${names.group}-${environment}"
    udfPaths << "${udfRoot}/udf2"

    // Concat all the Unix shell templates into one string
    udfPaths.collect { String path -> getContents(path) }.join('')
  }

  static String replaceUserDataTokens(Names names, String launchConfigName, String region, String env, String rawUserData) {
    String stack = names.stack ?: ''
    String cluster = names.cluster ?: ''

    // Replace the tokens & return the result
    String result = rawUserData
      .replace('%%app%%', names.app)
      .replace('%%env%%', env)
      .replace('%%region%%', region)
      .replace('%%group%%', names.group)
      .replace('%%autogrp%%', names.group)
      .replace('%%cluster%%', cluster)
      .replace('%%stack%%', stack)
      .replace('%%launchconfig%%', launchConfigName)

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


  String getContents(String depotFilePath) {
    try {
      List<IFileSpec> fileSpecs = FileSpecBuilder.makeFileSpecList(depotFilePath)
      GetDepotFilesOptions options = new GetDepotFilesOptions()
      List<IFileSpec> depotFileSpecs = p4server.getDepotFiles(fileSpecs, options)
      IFileSpec fileSpec = depotFileSpecs[0]
      InputStream fileSpecInputStream = fileSpec.getContents(true)
      String contents = fileSpecInputStream.getText('UTF-8')
      if (contents.length() && !contents.endsWith("\n")) {
        contents = contents + '\n'
      }
      contents
    } catch (P4JavaError IGNORE) {
      // This normal case happens if the requested file is not found.
      ''
    }
  }
}
