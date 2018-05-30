/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.caching.utils

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
class CachingSchemaUtil {
  private Map<String, CachingSchema> cachingSchemaForAccounts = [:]

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AwsLookupUtil awsLookupUtil

  CachingSchema getCachingSchemaForAccount(String account) {
    return cachingSchemaForAccounts.get(account) ?: CachingSchema.V1
  }

  @PostConstruct
  private void init() {
    accountCredentialsProvider.all.findAll {
      it instanceof NetflixTitusCredentials
    }.each { NetflixTitusCredentials credential ->
      credential.regions.each { region ->
        cachingSchemaForAccounts.put(credential.name, credential.splitCachingEnabled ? CachingSchema.V2 : CachingSchema.V1)
        cachingSchemaForAccounts.put(
          awsLookupUtil.awsAccountId(credential.name, region.name),
          credential.splitCachingEnabled ? CachingSchema.V2 : CachingSchema.V1
        )
      }
    }
  }
}
