/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.core.agent.ProjectClustersCachingAgent
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@Slf4j
@RestController
@RequestMapping("/projects/{project}")
class ProjectController {

  Cache cacheView

  @Autowired
  ProjectController(Cache cacheView) {
    this.cacheView = cacheView
  }

  @RequestMapping(method= RequestMethod.GET, value = "/clusters")
  List<ProjectClustersCachingAgent.ClusterModel> getClusters(@PathVariable String project) {
    CacheData cacheData = cacheView.get(Namespace.PROJECT_CLUSTERS.ns, "v1")
    if (cacheData == null) {
      throw new NotFoundException("Projects not cached")
    }

    Object clusters = cacheData.attributes.get(project)
    if (clusters == null) {
      throw new NotFoundException("Project not found (name: $project)")
    }
    return clusters
  }
}
