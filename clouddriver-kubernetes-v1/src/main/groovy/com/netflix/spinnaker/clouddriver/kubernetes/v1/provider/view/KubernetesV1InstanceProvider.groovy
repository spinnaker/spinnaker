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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesV1Instance
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.model.ContainerLog
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class KubernetesV1InstanceProvider implements InstanceProvider<KubernetesV1Instance, List<ContainerLog>> {
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  KubernetesV1InstanceProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  final String cloudProvider = KubernetesCloudProvider.ID

  @Override
  KubernetesV1Instance getInstance(String account, String namespace, String name) {
    Set<CacheData> instances = KubernetesProviderUtils.getAllMatchingKeyPattern(cacheView, Keys.Namespace.INSTANCES.ns, Keys.getInstanceKey(account, namespace, name))
    if (!instances || instances.size() == 0) {
      return null
    }

    if (instances.size() > 1) {
      throw new IllegalStateException("Multiple kubernetes pods with name $name in namespace $namespace exist.")
    }

    CacheData instanceData = (CacheData) instances.toArray()[0]

    if (!instanceData) {
      return null
    }

    def loadBalancers = instanceData.relationships[Keys.Namespace.LOAD_BALANCERS.ns].collect {
      Keys.parse(it).name
    }

    KubernetesV1Instance instance = objectMapper.convertValue(instanceData.attributes.instance, KubernetesV1Instance)
    instance.loadBalancers = loadBalancers

    return instance
  }

  @Override
  List<ContainerLog> getConsoleOutput(String account, String region, String id) {
    KubernetesNamedAccountCredentials<KubernetesV1Credentials> credentials;
    try {
      credentials = (KubernetesNamedAccountCredentials) accountCredentialsProvider.getCredentials(account)
    } catch(Exception e) {
      log.warn("Failure getting account credentials for ${account}")
      return null
    }
    if (credentials?.getProviderVersion() != ProviderVersion.v1) {
      return null
    }

    def trueCredentials = credentials.credentials
    def pod = trueCredentials.apiAdaptor.getPod(region, id)
    if (pod == null ) {
      return null
    }

    String podName = pod.getMetadata().getName()
    List<ContainerLog> result = new ArrayList()

    pod.getSpec().getContainers().collect { container ->
      ContainerLog log = new ContainerLog()
      log.setName(container.getName())
      try {
        String logOutput = trueCredentials.apiAdaptor.getLog(region, podName, container.getName())
        log.setOutput(logOutput)
      } catch(Exception e){
        log.setOutput(e.getMessage())
      }
      result.add(log)
    }

    return result

  }
}
