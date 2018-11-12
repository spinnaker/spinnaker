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

import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.http.HttpHeaders
import com.google.api.services.appengine.v1.model.ListVersionsResponse
import com.google.api.services.appengine.v1.model.Service
import com.google.api.services.appengine.v1.model.Version
import com.netflix.spinnaker.clouddriver.appengine.provider.callbacks.AppengineCallback
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task

class AppengineUtils {
  static List<Version> queryAllVersions(String project,
                                        AppengineNamedAccountCredentials credentials,
                                        Task task,
                                        String phase) {
    task.updateStatus phase, "Querying all versions for project $project..."
    def services = queryAllServices(project, credentials, task, phase)

    BatchRequest batch = credentials.appengine.batch()
    def allVersions = []

    services.each { service ->
      def callback = new AppengineCallback<ListVersionsResponse>()
        .success { ListVersionsResponse versionsResponse, HttpHeaders responseHeaders ->
          def versions = versionsResponse.getVersions()
          if (versions) {
            allVersions << versions
          }
        }

      credentials.appengine.apps().services().versions().list(project, service.getId()).queue(batch, callback)
    }

    if (batch.size() > 0) {
      batch.execute()
    }

    return allVersions.flatten()
  }

  static List<Service> queryAllServices(String project,
                                        AppengineNamedAccountCredentials credentials,
                                        Task task,
                                        String phase) {
    task.updateStatus phase, "Querying services for project $project..."
    return credentials.appengine.apps().services().list(project).execute().getServices()
  }

  static List<Version> queryVersionsForService(String project,
                                               String service,
                                               AppengineNamedAccountCredentials credentials,
                                               Task task,
                                               String phase) {
    task.updateStatus phase, "Querying versions for project $project and service $service"
    return credentials.appengine.apps().services().versions().list(project, service).execute().getVersions()
  }

  static Service queryService(String project,
                              String service,
                              AppengineNamedAccountCredentials credentials,
                              Task task,
                              String phase) {
    task.updateStatus phase, "Querying service $service for project $project..."
    return credentials.appengine.apps().services().get(project, service).execute()
  }
}
