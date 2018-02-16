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
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesV1SecurityGroup
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import io.fabric8.kubernetes.api.model.extensions.Ingress
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class KubernetesV1SecurityGroupProvider implements SecurityGroupProvider<KubernetesV1SecurityGroup> {

  final String cloudProvider = KubernetesCloudProvider.ID
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  KubernetesV1SecurityGroupProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<KubernetesV1SecurityGroup> getAll(boolean includeRules) {
    lookup("*", "*", "*", includeRules)
  }

  @Override
  Set<KubernetesV1SecurityGroup> getAllByRegion(boolean includeRules, String namespace) {
    lookup("*", namespace, "*", includeRules)
  }

  @Override
  Set<KubernetesV1SecurityGroup> getAllByAccount(boolean includeRules, String account) {
    lookup(account, "*", "*", includeRules)
  }

  @Override
  Set<KubernetesV1SecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String name) {
    lookup(account, "*", name, includeRules)
  }

  @Override
  Set<KubernetesV1SecurityGroup> getAllByAccountAndRegion(boolean includeRules, String account, String namespace) {
    lookup(account, namespace, "*", includeRules)
  }

  @Override
  KubernetesV1SecurityGroup get(String account, String namespace, String name, String vpcId) {
    lookup(account, namespace, name, true).getAt(0)
  }

  Set<KubernetesV1SecurityGroup> lookup(String account, String namespace, String name, boolean includeRule) {
    def keys = cacheView.filterIdentifiers(Keys.Namespace.SECURITY_GROUPS.ns, Keys.getSecurityGroupKey(account, namespace, name))
    cacheView.getAll(Keys.Namespace.SECURITY_GROUPS.ns, keys).collect {
      translateSecurityGroup(it, includeRule)
    }
  }

  public KubernetesV1SecurityGroup translateSecurityGroup(CacheData securityGroupEntry, boolean includeRule) {
    def parts = Keys.parse(securityGroupEntry.id)
    Ingress ingress = objectMapper.convertValue(securityGroupEntry.attributes.ingress, Ingress)
    return new KubernetesV1SecurityGroup(parts.application, parts.account, ingress, includeRule)
  }
}
