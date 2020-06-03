/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
@Slf4j
class ProjectService {

  @Autowired
  Front50Service front50Service

  @Autowired
  OrcaServiceSelector orcaServiceSelector

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  List<Map> getAll() {
    return front50Service.getAllProjects() ?: []
  }

  Map get(String id) {
    front50Service.getProject(id)
  }

  List<Map> getAllPipelines(String projectId, int limit, String statuses) {
    return orcaServiceSelector.select().getPipelinesForProject(projectId, limit, statuses)
  }

  List getClusters(String projectId, String selectorKey) {
    return clouddriverServiceSelector.select().getProjectClusters(projectId)
  }
}
