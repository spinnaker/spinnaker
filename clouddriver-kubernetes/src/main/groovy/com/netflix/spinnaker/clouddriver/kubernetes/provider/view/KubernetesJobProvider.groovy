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
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesJob
import com.netflix.spinnaker.clouddriver.model.JobProvider
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
  KubernetesInstanceProvider instanceProvider

  @Autowired
  KubernetesJobProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  KubernetesJob getJob(String account, String location, String id) {
    def instance = instanceProvider.getInstance(account, location, id)
    return new KubernetesJob(instance, account)
  }
}
