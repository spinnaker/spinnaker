/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesJob
import com.netflix.spinnaker.clouddriver.model.JobProvider
import io.fabric8.kubernetes.api.model.extensions.Job
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class KubernetesJobProvider implements JobProvider<KubernetesJob> {
  String platform = "kubernetes"
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  KubernetesSecurityGroupProvider securityGroupProvider

  @Autowired
  KubernetesJobProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  private KubernetesJob translateJob(CacheData jobData) {
    if (!jobData) {
      return null
    }

    String account = jobData.attributes.account
    String location = jobData.attributes.namespace

    Collection<CacheData> allLoadBalancers = KubernetesClusterProvider.resolveRelationshipDataForCollection(cacheView, [jobData],
                                                                                                            Keys.Namespace.LOAD_BALANCERS.ns,
                                                                                                            RelationshipCacheFilter.include(Keys.Namespace.SECURITY_GROUPS.ns))

    def securityGroups = KubernetesClusterProvider.loadBalancerToSecurityGroupMap(securityGroupProvider, cacheView, allLoadBalancers)

    Set<CacheData> processes = KubernetesProviderUtils.getAllMatchingKeyPattern(cacheView, Keys.Namespace.PROCESSES.ns,
                                                                                Keys.getProcessKey(account, location, (String)jobData.attributes.name, "*"))

    def job = objectMapper.convertValue(jobData.attributes.job, Job)
    def res = new KubernetesJob(job, KubernetesProviderUtils.controllerToInstanceMap(objectMapper, processes)[(String)jobData.attributes.name], account)
    res.loadBalancers?.each {
      res.securityGroups.addAll(securityGroups[it])
    }

    return res
  }

  @Override
  KubernetesJob getJob(String account, String location, String id) {
    String jobKey = Keys.getJobKey(account, location, id)
    return translateJob(cacheView.get(Keys.Namespace.JOBS.ns, jobKey))
  }

  @Override
  List<KubernetesJob> getJobsByApp(String app) {
    Set<CacheData> jobs = KubernetesProviderUtils.getAllMatchingKeyPattern(cacheView, Keys.Namespace.JOBS.ns, Keys.getJobKey("*", "*", "$app-*"))

    return jobs?.collect { translateJob(it) } ?: []
  }
}
