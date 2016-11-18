/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.gate.services

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.util.concurrent.UncheckedExecutionException
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * DefaultProviderLookupService.
 */
@Component("providerLookupService")
class DefaultProviderLookupService implements ProviderLookupService, AccountLookupService {

  private static final String FALLBACK = "unknown"
  private static final String ACCOUNTS_KEY = "all"

  private final ClouddriverService clouddriverService

  private final LoadingCache<String, List<ClouddriverService.Account>> accountsCache = CacheBuilder.newBuilder()
    .initialCapacity(1)
    .maximumSize(1)
    .refreshAfterWrite(2, TimeUnit.SECONDS)
    .build(new CacheLoader<String, List<ClouddriverService.Account>>() {
        @Override
        List<ClouddriverService.Account> load(String key) throws Exception {
          return clouddriverService.accounts
        }
      })

  @Autowired
  public DefaultProviderLookupService(ClouddriverService clouddriverService) {
    this.clouddriverService = clouddriverService
  }

  @Override
  public String providerForAccount(String account) {
    try {
      return accountsCache.get(ACCOUNTS_KEY)?.find { it.name == account }?.type ?: FALLBACK
    } catch (ExecutionException | UncheckedExecutionException ex) {
      return FALLBACK
    }
  }

  @Override
  public List<ClouddriverService.Account> getAccounts() {
    return accountsCache.get(ACCOUNTS_KEY)
  }
}
