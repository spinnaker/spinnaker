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
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesInstance
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import io.fabric8.kubernetes.api.model.Event
import io.fabric8.kubernetes.api.model.Pod
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class KubernetesInstanceProvider implements InstanceProvider<KubernetesInstance> {
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  KubernetesInstanceProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  String platform = Keys.Namespace.provider

  @Override
  KubernetesInstance getInstance(String account, String namespace, String name) {
    Set<CacheData> instances = KubernetesProviderUtils.getAllMatchingKeyPattern(cacheView, Keys.Namespace.INSTANCES.ns, Keys.getInstanceKey(account, namespace, name))
    if (!instances || instances.size() == 0) {
      return null
    }

    if (instances.size() > 1) {
      throw new IllegalStateException("Multiple kubernetes pods with name $name in namespace $namespace exist.")
    }

    CacheData instanceData = instances.toArray()[0]

    def loadBalancers = instanceData.relationships[Keys.Namespace.LOAD_BALANCERS.ns].collect {
      Keys.parse(it).name
    }

    def pod = objectMapper.convertValue(instanceData.attributes.pod, Pod)
    def events = objectMapper.convertValue(instanceData.attributes.events, List)

    events = events.collect { event ->
      objectMapper.convertValue(event, Event)
    }

    return new KubernetesInstance(pod, loadBalancers, events)
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    return null
  }
}
