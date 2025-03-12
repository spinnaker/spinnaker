/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.model.AmazonCertificate
import com.netflix.spinnaker.clouddriver.model.CertificateProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.CERTIFICATES

@Component
class AmazonCertificateProvider implements CertificateProvider<AmazonCertificate> {

  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  AmazonCertificateProvider(Cache cacheView, @Qualifier("amazonObjectMapper") ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  String getCloudProvider() {
    return AmazonCloudProvider.ID
  }

  @Override
  Set<AmazonCertificate> getAll() {
    cacheView.getAll(CERTIFICATES.ns, RelationshipCacheFilter.none()).collect {
      objectMapper.convertValue(it.attributes, AmazonCertificate)
    }
  }
}
