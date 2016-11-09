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

package com.netflix.spinnaker.clouddriver.appengine.deploy

import com.google.api.services.appengine.v1.model.Version
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver

import java.text.SimpleDateFormat

class AppEngineServerGroupNameResolver extends AbstractServerGroupNameResolver {
  private static final String PHASE = "DEPLOY"
  private static final SIMPLE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX"

  private final String project
  private final String region
  private final AppEngineNamedAccountCredentials credentials

  AppEngineServerGroupNameResolver(String project, String region, AppEngineNamedAccountCredentials credentials) {
    this.project = project
    this.region = region
    this.credentials = credentials
  }

  @Override
  String getPhase() {
    PHASE
  }

  @Override
  String getRegion() {
    region
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    def versions = AppEngineUtils.queryAllVersions(project, credentials, task, phase)
    return findMatchingVersions(versions, clusterName)
  }

  static List<AbstractServerGroupNameResolver.TakenSlot> findMatchingVersions(List<Version> versions, String clusterName) {
    if (!versions) {
      return []
    }

    return versions.findResults { version ->
      def versionName = AppEngineUtils.parseResourceName(version.getName())
      def friggaNames = Names.parseName(versionName)

      if (friggaNames.cluster == clusterName) {
        def timestamp = new SimpleDateFormat(SIMPLE_DATE_FORMAT).parse(version.getCreateTime()).getTime()
        return new AbstractServerGroupNameResolver.TakenSlot(
          serverGroupName: versionName,
          sequence: friggaNames.sequence,
          createdTime: new Date(timestamp)
        )
      } else {
        return null
      }
    }
  }
}
