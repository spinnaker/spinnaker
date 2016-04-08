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
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesProcess
import com.netflix.spinnaker.clouddriver.model.ProcessProvider
import io.fabric8.kubernetes.api.model.Pod
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class KubernetesProcessProvider implements ProcessProvider<KubernetesProcess> {
  private final Cache cacheView
  private final ObjectMapper objectMapper
  String platform = "kubernetes"

  @Autowired
  KubernetesProcessProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  KubernetesProcess getProcess(String account, String location, String id) {
    Set<CacheData> processs = KubernetesProviderUtils.getAllMatchingKeyPattern(cacheView, Keys.Namespace.PROCESSES.ns, Keys.getProcessKey(account, location, "*", id))
    if (!processs || processs.size() == 0) {
      return null
    }

    if (processs.size() > 1) {
      throw new IllegalStateException("Multiple kubernetes pods with name $id in namespace $location exist.")
    }

    CacheData processData = (CacheData) processs.toArray()[0]

    def pod = objectMapper.convertValue(processData.attributes.pod, Pod)

    return new KubernetesProcess(pod, account)
  }
}
