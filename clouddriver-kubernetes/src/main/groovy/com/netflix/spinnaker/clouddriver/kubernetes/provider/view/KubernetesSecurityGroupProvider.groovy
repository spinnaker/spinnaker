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
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesSecurityGroup
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import io.fabric8.kubernetes.api.model.extensions.Ingress
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class KubernetesSecurityGroupProvider implements SecurityGroupProvider<KubernetesSecurityGroup> {

  final String cloudProvider = KubernetesCloudProvider.ID
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  KubernetesSecurityGroupProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<KubernetesSecurityGroup> getAll(boolean includeRules) {
    lookup("*", "*", "*", includeRules)
  }

  @Override
  Set<KubernetesSecurityGroup> getAllByRegion(boolean includeRules, String namespace) {
    lookup("*", namespace, "*", includeRules)
  }

  @Override
  Set<KubernetesSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    lookup(account, "*", "*", includeRules)
  }

  @Override
  Set<KubernetesSecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String name) {
    lookup(account, "*", name, includeRules)
  }

  @Override
  Set<KubernetesSecurityGroup> getAllByAccountAndRegion(boolean includeRules, String account, String namespace) {
    lookup(account, namespace, "*", includeRules)
  }

  @Override
  KubernetesSecurityGroup get(String account, String namespace, String name, String vpcId) {
    lookup(account, namespace, name, true).getAt(0)
  }

  Set<KubernetesSecurityGroup> lookup(String account, String namespace, String name, boolean includeRule) {
    def keys = cacheView.filterIdentifiers(Keys.Namespace.SECURITY_GROUPS.ns, Keys.getSecurityGroupKey(account, namespace, name))
    cacheView.getAll(Keys.Namespace.SECURITY_GROUPS.ns, keys).collect {
      translateSecurityGroup(it, includeRule)
    }
  }

  public KubernetesSecurityGroup translateSecurityGroup(CacheData securityGroupEntry, boolean includeRule) {
    def parts = Keys.parse(securityGroupEntry.id)
    Ingress ingress = objectMapper.convertValue(securityGroupEntry.attributes.ingress, Ingress)
    return new KubernetesSecurityGroup(parts.application, parts.account, ingress, includeRule)
  }
}
