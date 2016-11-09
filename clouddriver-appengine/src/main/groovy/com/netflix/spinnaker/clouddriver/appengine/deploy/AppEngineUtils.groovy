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

import com.google.api.services.appengine.v1.model.Service
import com.google.api.services.appengine.v1.model.Version
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task

class AppEngineUtils {
  static List<Version> queryAllVersions(String project,
                                        AppEngineNamedAccountCredentials credentials,
                                        Task task,
                                        String phase) {
    task.updateStatus phase, "Querying all versions for project $project..."
    def services = queryAllServices(project, credentials, task, phase)

    services.collect { service ->
      queryVersionsForService(project, parseResourceName(service.getName()), credentials, task, phase)
    }.flatten() as List<Version>
  }

  static List<Service> queryAllServices(String project,
                                        AppEngineNamedAccountCredentials credentials,
                                        Task task,
                                        String phase) {
    task.updateStatus phase, "Querying services for project $project..."
    credentials.appengine.apps().services().list(project).execute().getServices()
  }

  static List<Version> queryVersionsForService(String project,
                                               String service,
                                               AppEngineNamedAccountCredentials credentials,
                                               Task task,
                                               String phase) {
    task.updateStatus phase, "Querying versions for project $project and service $service"
    credentials.appengine.apps().services().versions().list(project, service).execute().getVersions()
  }

  static String parseResourceName(String resourceName) {
    resourceName.split('/').last()
  }
}
